package dev.bluestep.secretfiles.spring;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.BooleanSupplier;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.boot.env.ConfigTreePropertySource;
import org.springframework.boot.env.ConfigTreePropertySource.Option;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.event.EventListener;
import org.springframework.core.annotation.Order;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.StandardEnvironment;

import dev.bluestep.secretfiles.SecretFiles;
import dev.bluestep.secretfiles.WatchedSecretDirectory;
import dev.bluestep.secretfiles.testing.KubeletSecretVolume;

/**
 * The reloader inside a real Spring Boot application that imports a kubelet-shaped volume with
 * {@code spring.config.import: configtree:} and picks the reloader up through
 * {@code @EnableAutoConfiguration} — so the imports file, the property source Boot's import created,
 * and Spring's own event delivery are all under test, not stand-ins.
 *
 * <p>"Nothing arrives" checks wait {@link #QUIET}, several times longer than the watcher needs to react
 * to a swap, and where possible are followed by a loud change that must be the first thing heard.</p>
 */
@DisplayName("ConfigTreeSecretsReloader")
class ConfigTreeSecretsReloaderTest {

	/** Generous: the watcher reacts within a few hundred milliseconds of a swap. */
	private static final Duration TIMEOUT = Duration.ofSeconds(15);

	/** Long enough for the watcher to have reacted, had it been going to. */
	private static final Duration QUIET = Duration.ofMillis(1500);

	@TempDir
	Path tmp;

	private ConfigurableApplicationContext context;

	@AfterEach
	void close() {
		if (context != null) {
			context.close();
		}
	}

	/** The imported directory; it exists only once {@link #createMount()} has run. */
	private Path mount() {
		return tmp.resolve("secrets");
	}

	private Path createMount() throws IOException {
		return Files.createDirectories(mount());
	}

	/** Boots {@link TestApplication}, importing {@link #mount()}, with a conventional directory that does not exist. */
	private ConfigurableApplicationContext boot(final String... extraArgs) throws IOException {
		return boot(tmp.resolve("not-mounted"), extraArgs);
	}

	/** Boots {@link TestApplication}, importing {@link #mount()}, with {@code conventional} as the chart's directory. */
	private ConfigurableApplicationContext boot(final Path conventional, final String... extraArgs) throws IOException {
		final List<String> args = new ArrayList<>(List.of(
				"--spring.config.import=optional:configtree:" + mount() + "/",
				// Resolved through a placeholder, the way application.yml reads every secret.
				"--holder.api-key=${API_KEY:}",
				"--" + SecretFiles.DIRECTORY_PROPERTY + "=" + conventional,
				// No database here; HikariSecretsAutoConfiguration is tested on its own.
				"--spring.autoconfigure.exclude=org.springframework.boot.jdbc.autoconfigure.DataSourceAutoConfiguration"));
		args.addAll(List.of(extraArgs));
		context = new SpringApplicationBuilder(TestApplication.class)
				.web(WebApplicationType.NONE)
				.run(args.toArray(String[]::new));
		return context;
	}

	private BlockingQueue<SecretsReloadedEvent> events() {
		return context.getBean(Recorder.class).events;
	}

