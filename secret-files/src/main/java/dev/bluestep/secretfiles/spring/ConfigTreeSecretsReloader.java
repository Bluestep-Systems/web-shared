package dev.bluestep.secretfiles.spring;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.SortedSet;
import java.util.TreeSet;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.springframework.boot.env.ConfigTreePropertySource;
import org.springframework.boot.env.ConfigTreePropertySource.Option;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.SmartLifecycle;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.PropertySource;

import dev.bluestep.secretfiles.ExceptionTypes;
import dev.bluestep.secretfiles.PinnedGeneration;
import dev.bluestep.secretfiles.SecretDirectory;
import dev.bluestep.secretfiles.SecretFiles;
import dev.bluestep.secretfiles.WatchedSecretDirectory;

/**
 * Keeps every config tree in the {@code Environment} in step with the directory it was read from, and
 * publishes {@link SecretsReloadedEvent} when one changes. Auto-configured by
 * {@link SecretFilesAutoConfiguration}.
 *
 * <h2>What it watches</h2>
 *
 * <p>Whatever {@code spring.config.import: optional:configtree:<dir>/} put into the Environment, and
 * nothing else: at {@link #start()} it finds each {@link ConfigTreePropertySource} and follows that
 * source's directory with a {@link WatchedSecretDirectory}. The import is therefore the one place the
 * mount path is written down. Where the directory does not exist the optional import added nothing, so
 * there is nothing to watch and this does nothing at all — local development and test suites run that
 * way, with every secret pinned by an environment variable or a property.</p>
 *
 * <p>One mistake is refused rather than tolerated: the conventional mount directory
 * ({@link SecretFiles#DEFAULT_DIRECTORY}, or whatever {@value SecretFiles#DIRECTORY_PROPERTY} names)
 * exists but no config tree imports it. The chart mounted secrets the application will never read,
 * and every placeholder that should resolve from them silently falls back to its default. Starting
 * fails instead, naming the import to add.</p>
 *
 * <h2>What a change does</h2>
 *
 * <p>The core library's notification is only the trigger. The truth is re-read from disk into a fresh
 * {@link ConfigTreePropertySource} with the same name and the same options the source at startup had,
 * so a reloaded value reads exactly as the value read at startup did: the mount directory untrimmed,
 * each file's exact bytes ({@link ExactSecretTreePostProcessor}), and any other config tree with the
 * {@link Option#AUTO_TRIM_TRAILING_NEW_LINE} Boot's import uses. The re-read is pinned to one kubelet generation, as the
 * core's is ({@link PinnedGeneration}): it is kept only if {@code ..data} named the same timestamped
 * directory before and after every value was read, and if Boot listed exactly that generation's keys;
 * a swap landing mid-read discards it, so the Environment is never given values from two versions of
 * the Secret. Every value is read eagerly — the source caches each one, so it never opens a file again
 * once it is in the Environment — and compared with what the Environment currently serves. Only a real
 * difference replaces the source
 * ({@code MutablePropertySources.replace}: same name, same position, so precedence does not move) and
 * publishes the event. Compare, replace and publish happen under one lock, so a listener never sees an
 * older Environment after a newer one.</p>
 *
 * <h2>A re-read that fails is retried until it succeeds</h2>
 *
 * <p>Boot's reader and the core's differ in one way that matters: Boot lists the visible links, the core
 * reads the timestamped directory itself. Just after the kubelet's rename, a link for a key it is
 * removing still dangles and a link for a key it is adding may not exist yet. The core reports the
 * consistent new state; Boot's listing fails to read the one or misses the other, and the re-read is
 * refused. The core then has nothing further to report — its own view is already
 * current — so a reloader that gave up after a failed re-read stayed stale until the Secret next
 * changed, which may be never. Here a failed re-read schedules another, with backoff doubling from
 * {@code 200 ms} to a ceiling of {@code 5 s}, and keeps retrying at the ceiling until one succeeds or a
 * fresh notification succeeds first. It warns on the first failure, logs an error once the ceiling is
 * reached, and says when it recovers.</p>
 *
 * <p>Building the replacement from the core's {@code SecretSnapshot} instead was rejected: the
 * replacement would no longer be a {@code ConfigTreePropertySource}, so it would lose Boot's origin
 * tracking and its {@code InputStreamSource} values, and could not be found again by the next
 * {@link #start()}. Building it over the timestamped directory itself is impossible: Boot skips every
 * path with a {@code ..}-prefixed element, which is every path inside one. Retrying keeps the
 * Environment holding exactly what Boot's own import would have built.</p>
 *
 * <p>The core and Boot read the same keys: single-dot names such as {@code .token} included, the
 * kubelet's {@code ..}-prefixed entries skipped. A subdirectory, which Boot would read as dotted keys, is
 * refused: a Secret volume cannot hold one, and {@link #start()} fails naming it.</p>
 *
 * <h2>Startup and shutdown</h2>
 *
 * <p>Boot reads the config tree long before this bean starts, and beans read secrets in between. A swap
 * landing in that window would otherwise never be noticed, so {@link #start()} subscribes first and
 * then re-reads once, publishing if anything moved. {@link #stop()} marks everything stopped under the
 * lock and closes the watchers outside it (closing waits for a watcher thread that may be waiting for
 * the lock); nothing is published once it returns, a pending retry included.</p>
 *
 * <p>Never logs a value: log lines name the directory, keys and exception types.</p>
 */
