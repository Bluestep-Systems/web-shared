package dev.bluestep.secretfiles.spring.hikari;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.BooleanSupplier;

import org.h2.jdbcx.JdbcDataSource;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.jdbc.datasource.AbstractDataSource;
import org.springframework.mock.env.MockEnvironment;

import com.zaxxer.hikari.HikariDataSource;
import com.zaxxer.hikari.util.Credentials;

import dev.bluestep.secretfiles.spring.SecretsReloadedEvent;
import dev.bluestep.secretfiles.spring.SecretsReloadedListenerFactory;
import dev.bluestep.secretfiles.spring.hikari.HikariCredentialsRotator.ExistingConnections;
import dev.bluestep.secretfiles.spring.hikari.HikariCredentialsRotator.Outcome;

/**
 * Validate-then-swap against a real database running in this JVM: an H2 in-memory database whose
 * login user's password the test changes with {@code ALTER USER}, so "the new password works" and "the
 * old one no longer does" are facts about the database, not about a mock.
 *
 * <p>Both pool shapes the services run are covered: one built from a {@code jdbcUrl} (Boot's
 * {@code spring.datasource} pool) and one wrapping a supplied {@code DataSource} (web's pools wrap a
 * {@code PGSimpleDataSource}; here an H2 {@code JdbcDataSource}).</p>
 */
@DisplayName("HikariCredentialsRotator against H2")
@ExtendWith(OutputCaptureExtension.class)
class HikariCredentialsRotatorTest {

	private static final String USER = "ROTATOR";
	private static final String FIRST = "first-pw-5d1e";
	private static final String SECOND = "second-pw-a07b";
	private static final String USERNAME_PROPERTY = "db.username";
	private static final String PASSWORD_PROPERTY = "db.password";
	private static final Duration RETRY = Duration.ofMillis(50);
	private static final Duration MAX_RETRY = Duration.ofMillis(200);
	private static final Duration TIMEOUT = Duration.ofSeconds(20);

	private String url;
	private Connection admin;
	private HikariDataSource pool;
	private final List<HikariCredentialsRotator> rotators = new ArrayList<>();
	private final MockEnvironment environment = new MockEnvironment()
			.withProperty(USERNAME_PROPERTY, USER)
			.withProperty(PASSWORD_PROPERTY, FIRST);

	@BeforeEach
	void createDatabase() throws SQLException {
		// No DB_CLOSE_DELAY: only an administrator may set it, and the admin connection held open below
		// keeps the in-memory database alive for the test anyway.
		url = "jdbc:h2:mem:" + UUID.randomUUID();
		// The first connection creates the database, with this user as its administrator.
		admin = DriverManager.getConnection(url, "SA", "admin-pw");
		execute("CREATE USER " + USER + " PASSWORD '" + FIRST + "'");
	}

	@AfterEach
	void dropDatabase() throws SQLException {
		rotators.forEach(HikariCredentialsRotator::close);
		if (pool != null) {
			pool.close();
		}
		execute("SHUTDOWN");
		admin.close();
	}

	private void execute(final String sql) throws SQLException {
		try (Statement statement = admin.createStatement()) {
			statement.execute(sql);
		}
	}

	private HikariDataSource jdbcUrlPool() {
		pool = new HikariDataSource();
		pool.setPoolName("rotation-probe-url");
		pool.setJdbcUrl(url);
		pool.setUsername(USER);
		pool.setPassword(FIRST);
		pool.setMaximumPoolSize(2);
		return pool;
	}

	/** A pool over a supplied DataSource that carries the original login itself, as a PGSimpleDataSource can. */
	private HikariDataSource suppliedDataSourcePool() {
		final JdbcDataSource supplied = new JdbcDataSource();
		supplied.setURL(url);
		supplied.setUser(USER);
		supplied.setPassword(FIRST);
		pool = new HikariDataSource();
		pool.setPoolName("rotation-probe-supplied");
		pool.setDataSource(supplied);
		pool.setMaximumPoolSize(2);
		return pool;
	}