	private static SecretsReloadedEvent next(final BlockingQueue<SecretsReloadedEvent> events)
			throws InterruptedException {
		final SecretsReloadedEvent event = events.poll(TIMEOUT.toMillis(), TimeUnit.MILLISECONDS);
		assertThat(event).as("an event within " + TIMEOUT).isNotNull();
		return event;
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

	@Test
	@DisplayName("is auto-configured through the imports file, with its registry and listener factory")
	void autoConfigured() throws IOException {
		KubeletSecretVolume.create(createMount(), Map.of("API_KEY", "first"));
		boot();

		assertThat(context.getBeansOfType(ConfigTreeSecretsReloader.class)).hasSize(1);
		assertThat(context.getBeansOfType(RotatingSecrets.class)).hasSize(1);
		assertThat(context.getBeansOfType(SecretsReloadedListenerFactory.class)).hasSize(1);
		assertThat(context.getBean(ConfigTreeSecretsReloader.class).watchedTrees()).isEqualTo(1);
	}

	@Test
	@DisplayName("with no config tree imported it watches nothing and publishes nothing")
	void absentMountIsANoOp() throws Exception {
		boot();

		final ConfigTreeSecretsReloader reloader = context.getBean(ConfigTreeSecretsReloader.class);
		assertThat(reloader.isRunning()).isTrue();
		assertThat(reloader.watchedTrees()).isZero();
		assertThat(context.getEnvironment().getPropertySources().stream()
				.anyMatch(ConfigTreePropertySource.class::isInstance)).isFalse();
		assertThat(context.getEnvironment().getProperty("holder.api-key")).isEmpty();
		assertThat(events().poll(QUIET.toMillis(), TimeUnit.MILLISECONDS)).isNull();
	}

	@Test
	@DisplayName("refuses to start when the conventional directory is mounted but not imported")
	void mountedButNotImportedIsRefused() throws IOException {
		final Path mounted = Files.createDirectories(tmp.resolve("mounted"));
		KubeletSecretVolume.create(mounted, Map.of("API_KEY", "first"));

		assertThatThrownBy(() -> boot(mounted))
				.rootCause()
				.isInstanceOf(IllegalStateException.class)
				.hasMessageContaining(mounted.toString())
				.hasMessageContaining("spring.config.import: optional:configtree:" + mounted + "/");
	}

	@Test
	@DisplayName("starts when the conventional directory is the one imported")
	void mountedAndImportedStarts() throws IOException {
		KubeletSecretVolume.create(createMount(), Map.of("API_KEY", "first"));
		boot(mount());

		assertThat(context.getBean(ConfigTreeSecretsReloader.class).watchedTrees()).isEqualTo(1);
	}

	@Test
	@DisplayName("a kubelet swap replaces the value, trimmed as Boot trims it, and publishes the directory")
	void swapReplacesValueAndPublishes() throws Exception {
		final KubeletSecretVolume volume = KubeletSecretVolume.create(createMount(),
				Map.of("API_KEY", "first\n", "DROPPED", "gone-soon"));
		final ConfigurableEnvironment environment = boot().getEnvironment();
		assertThat(environment.getProperty("holder.api-key"))
				.as("read at startup through Boot's own import, trailing newline trimmed")
				.isEqualTo("first");

		volume.swap(Map.of("API_KEY", "second\n", "ADDED", "new-key"));

		assertThat(next(events()).directory()).isEqualTo(mount());
		assertThat(environment.getProperty("holder.api-key"))
				.as("listeners re-reading the Environment see the new value, trimmed as at startup")
				.isEqualTo("second");
		assertThat(environment.getProperty("ADDED")).isEqualTo("new-key");
		assertThat(environment.containsProperty("DROPPED")).as("a key the swap removed is gone").isFalse();
		assertThat(environment.getPropertySources().stream().filter(ConfigTreePropertySource.class::isInstance))
				.as("the replacement is a real config tree source, so a later start() finds it again")
				.hasSize(1);
	}

	@Test
	@DisplayName("the replacement keeps the source's place, so a higher-precedence value still wins")
	void replacementKeepsPrecedence() throws Exception {
		final KubeletSecretVolume volume = KubeletSecretVolume.create(createMount(),
				Map.of("API_KEY", "first", "PINNED", "from-file"));
		final ConfigurableEnvironment environment = boot("--PINNED=from-command-line").getEnvironment();
		assertThat(environment.getProperty("PINNED")).isEqualTo("from-command-line");

		volume.swap(Map.of("API_KEY", "second", "PINNED", "from-file-rotated"));

		next(events());
		assertThat(environment.getProperty("holder.api-key")).isEqualTo("second");
		assertThat(environment.getProperty("PINNED"))
				.as("the command line still outranks the file after the reload, as an env var does")
				.isEqualTo("from-command-line");
	}

	@Test
	@DisplayName("a swap that changes only what configtree trims publishes nothing; the next real change is heard")
	void trimOnlyChangeIsNotAChange() throws Exception {
		final KubeletSecretVolume volume = KubeletSecretVolume.create(createMount(), Map.of("API_KEY", "same\n"));
		boot();

		// The core sees different bytes and notifies; the Environment would serve the same value.
		volume.swap(Map.of("API_KEY", "same"));
		assertThat(events().poll(QUIET.toMillis(), TimeUnit.MILLISECONDS)).isNull();

		volume.swap(Map.of("API_KEY", "changed"));
		next(events());
		assertThat(events()).as("exactly one event, for the real change").isEmpty();
		assertThat(context.getEnvironment().getProperty("holder.api-key")).isEqualTo("changed");
	}

	@Test
	@DisplayName("a swap between Boot reading the tree and start() is caught up by start() itself")
	void startCatchesUpOnASwapItMissed() throws IOException {
		final KubeletSecretVolume volume = KubeletSecretVolume.create(createMount(), Map.of("API_KEY", "first\n"));
		final StandardEnvironment environment = new StandardEnvironment();
		environment.getPropertySources().addLast(new ConfigTreePropertySource("Config tree '" + mount() + "'", mount(),
				Option.AUTO_TRIM_TRAILING_NEW_LINE));
		// A bean read it during startup, which is what fixes the old value in the imported source.
		assertThat(environment.getProperty("API_KEY")).isEqualTo("first");

		volume.swap(Map.of("API_KEY", "second\n"));
		final BlockingQueue<Object> published = new LinkedBlockingQueue<>();
		final ConfigTreeSecretsReloader reloader =
				new ConfigTreeSecretsReloader(environment, published::add, tmp.resolve("not-mounted"));
		try {
			reloader.start();

			assertThat(published).as("published by start() itself, not later")
					.containsExactly(new SecretsReloadedEvent(mount()));
			assertThat(environment.getProperty("API_KEY")).isEqualTo("second");
		} finally {
			reloader.stop();
		}
	}

	@Test
	@DisplayName("a re-read that fails mid-swap is retried until it succeeds, though the core never notifies again")
	void transientFailureDoesNotLeaveItStale() throws Exception {
		final KubeletSecretVolume volume = KubeletSecretVolume.create(createMount(),
				Map.of("API_KEY", "first", "DROPPED", "gone-soon"));
		boot();

		// Stop the kubelet just after its ..data rename: DROPPED's link now dangles. The core skips it and
		// notifies (API_KEY changed); Boot lists it, fails to read it, and the re-read fails.
		final KubeletSecretVolume.PendingSwap swap = volume.beginSwap(Map.of("API_KEY", "second"));
		// Longer than the old implementations' retry windows (3 x 200 ms, 4 x 250 ms).
		assertThat(events().poll(QUIET.toMillis(), TimeUnit.MILLISECONDS)).isNull();
		assertThat(context.getEnvironment().getProperty("holder.api-key")).isEqualTo("first");

		// Finishing the swap changes nothing the core can see, so it notifies nobody: only a retry recovers.
		swap.complete();

		assertThat(next(events()).directory()).isEqualTo(mount());
		assertThat(context.getEnvironment().getProperty("holder.api-key")).isEqualTo("second");
		assertThat(context.getEnvironment().containsProperty("DROPPED")).isFalse();
	}

	@Test
	@DisplayName("after stop nothing is published: not for a swap, and not for a retry pending at stop")
	void nothingAfterStop() throws Exception {
		final KubeletSecretVolume volume = KubeletSecretVolume.create(createMount(),
				Map.of("API_KEY", "first", "DROPPED", "gone-soon"));
		boot();
		final ConfigTreeSecretsReloader reloader = context.getBean(ConfigTreeSecretsReloader.class);

		// A retry is pending: the re-read failed on DROPPED's dangling link.
		final KubeletSecretVolume.PendingSwap swap = volume.beginSwap(Map.of("API_KEY", "second"));
		assertThat(events().poll(QUIET.toMillis(), TimeUnit.MILLISECONDS)).isNull();

		reloader.stop();
		swap.complete();
		volume.swap(Map.of("API_KEY", "third"));

		assertThat(reloader.isRunning()).isFalse();
		assertThat(events().poll(ConfigTreeSecretsReloader.MAX_RETRY_DELAY.plus(QUIET).toMillis(),
				TimeUnit.MILLISECONDS)).isNull();
		assertThat(context.getEnvironment().getProperty("holder.api-key")).isEqualTo("first");
	}

	@Test
	@DisplayName("a notification already in flight when stop() ran publishes nothing")
	void inFlightNotificationAfterStopPublishesNothing() throws Exception {
		final KubeletSecretVolume volume = KubeletSecretVolume.create(createMount(), Map.of("API_KEY", "first"));
		boot();
		final ConfigTreeSecretsReloader reloader = context.getBean(ConfigTreeSecretsReloader.class);
		final List<Runnable> inFlight = reloader.refreshers();
		assertThat(inFlight).hasSize(1);

		reloader.stop();
		volume.swap(Map.of("API_KEY", "second"));
		inFlight.forEach(Runnable::run);

		assertThat(events()).isEmpty();
		assertThat(context.getEnvironment().getProperty("holder.api-key")).isEqualTo("first");
	}

	@Test
	@DisplayName("a listener that throws does not starve the listeners after it")
	void throwingListenerDoesNotStarveOthers() throws Exception {
		final KubeletSecretVolume volume = KubeletSecretVolume.create(createMount(), Map.of("API_KEY", "first"));
		boot("--holder.enabled=true");
		final Thrower thrower = context.getBean(Thrower.class);

		volume.swap(Map.of("API_KEY", "second"));

		assertThat(next(events()).directory())
				.as("the recorder, ordered after the thrower, still hears the change")
				.isEqualTo(mount());
		assertThat(thrower.calls).as("the thrower did run first").hasValue(1);
		final RotatingSecret key = context.getBean(Holder.class).key;
		await(() -> "second".equals(key.current()), "the RotatingSecrets registry, after the thrower, rotated too");
	}

	@Test
	@DisplayName("RotatingSecrets follows the volume through the application.yml placeholder")
	void rotatingSecretFollowsTheVolume() throws Exception {
		final KubeletSecretVolume volume = KubeletSecretVolume.create(createMount(), Map.of("API_KEY", "first\n"));
		boot("--holder.enabled=true");
		final RotatingSecret key = context.getBean(Holder.class).key;
		assertThat(key.current()).isEqualTo("first");

		volume.swap(Map.of("API_KEY", "second\n"));
		next(events());

		await(() -> "second".equals(key.current()), "the secret was rotated");
	}

	@Test
	@DisplayName("a swap between two values of a re-read discards it: the Environment never mixes two versions")
	void aSwapMidRereadNeverPublishesAMixedSnapshot() throws Exception {
		final KubeletSecretVolume volume = KubeletSecretVolume.create(createMount(), Map.of("A", "old", "B", "old"));
		final StandardEnvironment environment = environmentImporting(mount());
		final List<String> served = new CopyOnWriteArrayList<>();
		final AtomicInteger valuesRead = new AtomicInteger();
		// The catch-up in start() re-reads A, then the kubelet's whole update lands, then it reads B.
		final ConfigTreeSecretsReloader reloader = new ConfigTreeSecretsReloader(environment,
				event -> served.add(environment.getProperty("A") + "/" + environment.getProperty("B")),
				tmp.resolve("not-mounted"), WatchedSecretDirectory.DEFAULT_POLL_INTERVAL, property -> {
					if (valuesRead.getAndIncrement() == 0) {
						swapQuietly(volume, Map.of("A", "new", "B", "new"));
					}
				});
		try {
			reloader.start();
			await(() -> !served.isEmpty(), "the swap was published");
			Thread.sleep(QUIET.toMillis());
		} finally {
			reloader.stop();
		}

		assertThat(served).as("every published Environment holds one version").containsOnly("new/new");
		assertThat(environment.getProperty("A")).isEqualTo("new");
		assertThat(environment.getProperty("B")).isEqualTo("new");
	}

	@Test
	@DisplayName("a re-read while the kubelet has renamed ..data but not yet linked a new key is refused, then retried")
	void aRereadMissingANewKeysLinkIsNotPublished() throws Exception {
		final KubeletSecretVolume volume = KubeletSecretVolume.create(createMount(), Map.of("A", "old"));
		final StandardEnvironment environment = environmentImporting(mount());
		final List<String> served = new CopyOnWriteArrayList<>();
		final AtomicInteger valuesRead = new AtomicInteger();
		final List<KubeletSecretVolume.PendingSwap> pending = new CopyOnWriteArrayList<>();
		final ConfigTreeSecretsReloader reloader = new ConfigTreeSecretsReloader(environment,
				event -> served.add(environment.getProperty("A") + "/" + environment.getProperty("ADDED")),
				tmp.resolve("not-mounted"), WatchedSecretDirectory.DEFAULT_POLL_INTERVAL, property -> {
					if (valuesRead.getAndIncrement() == 0) {
						try {
							// Renamed, but ADDED's visible link is not there yet: Boot's listing cannot see it.
							pending.add(volume.beginSwap(Map.of("A", "new", "ADDED", "added")));
							Files.delete(mount().resolve("ADDED"));
						} catch (IOException e) {
							throw new UncheckedIOException(e);
						}
					}
				});
		try {
			reloader.start();
			Thread.sleep(QUIET.toMillis());
			assertThat(served).as("A from the new version without ADDED is half a version").isEmpty();
			assertThat(environment.getProperty("A")).isEqualTo("old");

			// The kubelet links the new key; a retry now succeeds without any further notification.
			Files.createSymbolicLink(mount().resolve("ADDED"), Path.of("..data", "ADDED"));
			pending.getFirst().complete();
			await(() -> !served.isEmpty(), "the complete version was published");
		} finally {
			reloader.stop();
		}

		assertThat(served).containsOnly("new/added");
	}

	@Test
	@DisplayName("rotating a single-dot key such as .token refreshes the Environment")
	void aSingleDotKeyRotates() throws Exception {
		final KubeletSecretVolume volume = KubeletSecretVolume.create(createMount(),
				Map.of("API_KEY", "key", ".token", "one"));
		final ConfigurableEnvironment environment = boot().getEnvironment();
		assertThat(environment.getProperty(".token")).as("Boot imports it").isEqualTo("one");

		volume.swap(Map.of("API_KEY", "key", ".token", "two"));

		assertThat(next(events()).directory()).isEqualTo(mount());
		assertThat(environment.getProperty(".token")).isEqualTo("two");
	}

	@Test
	@DisplayName("a subdirectory in an imported tree refuses startup, naming it")
	void aSubdirectoryIsRefusedAtStartup() throws IOException {
		Files.writeString(createMount().resolve("API_KEY"), "key");
		Files.createDirectory(mount().resolve("nested"));
		Files.writeString(mount().resolve("nested").resolve("inner"), "Boot reads this as nested.inner");

		assertThatThrownBy(() -> boot())
				.rootCause()
				.isInstanceOf(IllegalArgumentException.class)
				.hasMessageContaining(mount().toString())
				.hasMessageContaining("nested");
	}

	@Test
	@DisplayName("the mount directory serves each file's exact bytes, trailing newline included, at startup and after a reload")
	void theMountDirectoryKeepsATrailingNewline() throws Exception {
		final KubeletSecretVolume volume = KubeletSecretVolume.create(createMount(),
				Map.of("API_KEY", "seed\n", "OTHER", "x"));
		final ConfigurableEnvironment environment = boot(mount(), "--startup-reader.enabled=true").getEnvironment();
		assertThat(context.getBean(StartupReader.class).apiKey)
				.as("read by a bean during startup, before the reloader starts: exactly what the env var carried")
				.isEqualTo("seed\n");
		assertThat(events().poll(QUIET.toMillis(), TimeUnit.MILLISECONDS))
				.as("the import was already exact, so the reloader's catch-up had nothing to change").isNull();
		assertThat(environment.getProperty("holder.api-key")).isEqualTo("seed\n");

		volume.swap(Map.of("API_KEY", "seed\n", "OTHER", "y"));
		next(events());
		assertThat(environment.getProperty("holder.api-key")).as("a reload keeps the bytes too").isEqualTo("seed\n");

		volume.swap(Map.of("API_KEY", "seed2\n", "OTHER", "y"));
		next(events());
		assertThat(environment.getProperty("holder.api-key")).isEqualTo("seed2\n");
	}

	private static StandardEnvironment environmentImporting(final Path directory) {
		final StandardEnvironment environment = new StandardEnvironment();
		environment.getPropertySources().addLast(new ConfigTreePropertySource("Config tree '" + directory + "'",
				directory, Option.AUTO_TRIM_TRAILING_NEW_LINE));
		return environment;
	}

	private static void swapQuietly(final KubeletSecretVolume volume, final Map<String, String> next) {
		try {
			volume.swap(next);
		} catch (IOException e) {
			throw new UncheckedIOException(e);
		}
	}

	/** The smallest Boot application: auto-configuration, plus listeners to observe it. */
	@Configuration(proxyBeanMethods = false)
	@EnableAutoConfiguration
	static class TestApplication {

		@Bean
		Thrower thrower() {
			return new Thrower();
		}

		@Bean
		Recorder recorder() {
			return new Recorder();
		}

		@Bean
		@ConditionalOnProperty("startup-reader.enabled")
		StartupReader startupReader(final ConfigurableEnvironment environment) {
			return new StartupReader(environment.getProperty("holder.api-key"));
		}

		@Bean
		@ConditionalOnProperty("holder.enabled")
		Holder holder(final RotatingSecrets secrets) {
			return new Holder(secrets.required("holder.api-key", "API_KEY"));
		}
	}

	/** Throws on every change, and is ordered first. */
	static final class Thrower {

		final AtomicInteger calls = new AtomicInteger();

		@EventListener
		@Order(1)
		void on(final SecretsReloadedEvent event) {
			calls.incrementAndGet();
			throw new IllegalStateException("listener bug");
		}
	}

	/** Collects every {@link SecretsReloadedEvent}, ordered after {@link Thrower}. */
	static final class Recorder {

		final BlockingQueue<SecretsReloadedEvent> events = new LinkedBlockingQueue<>();

		@EventListener
		@Order(2)
		void on(final SecretsReloadedEvent event) {
			events.add(event);
		}
	}

	/** What a bean read from the Environment while the context was starting, before any lifecycle bean ran. */
	static final class StartupReader {

		final String apiKey;

		StartupReader(final String apiKey) {
			this.apiKey = apiKey;
		}
	}

	/** A consumer holding a secret from the registry. */
	static final class Holder {

		final RotatingSecret key;

		Holder(final RotatingSecret key) {
			this.key = key;
		}
	}
}