public final class ConfigTreeSecretsReloader implements SmartLifecycle {

	/** The first retry of a failed re-read waits this long. */
	static final Duration INITIAL_RETRY_DELAY = Duration.ofMillis(200);

	/** Retries back off, doubling, to this ceiling, and then continue at it. */
	static final Duration MAX_RETRY_DELAY = Duration.ofSeconds(5);

	private static final Log LOG = LogFactory.getLog(ConfigTreeSecretsReloader.class);

	private final ConfigurableEnvironment environment;
	private final ApplicationEventPublisher events;
	private final Path conventionalDirectory;
	private final Duration pollInterval;
	/** Test seam; does nothing in production. */
	private final Consumer<String> afterEachValue;

	/** Guards trees, retries, every Tree's state, and the replace-and-publish. */
	private final Object lock = new Object();
	/** The config trees followed while running; empty when stopped. Guarded by {@link #lock}. */
	private final List<Tree> trees = new ArrayList<>();
	/** Runs retries while running with at least one tree. Guarded by {@link #lock}. */
	private Optional<ScheduledExecutorService> retries = Optional.empty();
	/** Written under {@link #lock}; read without it by {@link #isRunning()}. */
	private volatile boolean running;

	/**
	 * Follows the config trees in {@code environment}, polling each at least every
	 * {@link WatchedSecretDirectory#DEFAULT_POLL_INTERVAL}.
	 *
	 * @param environment           the application's Environment, whose config trees are followed and
	 *                              replaced
	 * @param events                where {@link SecretsReloadedEvent} is published
	 * @param conventionalDirectory the directory the chart mounts; if it exists, a config tree must
	 *                              import it
	 */
	public ConfigTreeSecretsReloader(final ConfigurableEnvironment environment, final ApplicationEventPublisher events,
			final Path conventionalDirectory) {
		this(environment, events, conventionalDirectory, WatchedSecretDirectory.DEFAULT_POLL_INTERVAL);
	}

	/** Test seam: polls each tree every {@code pollInterval}. */
	ConfigTreeSecretsReloader(final ConfigurableEnvironment environment, final ApplicationEventPublisher events,
			final Path conventionalDirectory, final Duration pollInterval) {
		this(environment, events, conventionalDirectory, pollInterval, property -> { });
	}

	/**
	 * Test seam: {@code afterEachValue} runs after each value a re-read takes from disk, so a test can
	 * swap the volume between two of them.
	 */
	ConfigTreeSecretsReloader(final ConfigurableEnvironment environment, final ApplicationEventPublisher events,
			final Path conventionalDirectory, final Duration pollInterval, final Consumer<String> afterEachValue) {
		this.environment = Objects.requireNonNull(environment, "environment");
		this.events = Objects.requireNonNull(events, "events");
		this.conventionalDirectory = conventionalDirectory.toAbsolutePath().normalize();
		this.pollInterval = Objects.requireNonNull(pollInterval, "pollInterval");
		this.afterEachValue = Objects.requireNonNull(afterEachValue, "afterEachValue");
	}