	private HikariCredentialsRotator rotator(final HikariDataSource rotated) {
		return track(new HikariCredentialsRotator(rotated, environment, USERNAME_PROPERTY, PASSWORD_PROPERTY,
				ExistingConnections.SOFT_EVICT));
	}

	/** A rotator that retries a refused offer every {@link #RETRY} to {@link #MAX_RETRY}, not every 5 s to 5 min. */
	private HikariCredentialsRotator quicklyRetrying(final HikariDataSource rotated) {
		return track(new HikariCredentialsRotator(rotated, environment, USERNAME_PROPERTY, PASSWORD_PROPERTY,
				ExistingConnections.SOFT_EVICT, RETRY, MAX_RETRY));
	}

	private HikariCredentialsRotator track(final HikariCredentialsRotator rotator) {
		rotators.add(rotator);
		return rotator;
	}

	/** Opens a connection the pool must authenticate afresh: every pooled one was soft-evicted. */
	private static String currentUserOfANewConnection(final HikariDataSource rotated) {
		if (rotated.getHikariPoolMXBean() != null) {
			rotated.getHikariPoolMXBean().softEvictConnections();
		}
		try (Connection connection = rotated.getConnection();
				Statement statement = connection.createStatement();
				ResultSet rows = statement.executeQuery("SELECT CURRENT_USER")) {
			rows.next();
			return rows.getString(1);
		} catch (SQLException e) {
			throw new AssertionError("The pool could not open a new connection", e);
		}
	}

	@Test
	@DisplayName("jdbcUrl pool: a rotated password that works is applied, and new connections use it")
	void jdbcUrlPoolAppliesAWorkingPassword(final CapturedOutput output) throws SQLException {
		final HikariDataSource rotated = jdbcUrlPool();
		assertThat(currentUserOfANewConnection(rotated)).isEqualTo(USER);
		final HikariCredentialsRotator rotator = rotator(rotated);

		execute("ALTER USER " + USER + " SET PASSWORD '" + SECOND + "'");
		environment.setProperty(PASSWORD_PROPERTY, SECOND);

		assertThat(rotator.rotate()).isEqualTo(Outcome.APPLIED);
		assertThat(rotated.getCredentials().getPassword()).isEqualTo(SECOND);
		assertThat(currentUserOfANewConnection(rotated))
				.as("the old password is no longer the user's, so only the new one can open this")
				.isEqualTo(USER);
		assertThat(output).doesNotContain(FIRST).doesNotContain(SECOND);
	}

	@Test
	@DisplayName("jdbcUrl pool: a rotated password that does not work is refused, and the pool keeps working")
	void jdbcUrlPoolRefusesABadPassword(final CapturedOutput output) {
		final HikariDataSource rotated = jdbcUrlPool();
		environment.setProperty(PASSWORD_PROPERTY, SECOND);

		final HikariCredentialsRotator rotator = rotator(rotated);
		assertThat(rotator.rotate()).isEqualTo(Outcome.REFUSED);
		assertThat(rotated.getCredentials().getPassword()).isEqualTo(FIRST);
		assertThat(currentUserOfANewConnection(rotated)).isEqualTo(USER);
		assertThat(rotator.rotate()).as("and stays refused until the secret is corrected").isEqualTo(Outcome.REFUSED);
		assertThat(output).contains("ERROR").contains("SQLState 28000").contains("rotation-probe-url")
				.doesNotContain(FIRST).doesNotContain(SECOND);
	}

	@Test
	@DisplayName("unchanged credentials are neither re-validated nor re-applied")
	void unchangedIsANoOp() {
		final HikariDataSource rotated = jdbcUrlPool();
		final Credentials before = rotated.getCredentials();

		assertThat(rotator(rotated).rotate()).isEqualTo(Outcome.UNCHANGED);
		assertThat(rotated.getCredentials()).as("not even replaced by an equal pair").isSameAs(before);
	}

