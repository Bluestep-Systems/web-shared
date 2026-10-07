package dev.bluestep.secretfiles.spring.hikari;

import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.Duration;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.Executors;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

import javax.sql.DataSource;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.jspecify.annotations.Nullable;
import org.springframework.context.event.EventListener;
import org.springframework.core.env.PropertyResolver;

import com.zaxxer.hikari.HikariDataSource;
import com.zaxxer.hikari.HikariPoolMXBean;
import com.zaxxer.hikari.util.Credentials;
import com.zaxxer.hikari.util.DriverDataSource;
import com.zaxxer.hikari.util.PropertyElf;
import com.zaxxer.hikari.util.UtilityElf;

import dev.bluestep.secretfiles.ExceptionTypes;
import dev.bluestep.secretfiles.spring.SecretsReloadedEvent;

/**
 * Moves a Hikari pool onto rotated database credentials without a restart — but only once they have
 * been shown to work.
 *
 * <h2>Validate, then swap</h2>
 *
 * <p>On each {@link SecretsReloadedEvent}, and once at construction, the username and password
 * properties are read back from the Environment. If they are what the pool already holds, nothing
 * happens. Otherwise one connection is opened with them first, exactly as the pool opens its own, through
 * the DataSource Hikari itself would use: a supplied {@link DataSource} (a {@code PGSimpleDataSource},
 * say); else a fresh instance of the pool's {@code dataSourceClassName} given its data-source
 * properties (Hikari then ignores any {@code jdbcUrl}, and so does the probe); else a
 * {@link DriverDataSource} over the {@code jdbcUrl}, driver class and data-source properties. The
 * credentials go to {@code getConnection(username, password)}, the call the pool makes. The pool's
 * {@code connectionInitSql}, if any, is run on that connection, as the pool runs it on each of its own.
 * Only if all of that works and the connection answers a validity check are the credentials handed to
 * the pool, atomically, through {@link HikariDataSource#setCredentials}. Otherwise the refusal is logged
 * as an error and the pool keeps what it has.</p>
 *
 * <p>A blank or absent username property means the password rotates alone, against the username the
 * pool holds ({@code HikariConfig.getUsername()}); with no username there either, the offer is
 * refused untried.</p>
 *
 * <h2>Catching up at construction</h2>
 *
 * <p>A rotator does not wait for the next reload to compare: its constructor runs the same
 * validate-then-swap, retries included. A pool built from old properties after a rotation it never heard
 * of — say the password was published before {@code ALTER ROLE}, another pool refused it and is
 * retrying, and this pool was created since — therefore converges on its own. An offer that cannot be
 * read at construction is logged and left to the next reload.</p>
 *
 * <p>The asymmetry is deliberate. A pool given credentials that do not work keeps serving from the
 * connections it holds and then fails every new one — a slow, total outage that starts whenever the
 * pool next grows or retires a connection. Keeping the old credentials costs only the rotation, and
 * only until the offer works.</p>
 *
 * <h2>A refused offer is retried</h2>
 *
 * <p>A refusal is usually a matter of order or timing — the Secret was updated before the role's
 * password was, or the database blinked — and the Secret will not change again to say it is now
 * fine. So a refused offer is tried again on a background virtual thread: after
 * {@value #INITIAL_RETRY_SECONDS} s, then doubling, to a ceiling of {@value #MAX_RETRY_SECONDS} s, and
 * on at the ceiling for as long as it takes. Each retry reads the offer back from the Environment
 * afresh, exactly as an event does. Retrying ends when:</p>
 * <ul>
 *   <li>the offer validates — it is applied, just as an event would have applied it;</li>
 *   <li>the offer changes — the new offer supersedes it, and the old retry is cancelled (a new offer
 *       that is refused in turn starts its own retry from the first delay); an offer that reverts to
 *       what the pool holds, or goes blank, ends retrying altogether;</li>
 *   <li>the offer can no longer be read (a placeholder that stopped resolving) — the Environment changes
 *       only with a reload, which publishes the event that tries again;</li>
 *   <li>the rotator is {@linkplain #close() closed}.</li>
 * </ul>
 *
 * <p>The refusal is logged once, as an error; failed retries only at debug; the eventual success at
 * info.</p>
 *
 * <h2>Observing an apply</h2>
 *
 * <p>An apply can happen on the event's thread or, later, on the retry thread. A consumer that keeps
 * state derived from the pool's credentials registers {@link #onApplied}: it is called after every
 * successful {@code setCredentials}, from either path, with exactly the credentials the pool now
 * holds, while this rotator's lock is held — so two applies reach it in the order the pool took them.
 * Refusals and unchanged offers never reach it.</p>
 *
 * <h2>Open connections</h2>
 *
 * <p>By default ({@link ExistingConnections#RETIRE_AT_MAX_LIFETIME}) connections already open stay
 * open — a Postgres session outlives a password change — and are retired by the pool's normal
 * {@code maxLifetime}, so a rotation causes no burst of reconnects. {@link ExistingConnections#SOFT_EVICT}
 * instead retires each one as soon as it is idle, for a role whose old password is about to be revoked
 * outright.</p>
 *
 * <h2>Wiring</h2>
 *
 * <p>Declare one as a bean per pool; its {@code @EventListener} does the rest, and the context closes
 * it (it is {@link AutoCloseable}) on shutdown. Boot's own {@code spring.datasource} pool gets one
 * automatically ({@link HikariSecretsAutoConfiguration}). A rotator that is not a bean listens to
 * nothing: its owner calls {@link #rotate()} on each reload and {@link #close()} at shutdown. A pool
 * that takes its credentials from a {@code HikariCredentialsProvider} is refused at construction:
 * Hikari ignores {@code setCredentials} for such a pool, so a rotation would validate and then silently
 * not happen.</p>
 *
 * <p>Never logs a username or password, nor any exception's message or stack trace (a driver's or a
 * binder's message can quote what it was given): a log line names the properties, the pool, the
 * SQLState and exception types.</p>
 */