	/**
	 * Starts following each config tree in the Environment and catches each up once; does nothing when
	 * there is none.
	 *
	 * @throws IllegalStateException        if the conventional mount directory exists but no config tree
	 *                                      imports it
	 * @throws IllegalArgumentException     if an imported directory holds a subdirectory
	 * @throws java.io.UncheckedIOException if an imported directory cannot be watched
	 */
	@Override
	public void start() {
		synchronized (lock) {
			if (running) {
				return;
			}
			final List<ConfigTreePropertySource> imported = new ArrayList<>();
			for (PropertySource<?> source : environment.getPropertySources()) {
				if (source instanceof ConfigTreePropertySource tree) {
					imported.add(tree);
				}
			}
			requireConventionalDirectoryImported(imported);
			if (imported.isEmpty()) {
				LOG.info("No config tree is imported, so no mounted secrets are watched: every secret is fixed for "
						+ "the life of this process");
				running = true;
				return;
			}
			final ScheduledExecutorService executor = Executors.newSingleThreadScheduledExecutor(
					Thread.ofVirtual().name("secret-files-reload-retry").factory());
			try {
				for (ConfigTreePropertySource source : imported) {
					final SecretDirectory directory = WatchedSecretDirectory.open(source.getSource(), pollInterval);
					final Tree tree = new Tree(source, directory,
							ExactSecretTreePostProcessor.isSecretsDirectory(source.getSource(), conventionalDirectory));
					trees.add(tree);
					// Subscribe, then catch up below: a swap since Boot read the tree is seen now or notified later.
					directory.subscribe(snapshot -> refresh(tree));
				}
			} catch (RuntimeException e) {
				for (Tree tree : trees) {
					tree.directory.close();
				}
				trees.clear();
				executor.shutdownNow();
				throw e;
			}
			retries = Optional.of(executor);
			running = true;
			for (Tree tree : trees) {
				LOG.info("Watching mounted secrets in " + tree.path + " for property source [" + tree.name + "]");
				refresh(tree);
			}
		}
	}

	/**
	 * Stops following every directory. Idempotent. The Environment keeps the last values it was given,
	 * and nothing is published once this returns.
	 */
	@Override
	public void stop() {
		final List<Tree> closing;
		final Optional<ScheduledExecutorService> executor;
		synchronized (lock) {
			running = false;
			for (Tree tree : trees) {
				tree.cancelRetry();
				tree.active = false;
			}
			closing = List.copyOf(trees);
			trees.clear();
			executor = retries;
			retries = Optional.empty();
		}
		// Outside the lock: close() joins the watcher thread, which may be waiting for the lock to deliver
		// a change — and finds the tree inactive once it gets it.
		for (Tree tree : closing) {
			tree.directory.close();
		}
		executor.ifPresent(ScheduledExecutorService::shutdownNow);
	}

	@Override
	public boolean isRunning() {
		return running;
	}

	/**
	 * Test seam: how many config trees are being followed.
	 */
	int watchedTrees() {
		synchronized (lock) {
			return trees.size();
		}
	}

	/**
	 * Test seam: the refresh each followed tree's watcher runs, captured so a test can run one after
	 * {@link #stop()} — as a notification already in flight when it was called would.
	 */
	List<Runnable> refreshers() {
		synchronized (lock) {
			return trees.stream().<Runnable>map(tree -> () -> refresh(tree)).toList();
		}
	}

	private void requireConventionalDirectoryImported(final List<ConfigTreePropertySource> imported) {
		if (!Files.isDirectory(conventionalDirectory)) {
			return;
		}
		for (ConfigTreePropertySource tree : imported) {
			if (tree.getSource().toAbsolutePath().normalize().equals(conventionalDirectory)) {
				return;
			}
		}
		throw new IllegalStateException("The secret directory " + conventionalDirectory + " is mounted but no "
				+ "config tree imports it, so the secrets in it would never be read. Add "
				+ "spring.config.import: optional:configtree:" + conventionalDirectory + "/ to application.yml, or set "
				+ SecretFiles.DIRECTORY_PROPERTY + " to the directory the import names.");
	}