	@Test
	@DisplayName("a blank or absent password is refused without trying it")
	void blankIsRefused(final CapturedOutput output) {
		final HikariDataSource rotated = jdbcUrlPool();
		environment.setProperty(PASSWORD_PROPERTY, " ");

		assertThat(rotator(rotated).rotate()).isEqualTo(Outcome.REFUSED);
		assertThat(rotated.getCredentials().getPassword()).isEqualTo(FIRST);
		assertThat(output).as("refused for being blank, not after a failed login")
				.contains(PASSWORD_PROPERTY + " is now blank or absent").doesNotContain("SQLState");
	}

	@Test
	@DisplayName("supplied-DataSource pool: a working password is validated through that DataSource and applied")
	void suppliedDataSourcePoolAppliesAWorkingPassword() throws SQLException {
		final HikariDataSource rotated = suppliedDataSourcePool();
		assertThat(currentUserOfANewConnection(rotated)).isEqualTo(USER);
		final HikariCredentialsRotator rotator = rotator(rotated);

		execute("ALTER USER " + USER + " SET PASSWORD '" + SECOND + "'");
		environment.setProperty(PASSWORD_PROPERTY, SECOND);

		assertThat(rotator.rotate()).isEqualTo(Outcome.APPLIED);
		assertThat(rotated.getCredentials().getUsername()).isEqualTo(USER);
		assertThat(rotated.getCredentials().getPassword()).isEqualTo(SECOND);
		assertThat(currentUserOfANewConnection(rotated))
				.as("the supplied DataSource still carries the old password; only the pool's credentials work")
				.isEqualTo(USER);
	}

	@Test
	@DisplayName("supplied-DataSource pool: a bad password is refused and the pool keeps working")
	void suppliedDataSourcePoolRefusesABadPassword() {
		final HikariDataSource rotated = suppliedDataSourcePool();
		environment.setProperty(PASSWORD_PROPERTY, SECOND);

		assertThat(rotator(rotated).rotate()).isEqualTo(Outcome.REFUSED);
		assertThat(rotated.getCredentials().getPassword()).as("still the pool's own (none)").isNull();
		assertThat(currentUserOfANewConnection(rotated)).isEqualTo(USER);
	}

	@Test
	@DisplayName("a pool reading a credentials provider is refused at construction")
	void credentialsProviderPoolIsRefused() {
		final HikariDataSource rotated = jdbcUrlPool();
		rotated.setCredentialsProvider(() -> Credentials.of(USER, FIRST));

		assertThatThrownBy(() -> rotator(rotated))
				.isInstanceOf(IllegalArgumentException.class)
				.hasMessageContaining("HikariCredentialsProvider");
	}

	@Test
	@DisplayName("as a bean, it rotates on SecretsReloadedEvent")
	void rotatesOnTheEvent() throws SQLException {
		final HikariDataSource rotated = jdbcUrlPool();
		try (AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext()) {
			context.setEnvironment(environment);
			context.registerBean(SecretsReloadedListenerFactory.class);
			context.registerBean(HikariCredentialsRotator.class, () -> rotator(rotated));
			context.refresh();
			// Changed after the rotator exists, so only the event can apply it.
			execute("ALTER USER " + USER + " SET PASSWORD '" + SECOND + "'");
			environment.setProperty(PASSWORD_PROPERTY, SECOND);

			context.publishEvent(new SecretsReloadedEvent(Path.of("/var/lib/bluestep/secrets")));

			assertThat(rotated.getCredentials().getPassword()).isEqualTo(SECOND);
		}
	}
	@Test
	@DisplayName("a refused offer is retried, and applied once the role catches up — with no further secret change")
	void refusedOfferIsRetriedUntilTheRoleCatchesUp(final CapturedOutput output) throws Exception {
		final HikariDataSource rotated = jdbcUrlPool();
		final List<Credentials> applied = new CopyOnWriteArrayList<>();
		final List<String> threads = new CopyOnWriteArrayList<>();
		final HikariCredentialsRotator rotator = quicklyRetrying(rotated).onApplied(credentials -> {
			applied.add(credentials);
			threads.add(Thread.currentThread().getName());
		});
		// The Secret was updated before the ALTER ran.
		environment.setProperty(PASSWORD_PROPERTY, SECOND);

		assertThat(rotator.rotate()).isEqualTo(Outcome.REFUSED);
		assertThat(rotator.retryPending()).isTrue();
		// Let a few retries fail first, so the success is a retry's and not the first one's.
		Thread.sleep(MAX_RETRY.multipliedBy(3));
		assertThat(rotated.getCredentials().getPassword()).isEqualTo(FIRST);

		execute("ALTER USER " + USER + " SET PASSWORD '" + SECOND + "'");
		await(() -> SECOND.equals(rotated.getCredentials().getPassword()), "a retry applied the offer");

		assertThat(currentUserOfANewConnection(rotated)).isEqualTo(USER);
		assertThat(rotator.retryPending()).as("retrying stops once applied").isFalse();
		assertThat(applied).as("the callback heard the retry's apply, once")
				.singleElement().satisfies(credentials -> {
					assertThat(credentials.getUsername()).isEqualTo(USER);
					assertThat(credentials.getPassword()).isEqualTo(SECOND);
				});
		assertThat(threads).singleElement().asString().startsWith("secret-files-credentials-retry-");
		assertThat(output.getAll().split("were refused", -1)).as("the refusal is logged once").hasSize(2);
		assertThat(output).contains("verified and applied after being refused at first")
				.doesNotContain(FIRST).doesNotContain(SECOND);
	}