public final class HikariCredentialsRotator implements AutoCloseable {

	/** The first retry of a refused offer waits this many seconds. */
	public static final long INITIAL_RETRY_SECONDS = 5;

	/** Retries back off, doubling, to this many seconds, and then continue at it. */
	public static final long MAX_RETRY_SECONDS = 300;

	/** How long the test connection may take to answer a validity check once it is open. */
	private static final int VALIDATION_TIMEOUT_SECONDS = 5;

	private static final Log LOG = LogFactory.getLog(HikariCredentialsRotator.class);

	/** What {@link #rotate()} did. */
	public enum Outcome {
		/** The Environment's credentials are the ones the pool already holds. */
		UNCHANGED,
		/** The new credentials opened a connection and the pool now uses them. */
		APPLIED,
		/**
		 * The new credentials are missing or could not open a connection; the pool kept its own. One that
		 * could not open a connection is being retried.
		 */
		REFUSED
	}

	/** What happens to the connections a pool already holds when its credentials rotate. */
	public enum ExistingConnections {
		/** Left alone; the pool retires each at its {@code maxLifetime}. The default. */
		RETIRE_AT_MAX_LIFETIME,
		/** Soft-evicted: each is closed as soon as it is idle, so the pool reconnects promptly. */
		SOFT_EVICT
	}

	private final HikariDataSource pool;
	private final PropertyResolver properties;
	private final String usernameProperty;
	private final String passwordProperty;
	private final ExistingConnections existing;
	private final Duration initialRetryDelay;
	private final Duration maxRetryDelay;
	private final List<Consumer<? super Credentials>> appliedListeners = new CopyOnWriteArrayList<>();
	/** Runs the retries; starts no thread until the first refusal. */
	private final ScheduledExecutorService retries;
	/** Set once by {@link #close()}; read without the lock so a retry can see it at once. */
	private volatile boolean closed;