	/**
	 * Re-reads {@code tree} and, if what it would serve differs from what the Environment serves,
	 * replaces it and publishes. A failed re-read schedules a retry.
	 */
	private void refresh(final Tree tree) {
		synchronized (lock) {
			if (!running || !tree.active) {
				return;
			}
			final Reread reread;
			try {
				reread = reread(tree);
			} catch (IOException | RuntimeException e) {
				scheduleRetry(tree, e);
				return;
			}
			final ConfigTreePropertySource fresh = reread.source();
			final Map<String, String> values = reread.values();
			tree.recovered();
			if (tree.applied.isPresent() && tree.applied.get().equals(values)) {
				return;
			}
			if (!environment.getPropertySources().contains(tree.name)) {
				LOG.warn("Property source [" + tree.name + "] is no longer in the Environment, so the change to "
						+ tree.path + " cannot be applied");
				return;
			}
			final Map<String, String> before = tree.applied.orElse(Map.of());
			environment.getPropertySources().replace(tree.name, fresh);
			tree.applied = Optional.of(values);
			LOG.info("Mounted secrets in " + tree.path + " changed (" + describeChange(before, values)
					+ "); property source [" + tree.name + "] replaced");
			try {
				events.publishEvent(new SecretsReloadedEvent(tree.path));
			} catch (RuntimeException e) {
				// Only a listener outside SecretsReloadedListenerFactory's reach can get here. The
				// Environment is already current; what is lost is the listeners after that one.
				LOG.error("A SecretsReloadedEvent listener for " + tree.path + " threw ("
						+ ExceptionTypes.of(e) + "); listeners after it were not told of this change. "
						+ "Listen with @EventListener on SecretsReloadedEvent so each listener is isolated.");
			}
		}
	}

	/**
	 * Builds the replacement source exactly as Boot's import does, pinned to one kubelet generation: it
	 * is kept only if {@code ..data} named the same timestamped directory before and after every value
	 * was read — so every read through a {@code KEY -> ..data/KEY} link landed in that one generation —
	 * and if the keys Boot listed are exactly that generation's keys, which they are not while the
	 * kubelet is still adding or removing visible links after its rename. Otherwise the read is done
	 * again ({@link PinnedGeneration#read}) or fails, and a failure is retried by the caller.
	 *
	 * <p>Every value is read here and cached by the source (it is built without
	 * {@link Option#ALWAYS_READ}), so the Environment serves captured values and never opens a file again
	 * — not even one whose link the kubelet has since deleted.</p>
	 */
	private Reread reread(final Tree tree) throws IOException {
		return PinnedGeneration.read(tree.path, generation -> {
			// The mount directory exactly, as ExactSecretTreePostProcessor imported it; any other tree as Boot does.
			final ConfigTreePropertySource fresh = tree.exact
					? new ConfigTreePropertySource(tree.name, tree.path)
					: new ConfigTreePropertySource(tree.name, tree.path, Option.AUTO_TRIM_TRAILING_NEW_LINE);
			final Map<String, String> values = valuesOf(fresh, afterEachValue);
			if (!values.keySet().equals(PinnedGeneration.keys(generation))) {
				throw new IOException("The visible keys in " + tree.path + " do not yet match the generation "
						+ PinnedGeneration.DATA_LINK + " names");
			}
			return new Reread(fresh, values);
		});
	}

	/** Called under {@link #lock}. */
	private void scheduleRetry(final Tree tree, final Exception failure) {
		tree.failures++;
		final Duration delay = retryDelay(tree.failures);
		if (tree.failures == 1) {
			LOG.warn("Re-reading mounted secrets in " + tree.path + " failed (" + ExceptionTypes.of(failure)
					+ "), probably mid-swap; retrying until it succeeds. The Environment keeps its current "
					+ "values until then.");
		} else if (delay.equals(MAX_RETRY_DELAY) && !tree.reportedStuck) {
			tree.reportedStuck = true;
			LOG.error("Mounted secrets in " + tree.path + " still cannot be re-read after " + tree.failures
					+ " attempts (" + ExceptionTypes.of(failure) + "); the Environment is serving the values "
					+ "from before the last change. Retrying every " + MAX_RETRY_DELAY.toSeconds() + " s.");
		}
		if (tree.retry.isPresent() && !tree.retry.get().isDone()) {
			return;
		}
		final Optional<ScheduledExecutorService> executor = retries;
		if (executor.isPresent()) {
			tree.retry = Optional.of(executor.get().schedule(() -> retry(tree), delay.toMillis(),
					TimeUnit.MILLISECONDS));
		}
	}