	@Test
	@DisplayName("a new offer supersedes the one being retried: the old retry is cancelled, a refused new one retries itself")
	void aNewOfferSupersedesTheRetry(final CapturedOutput output) throws Exception {
		final HikariDataSource rotated = jdbcUrlPool();
		final HikariCredentialsRotator rotator = quicklyRetrying(rotated);
		environment.setProperty(PASSWORD_PROPERTY, "never-the-password-1");
		assertThat(rotator.rotate()).isEqualTo(Outcome.REFUSED);
		assertThat(rotator.retryPending()).isTrue();

		// Reverted to what the pool holds: nothing left to retry.
		environment.setProperty(PASSWORD_PROPERTY, FIRST);
		assertThat(rotator.rotate()).isEqualTo(Outcome.UNCHANGED);
		assertThat(rotator.retryPending()).as("the reverted offer's retry was cancelled").isFalse();

		// A second bad offer is refused (and logged) in its own right, and retried until it works.
		environment.setProperty(PASSWORD_PROPERTY, SECOND);
		assertThat(rotator.rotate()).isEqualTo(Outcome.REFUSED);
		assertThat(rotator.retryPending()).isTrue();
		assertThat(output.getAll().split("were refused", -1)).as("each offer's refusal is logged once").hasSize(3);

		// A third offer that works supersedes it at once, and its retry is cancelled.
		final String third = "third-pw-77c1";
		execute("ALTER USER " + USER + " SET PASSWORD '" + third + "'");
		environment.setProperty(PASSWORD_PROPERTY, third);
		assertThat(rotator.rotate()).isEqualTo(Outcome.APPLIED);
		assertThat(rotator.retryPending()).as("the superseded offer's retry was cancelled").isFalse();
		assertThat(rotated.getCredentials().getPassword()).isEqualTo(third);
	}

	@Test
	@DisplayName("close() cancels the retry: the role catching up afterwards changes nothing")
	void closeCancelsTheRetry() throws Exception {
		final HikariDataSource rotated = jdbcUrlPool();
		final List<Credentials> applied = new CopyOnWriteArrayList<>();
		final HikariCredentialsRotator rotator = quicklyRetrying(rotated).onApplied(applied::add);
		environment.setProperty(PASSWORD_PROPERTY, SECOND);
		assertThat(rotator.rotate()).isEqualTo(Outcome.REFUSED);
		assertThat(rotator.retryPending()).isTrue();

		rotator.close();
		assertThat(rotator.retryPending()).isFalse();
		execute("ALTER USER " + USER + " SET PASSWORD '" + SECOND + "'");
		Thread.sleep(MAX_RETRY.multipliedBy(10));

		assertThat(rotated.getCredentials().getPassword()).isEqualTo(FIRST);
		assertThat(applied).isEmpty();
		rotator.close();
	}