	/** The offer refused and being retried; empty when there is none. Guarded by {@code this}. */
	private Optional<Credentials> refused = Optional.empty();
	/** Retries of {@link #refused} that failed; picks the next delay. Guarded by {@code this}. */
	private int failedRetries;
	/** The scheduled retry; empty when none is pending. Guarded by {@code this}. */
	private Optional<ScheduledFuture<?>> retry = Optional.empty();
	/**
	 * Bumped whenever a retry is scheduled or retrying stops, so a retry that had already started when it
	 * was cancelled sees it is stale and does nothing. Guarded by {@code this}.
	 */
	private long retryGeneration;
	/** The credentials this rotator last applied; empty until it applies any. Guarded by {@code this}. */
	private Optional<Credentials> lastApplied = Optional.empty();

	/**
	 * Rotates {@code pool}, leaving its open connections to retire at {@code maxLifetime}.
	 *
	 * @param pool             the pool to rotate
	 * @param properties       where the credentials are read back, normally the application's Environment
	 * @param usernameProperty the property holding the username, such as {@code spring.datasource.username}
	 * @param passwordProperty the property holding the password, such as {@code spring.datasource.password}
	 * @throws IllegalArgumentException if the pool takes its credentials from a credentials provider, or
	 *                                  is configured by none of a supplied {@code DataSource}, a
	 *                                  {@code dataSourceClassName} and a {@code jdbcUrl} (a JNDI pool)
	 */
	public HikariCredentialsRotator(final HikariDataSource pool, final PropertyResolver properties,
			final String usernameProperty, final String passwordProperty) {
		this(pool, properties, usernameProperty, passwordProperty, ExistingConnections.RETIRE_AT_MAX_LIFETIME);
	}

	/**
	 * Rotates {@code pool}, treating its open connections as {@code existing} says.
	 *
	 * @param pool             the pool to rotate
	 * @param properties       where the credentials are read back, normally the application's Environment
	 * @param usernameProperty the property holding the username
	 * @param passwordProperty the property holding the password
	 * @param existing         what to do with the connections already open after a rotation
	 * @throws IllegalArgumentException if the pool takes its credentials from a credentials provider, or
	 *                                  is configured by none of a supplied {@code DataSource}, a
	 *                                  {@code dataSourceClassName} and a {@code jdbcUrl}
	 */
	public HikariCredentialsRotator(final HikariDataSource pool, final PropertyResolver properties,
			final String usernameProperty, final String passwordProperty, final ExistingConnections existing) {
		this(pool, properties, usernameProperty, passwordProperty, existing,
				Duration.ofSeconds(INITIAL_RETRY_SECONDS), Duration.ofSeconds(MAX_RETRY_SECONDS));
	}

	/** Test seam: retries a refused offer after {@code initialRetryDelay}, doubling to {@code maxRetryDelay}. */
	HikariCredentialsRotator(final HikariDataSource pool, final PropertyResolver properties,
			final String usernameProperty, final String passwordProperty, final ExistingConnections existing,
			final Duration initialRetryDelay, final Duration maxRetryDelay) {
		this.pool = Objects.requireNonNull(pool, "pool");
		this.properties = Objects.requireNonNull(properties, "properties");
		this.usernameProperty = Objects.requireNonNull(usernameProperty, "usernameProperty");
		this.passwordProperty = Objects.requireNonNull(passwordProperty, "passwordProperty");
		this.existing = Objects.requireNonNull(existing, "existing");
		this.initialRetryDelay = Objects.requireNonNull(initialRetryDelay, "initialRetryDelay");
		this.maxRetryDelay = Objects.requireNonNull(maxRetryDelay, "maxRetryDelay");
		if (pool.getCredentialsProvider() != null) {
			throw new IllegalArgumentException("Pool " + pool.getPoolName() + " takes its credentials from a "
					+ "HikariCredentialsProvider, which Hikari consults instead of setCredentials; rotate through "
					+ "the provider, or drop it and let this rotator set the credentials");
		}
		if (pool.getDataSource() == null && pool.getDataSourceClassName() == null && pool.getJdbcUrl() == null) {
			throw new IllegalArgumentException("Pool " + pool.getPoolName() + " is configured by none of a "
					+ "supplied DataSource, a dataSourceClassName or a jdbcUrl, so there is no way to open a "
					+ "validation connection the way it opens its own");
		}
		this.retries = Executors.newSingleThreadScheduledExecutor(
				Thread.ofVirtual().name("secret-files-credentials-retry-" + pool.getPoolName()).factory());
		catchUp();
	}