	/**
	 * A scheduled retry: forgets itself first, so a retry that fails again can schedule the next.
	 */
	private void retry(final Tree tree) {
		synchronized (lock) {
			tree.retry = Optional.empty();
			refresh(tree);
		}
	}

	/** 200 ms, 400 ms, 800 ms ... capped at {@link #MAX_RETRY_DELAY}. */
	static Duration retryDelay(final int failures) {
		final int doublings = Math.min(failures - 1, 20);
		final Duration delay = INITIAL_RETRY_DELAY.multipliedBy(1L << doublings);
		return delay.compareTo(MAX_RETRY_DELAY) > 0 ? MAX_RETRY_DELAY : delay;
	}

	private static Map<String, String> valuesOf(final ConfigTreePropertySource source,
			final Consumer<String> afterEachValue) {
		final Map<String, String> values = new HashMap<>();
		for (String property : source.getPropertyNames()) {
			// toString() reads the file and caches the content in the source.
			values.put(property, String.valueOf(source.getProperty(property)));
			afterEachValue.accept(property);
		}
		return Map.copyOf(values);
	}

	/**
	 * The values the imported source serves, or empty when one of them can no longer be read — which
	 * counts as unknown, so the catch-up treats any successful re-read as a change and applies it.
	 */
	private static Optional<Map<String, String>> servedBy(final ConfigTreePropertySource imported) {
		try {
			return Optional.of(valuesOf(imported, property -> { }));
		} catch (RuntimeException e) {
			return Optional.empty();
		}
	}

	/**
	 * A re-read source and the values it now caches.
	 *
	 * @param source the replacement, every value already read
	 * @param values those values, as the Environment will serve them
	 */
	private record Reread(ConfigTreePropertySource source, Map<String, String> values) {
	}

	private static String describeChange(final Map<String, String> before, final Map<String, String> after) {
		final SortedSet<String> added = new TreeSet<>(after.keySet());
		added.removeAll(before.keySet());
		final SortedSet<String> removed = new TreeSet<>(before.keySet());
		removed.removeAll(after.keySet());
		final SortedSet<String> changed = new TreeSet<>();
		for (Map.Entry<String, String> entry : after.entrySet()) {
			final String old = before.get(entry.getKey());
			if (old != null && !old.equals(entry.getValue())) {
				changed.add(entry.getKey());
			}
		}
		return "added " + added + ", removed " + removed + ", changed " + changed;
	}

	/** One followed config tree. Every mutable field is guarded by the reloader's lock. */
	private static final class Tree {

		private final String name;
		private final Path path;
		private final SecretDirectory directory;
		/** Whether this is the mount directory, served untrimmed ({@link ExactSecretTreePostProcessor}). */
		private final boolean exact;
		/** What the Environment serves for this source; empty when that could not be read. */
		private Optional<Map<String, String>> applied;
		private boolean active = true;
		private int failures;
		private boolean reportedStuck;
		private Optional<ScheduledFuture<?>> retry = Optional.empty();

		Tree(final ConfigTreePropertySource imported, final SecretDirectory directory, final boolean exact) {
			this.name = imported.getName();
			this.path = imported.getSource();
			this.directory = directory;
			this.exact = exact;
			this.applied = servedBy(imported);
		}

		void recovered() {
			if (failures > 0) {
				LOG.info("Mounted secrets in " + path + " re-read successfully after " + failures + " failed attempts");
			}
			failures = 0;
			reportedStuck = false;
			cancelRetry();
		}

		void cancelRetry() {
			retry.ifPresent(pending -> pending.cancel(false));
			retry = Optional.empty();
		}
	}
}