	@Test
	@DisplayName("onApplied hears an apply on the event path; a throwing listener neither undoes it nor silences the next")
	void onAppliedHearsTheEventPath(final CapturedOutput output) throws SQLException {
		final HikariDataSource rotated = jdbcUrlPool();
		final List<Credentials> applied = new CopyOnWriteArrayList<>();
		try (AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext()) {
			context.setEnvironment(environment);
			context.registerBean(SecretsReloadedListenerFactory.class);
			context.registerBean(HikariCredentialsRotator.class, () -> rotator(rotated)
					.onApplied(credentials -> {
						throw new IllegalStateException("listener quoting " + credentials.getPassword());
					})
					.onApplied(applied::add));
			context.refresh();
			// Changed after the rotator exists, so only the event can apply it.
			execute("ALTER USER " + USER + " SET PASSWORD '" + SECOND + "'");
			environment.setProperty(PASSWORD_PROPERTY, SECOND);

			context.publishEvent(new SecretsReloadedEvent(Path.of("/var/lib/bluestep/secrets")));
		}

		assertThat(rotated.getCredentials().getPassword()).isEqualTo(SECOND);
		assertThat(applied).singleElement().extracting(Credentials::getPassword).isEqualTo(SECOND);
		assertThat(output).contains("failed (java.lang.IllegalStateException)").doesNotContain(SECOND)
				.doesNotContain("listener quoting");
	}

	@Test
	@DisplayName("refusals, unchanged offers and blank offers never reach onApplied")
	void onAppliedHearsOnlyApplies() {
		final HikariDataSource rotated = jdbcUrlPool();
		final List<Credentials> applied = new CopyOnWriteArrayList<>();
		final HikariCredentialsRotator rotator = rotator(rotated).onApplied(applied::add);

		assertThat(rotator.rotate()).isEqualTo(Outcome.UNCHANGED);
		environment.setProperty(PASSWORD_PROPERTY, SECOND);
		assertThat(rotator.rotate()).isEqualTo(Outcome.REFUSED);
		environment.setProperty(PASSWORD_PROPERTY, " ");
		assertThat(rotator.rotate()).isEqualTo(Outcome.REFUSED);
		assertThat(rotator.retryPending()).as("a blank offer is not retried").isFalse();

		assertThat(applied).isEmpty();
	}

	@Test
	@DisplayName("a test connection failing with an unchecked exception is logged by type, never by message")
	void uncheckedValidationFailureIsLoggedByTypeOnly(final CapturedOutput output) {
		final AbstractDataSource leaky = new AbstractDataSource() {
			@Override
			public Connection getConnection() {
				throw new UnsupportedOperationException("the rotator passes the offered credentials");
			}

			@Override
			public Connection getConnection(final String user, final String password) {
				throw new IllegalStateException("driver quoting " + password,
						new IllegalArgumentException("still " + password));
			}
		};
		pool = new HikariDataSource();
		pool.setPoolName("rotation-probe-leaky");
		pool.setDataSource(leaky);
		environment.setProperty(PASSWORD_PROPERTY, SECOND);

		assertThat(rotator(pool).rotate()).isEqualTo(Outcome.REFUSED);
		assertThat(output).contains("could not be tested (java.lang.IllegalStateException <- "
				+ "java.lang.IllegalArgumentException)").doesNotContain(SECOND).doesNotContain("driver quoting");
	}