	/**
	 * Calls {@code listener} after every rotation this rotator applies, whether an event, a call to
	 * {@link #rotate()}, a retry or the catch-up at construction applied it, with the credentials the
	 * pool now holds. If this rotator has already applied credentials, {@code listener} is told of the
	 * latest at once, before this returns, so state derived from the pool's credentials starts in step
	 * even when the catch-up applied them. It runs with this rotator's lock held, so applies reach it in
	 * order; keep it quick, and do not call back into this rotator from another thread while it runs. A
	 * listener that throws is logged by exception type and does not undo the rotation or stop the other
	 * listeners.
	 *
	 * @param listener told of each applied rotation; never told of a refusal
	 * @return this rotator, so construction and registration can be one expression
	 */
	public synchronized HikariCredentialsRotator onApplied(final Consumer<? super Credentials> listener) {
		appliedListeners.add(Objects.requireNonNull(listener, "listener"));
		lastApplied.ifPresent(credentials -> tell(listener, credentials));
		return this;
	}

	/**
	 * Rotates after the mounted secrets changed.
	 *
	 * @param event the change; which keys changed does not matter, the comparison is with the pool
	 */
	@EventListener
	public void onSecretsReloaded(final SecretsReloadedEvent event) {
		rotate();
	}

	/**
	 * Applies the Environment's credentials to the pool if they changed and a connection opens with
	 * them; a changed offer that cannot open one is refused and retried in the background. Synchronized
	 * with the retries: two attempts validate and apply one after the other. Still works after
	 * {@link #close()}, but schedules no retry.
	 *
	 * @return what happened; a refusal has already been logged as an error
	 * @throws IllegalArgumentException if a property's value holds a placeholder that no longer resolves;
	 *                                  the pool keeps its credentials
	 */
	public synchronized Outcome rotate() {
		return attempt(false);
	}

	/**
	 * Stops retrying, for good: a pending retry is cancelled and one already running is interrupted.
	 * Idempotent. The pool is not closed.
	 */
	@Override
	public void close() {
		closed = true;
		// Outside the lock: interrupts a retry blocked opening its test connection, which holds the lock.
		retries.shutdownNow();
		synchronized (this) {
			stopRetrying();
		}
	}

	/** Test seam: whether a retry of a refused offer is scheduled. */
	synchronized boolean retryPending() {
		return retry.isPresent();
	}

	/** One attempt, from {@link #rotate()} or a retry. Called holding {@code this}. */
	private Outcome attempt(final boolean retrying) {
		final String username = offeredUsername();
		final String password = properties.getProperty(passwordProperty);
		if (holds(pool.getCredentials(), username, password)) {
			stopRetrying();
			return Outcome.UNCHANGED;
		}
		if (username == null || username.isBlank() || password == null || password.isBlank()) {
			stopRetrying();
			LOG.error((username == null || username.isBlank()
					? usernameProperty + " is blank or absent and pool " + pool.getPoolName() + " has no username of "
							+ "its own"
					: passwordProperty + " is now blank or absent")
					+ ", so pool " + pool.getPoolName() + " keeps the credentials it has");
			return Outcome.REFUSED;
		}
		final boolean alreadyRefused = refused.filter(offer -> holds(offer, username, password)).isPresent();
		try {
			validate(username, password);
		} catch (SQLException e) {
			return refuse(Credentials.of(username, password), alreadyRefused, retrying,
					"a test connection with them failed (SQLState " + e.getSQLState() + ", "
							+ ExceptionTypes.of(e) + ")");
		} catch (RuntimeException e) {
			return refuse(Credentials.of(username, password), alreadyRefused, retrying,
					"they could not be tested (" + ExceptionTypes.of(e) + ")");
		}
		if (retrying && closed) {
			// Closed while this retry was testing: the owner is shutting down and no longer wants applies.
			return Outcome.REFUSED;
		}
		apply(Credentials.of(username, password), alreadyRefused);
		return Outcome.APPLIED;
	}