	@Test
	@DisplayName("dataSourceClassName pool: the probe connects where the pool does, not to the jdbcUrl Hikari ignores")
	void dataSourceClassNamePoolIsProbedThroughItsOwnDataSource() throws SQLException {
		// Two databases with the same user. The pool's jdbcUrl names the first, but its dataSourceClassName
		// wins in Hikari and connects to the second.
		final String classBacked = "jdbc:h2:mem:" + UUID.randomUUID();
		try (Connection classBackedAdmin = DriverManager.getConnection(classBacked, "SA", "admin-pw")) {
			execute(classBackedAdmin, "CREATE USER " + USER + " PASSWORD '" + FIRST + "'");
			pool = new HikariDataSource();
			pool.setPoolName("rotation-probe-class");
			pool.setJdbcUrl(url);
			pool.setDataSourceClassName(JdbcDataSource.class.getName());
			pool.addDataSourceProperty("url", classBacked);
			pool.setUsername(USER);
			pool.setPassword(FIRST);
			pool.setMaximumPoolSize(2);
			final HikariCredentialsRotator rotator = rotator(pool);

			// Valid on the jdbcUrl's database only.
			execute("ALTER USER " + USER + " SET PASSWORD '" + SECOND + "'");
			environment.setProperty(PASSWORD_PROPERTY, SECOND);
			assertThat(rotator.rotate()).as("the pool's own database still wants the old password")
					.isEqualTo(Outcome.REFUSED);
			assertThat(pool.getCredentials().getPassword()).isEqualTo(FIRST);

			execute(classBackedAdmin, "ALTER USER " + USER + " SET PASSWORD '" + SECOND + "'");
			assertThat(rotator.rotate()).isEqualTo(Outcome.APPLIED);
			assertThat(currentUserOfANewConnection(pool)).isEqualTo(USER);
			execute(classBackedAdmin, "SHUTDOWN");
		}
	}

	@Test
	@DisplayName("a pool configured by dataSourceClassName alone is accepted and rotated")
	void dataSourceClassNameOnlyPoolRotates() throws SQLException {
		pool = new HikariDataSource();
		pool.setPoolName("rotation-probe-class-only");
		pool.setDataSourceClassName(JdbcDataSource.class.getName());
		pool.addDataSourceProperty("url", url);
		pool.setUsername(USER);
		pool.setPassword(FIRST);
		pool.setMaximumPoolSize(2);
		final HikariCredentialsRotator rotator = rotator(pool);

		execute("ALTER USER " + USER + " SET PASSWORD '" + SECOND + "'");
		environment.setProperty(PASSWORD_PROPERTY, SECOND);

		assertThat(rotator.rotate()).isEqualTo(Outcome.APPLIED);
		assertThat(currentUserOfANewConnection(pool)).isEqualTo(USER);
	}

	@Test
	@DisplayName("the probe runs the pool's connectionInitSql: credentials it fails for are refused, and retried")
	void connectionInitSqlFailingIsARefusal(final CapturedOutput output) throws Exception {
		final String other = "ROTATOR_TWO";
		execute("CREATE TABLE PROBE_T (ID INT)");
		execute("GRANT SELECT ON PROBE_T TO " + USER);
		// Logs in fine, but may not run the pool's init SQL.
		execute("CREATE USER " + other + " PASSWORD '" + SECOND + "'");
		final HikariDataSource rotated = jdbcUrlPool();
		rotated.setConnectionInitSql("SELECT COUNT(*) FROM PUBLIC.PROBE_T");
		final HikariCredentialsRotator rotator = quicklyRetrying(rotated);

		environment.setProperty(USERNAME_PROPERTY, other);
		environment.setProperty(PASSWORD_PROPERTY, SECOND);
		assertThat(rotator.rotate()).isEqualTo(Outcome.REFUSED);
		assertThat(rotator.retryPending()).isTrue();
		assertThat(rotated.getCredentials().getUsername()).isEqualTo(USER);
		assertThat(output).contains("a test connection with them failed").doesNotContain(SECOND);

		execute("GRANT SELECT ON PROBE_T TO " + other);
		await(() -> other.equals(rotated.getCredentials().getUsername()), "a retry applied the offer");
		assertThat(currentUserOfANewConnection(rotated)).isEqualTo(other);
	}