	/** Called holding {@code this}. */
	private Outcome refuse(final Credentials offer, final boolean alreadyRefused, final boolean retrying,
			final String reason) {
		if (!alreadyRefused) {
			stopRetrying();
			refused = Optional.of(offer);
			LOG.error("Rotated credentials for pool " + pool.getPoolName() + " (" + usernameProperty + ", "
					+ passwordProperty + ") were refused: " + reason + ". The pool keeps the credentials it has. "
					+ "Retrying them in " + initialRetryDelay.toSeconds() + " s, backing off to every "
					+ maxRetryDelay.toSeconds() + " s, until they work or the secret changes; rotate the database "
					+ "role before publishing its new password.");
			scheduleRetry();
		} else if (retrying) {
			failedRetries++;
			if (LOG.isDebugEnabled()) {
				LOG.debug("Retry " + failedRetries + " of the refused credentials for pool " + pool.getPoolName()
						+ " failed too: " + reason);
			}
			scheduleRetry();
		}
		// Otherwise an event re-offered the offer already being retried; that retry carries on unchanged.
		return Outcome.REFUSED;
	}

	/** Called holding {@code this}. */
	private void apply(final Credentials offer, final boolean afterRefusal) {
		final int failed = failedRetries;
		pool.setCredentials(offer);
		if (existing == ExistingConnections.SOFT_EVICT) {
			final HikariPoolMXBean running = pool.getHikariPoolMXBean();
			// Null until the pool's first connection, when there is nothing open to retire.
			if (running != null) {
				running.softEvictConnections();
			}
		}
		stopRetrying();
		lastApplied = Optional.of(offer);
		LOG.info("Rotated credentials for pool " + pool.getPoolName() + " verified and applied"
				+ (afterRefusal ? " after being refused at first (" + failed + " failed retries since)" : "")
				+ "; new connections use them" + (existing == ExistingConnections.SOFT_EVICT
						? " and idle ones are being retired now"
						: " and open ones retire at the pool's maxLifetime"));
		for (Consumer<? super Credentials> listener : appliedListeners) {
			tell(listener, offer);
		}
	}

	/** Tells one listener of an apply, isolating its failure. Called holding {@code this}. */
	private void tell(final Consumer<? super Credentials> listener, final Credentials applied) {
		try {
			listener.accept(applied);
		} catch (RuntimeException e) {
			LOG.error("A listener told of the credentials applied to pool " + pool.getPoolName() + " failed ("
					+ ExceptionTypes.of(e) + "); the pool uses them regardless, and the other listeners "
					+ "were still told");
		}
	}

	/**
	 * The catch-up at construction: if the secrets already hold credentials other than the pool's — a pool
	 * built from old properties after a rotation it never heard of — they are validated and applied now,
	 * or refused and retried, exactly as a reload would. An offer that cannot be read is logged and left
	 * to the next reload, so construction does not fail on it.
	 */
	private synchronized void catchUp() {
		try {
			attempt(false);
		} catch (RuntimeException e) {
			LOG.error("Pool " + pool.getPoolName() + " could not be caught up with " + usernameProperty + " and "
					+ passwordProperty + ": they cannot be read (" + ExceptionTypes.of(e) + "). The next change to "
					+ "the secrets tries again.");
		}
	}