	@Test
	@DisplayName("with no username property, the password rotates against the pool's own username")
	void passwordOnlyRotates() throws SQLException {
		final MockEnvironment passwordOnly = new MockEnvironment().withProperty(PASSWORD_PROPERTY, FIRST);
		final HikariDataSource rotated = jdbcUrlPool();
		final HikariCredentialsRotator rotator = track(new HikariCredentialsRotator(rotated, passwordOnly,
				USERNAME_PROPERTY, PASSWORD_PROPERTY, ExistingConnections.SOFT_EVICT));

		execute("ALTER USER " + USER + " SET PASSWORD '" + SECOND + "'");
		passwordOnly.setProperty(PASSWORD_PROPERTY, SECOND);

		assertThat(rotator.rotate()).isEqualTo(Outcome.APPLIED);
		assertThat(rotated.getCredentials().getUsername()).isEqualTo(USER);
		assertThat(rotated.getCredentials().getPassword()).isEqualTo(SECOND);
		assertThat(currentUserOfANewConnection(rotated)).isEqualTo(USER);
	}

	@Test
	@DisplayName("with neither a username property nor a pool username, the password is refused untried")
	void noUsernameAnywhereIsRefused(final CapturedOutput output) {
		final MockEnvironment passwordOnly = new MockEnvironment().withProperty(PASSWORD_PROPERTY, SECOND);
		pool = new HikariDataSource();
		pool.setPoolName("rotation-probe-no-user");
		pool.setJdbcUrl(url);

		final HikariCredentialsRotator rotator = track(new HikariCredentialsRotator(pool, passwordOnly,
				USERNAME_PROPERTY, PASSWORD_PROPERTY));

		assertThat(rotator.rotate()).isEqualTo(Outcome.REFUSED);
		assertThat(rotator.retryPending()).isFalse();
		assertThat(output).contains(USERNAME_PROPERTY + " is blank or absent and pool rotation-probe-no-user has "
				+ "no username of its own").doesNotContain("SQLState").doesNotContain(SECOND);
	}

	@Test
	@DisplayName("a rotator created after the secret moved catches its pool up, with no reload event at all")
	void aNewRotatorCatchesUpWithoutAnEvent() throws Exception {
		// Pool A refused the new password (published before ALTER ROLE) and is retrying; pool B is built
		// now from the old properties and never hears that earlier event.
		environment.setProperty(PASSWORD_PROPERTY, SECOND);
		final HikariDataSource rotated = jdbcUrlPool();

		final HikariCredentialsRotator rotator = quicklyRetrying(rotated);
		assertThat(rotator.retryPending()).as("refused at construction, and retrying").isTrue();

		execute("ALTER USER " + USER + " SET PASSWORD '" + SECOND + "'");
		await(() -> SECOND.equals(rotated.getCredentials().getPassword()), "the pool converged");
		assertThat(currentUserOfANewConnection(rotated)).isEqualTo(USER);
	}

	@Test
	@DisplayName("onApplied registered after the construction catch-up applied is told of it at once")
	void onAppliedHearsTheCatchUp() throws SQLException {
		execute("ALTER USER " + USER + " SET PASSWORD '" + SECOND + "'");
		environment.setProperty(PASSWORD_PROPERTY, SECOND);
		final HikariDataSource rotated = jdbcUrlPool();
		final List<Credentials> applied = new CopyOnWriteArrayList<>();

		rotator(rotated).onApplied(applied::add);

		assertThat(rotated.getCredentials().getPassword()).isEqualTo(SECOND);
		assertThat(applied).singleElement().extracting(Credentials::getPassword).isEqualTo(SECOND);
	}

	private static void execute(final Connection connection, final String sql) throws SQLException {
		try (Statement statement = connection.createStatement()) {
			statement.execute(sql);
		}
	}

	private static void await(final BooleanSupplier condition, final String what) throws InterruptedException {
		final long deadline = System.nanoTime() + TIMEOUT.toNanos();
		while (!condition.getAsBoolean()) {
			if (System.nanoTime() - deadline > 0) {
				throw new AssertionError(what + ": not within " + TIMEOUT);
			}
			Thread.sleep(10);
		}
	}
}