	/** Called holding {@code this}. */
	private void scheduleRetry() {
		if (closed) {
			return;
		}
		final long generation = ++retryGeneration;
		final Duration delay = retryDelay(failedRetries);
		try {
			retry = Optional.of(retries.schedule(() -> runRetry(generation), delay.toMillis(), TimeUnit.MILLISECONDS));
		} catch (RejectedExecutionException e) {
			// close() shut the executor down between the check above and here.
			retry = Optional.empty();
		}
	}

	/** A scheduled retry; does nothing if retrying stopped or was rescheduled since it was scheduled. */
	private synchronized void runRetry(final long generation) {
		if (closed || generation != retryGeneration) {
			return;
		}
		retry = Optional.empty();
		try {
			attempt(true);
		} catch (RuntimeException e) {
			LOG.error("Retrying the refused credentials for pool " + pool.getPoolName() + " stopped: "
					+ usernameProperty + " or " + passwordProperty + " can no longer be read ("
					+ ExceptionTypes.of(e) + "). The next change to the secrets tries again.");
			stopRetrying();
		}
	}

	/** Forgets the refused offer and cancels its retry. Called holding {@code this}. */
	private void stopRetrying() {
		retry.ifPresent(pending -> pending.cancel(false));
		retry = Optional.empty();
		refused = Optional.empty();
		failedRetries = 0;
		retryGeneration++;
	}

	/** {@code initial}, doubling with each failed retry, capped at {@code max}. */
	private Duration retryDelay(final int failures) {
		final Duration delay = initialRetryDelay.multipliedBy(1L << Math.min(failures, 20));
		return delay.compareTo(maxRetryDelay) > 0 ? maxRetryDelay : delay;
	}

	private static boolean holds(final Credentials credentials, final @Nullable String username,
			final @Nullable String password) {
		return Objects.equals(credentials.getUsername(), username) && Objects.equals(credentials.getPassword(), password);
	}

	/**
	 * The username offered: the username property's value, or — when that is blank or absent, as for a
	 * configuration that rotates only the password — the username the pool holds now.
	 */
	private @Nullable String offeredUsername() {
		final String configured = properties.getProperty(usernameProperty);
		if (configured != null && !configured.isBlank()) {
			return configured;
		}
		return pool.getUsername();
	}

	/**
	 * Opens one connection exactly as the pool would open it with these credentials, runs the pool's
	 * {@code connectionInitSql} on it as the pool does on each of its own, checks it answers, and closes
	 * it.
	 *
	 * @throws SQLException if the connection cannot be opened, the init SQL fails, or it does not answer
	 */
	private void validate(final String username, final String password) throws SQLException {
		try (Connection connection = probeSource(username, password).getConnection(username, password)) {
			final String initSql = pool.getConnectionInitSql();
			if (initSql != null) {
				try (Statement statement = connection.createStatement()) {
					statement.execute(initSql);
				}
			}
			if (!connection.isValid(VALIDATION_TIMEOUT_SECONDS)) {
				throw new SQLException("Test connection opened but did not answer a validity check");
			}
		}
	}

	/**
	 * The DataSource the pool itself opens connections through, chosen in Hikari's own order
	 * ({@code PoolBase.initializeDataSource}): a supplied {@code DataSource}; else a fresh instance of
	 * {@code dataSourceClassName} given the pool's data-source properties, exactly as Hikari builds its
	 * own (Hikari then ignores any {@code jdbcUrl}); else a {@link DriverDataSource} over the
	 * {@code jdbcUrl}. The credentials are passed to {@code getConnection(username, password)}, the call
	 * the pool makes.
	 */
	private DataSource probeSource(final String username, final String password) {
		final DataSource supplied = pool.getDataSource();
		if (supplied != null) {
			return supplied;
		}
		final String className = pool.getDataSourceClassName();
		if (className != null) {
			final DataSource instance = UtilityElf.createInstance(className, DataSource.class);
			PropertyElf.setTargetFromProperties(instance, pool.getDataSourceProperties());
			return instance;
		}
		return new DriverDataSource(pool.getJdbcUrl(), pool.getDriverClassName(), pool.getDataSourceProperties(),
				username, password);
	}
}
