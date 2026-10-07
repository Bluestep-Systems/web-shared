package dev.bluestep.secretfiles.spring;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Map;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.boot.env.ConfigTreePropertySource;
import org.springframework.boot.env.ConfigTreePropertySource.Option;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.event.EventListener;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.StandardEnvironment;
import org.springframework.mock.env.MockEnvironment;

import dev.bluestep.secretfiles.testing.KubeletSecretVolume;

/**
 * A failure met while handling a secret is logged by exception type, never by message or stack trace:
 * Spring Boot's binder quotes the offending property value in its exception, so logging the exception
 * would log the secret.
 */
@DisplayName("Failures are logged by type, never by message or stack trace")
@ExtendWith(OutputCaptureExtension.class)
class FailureLoggingTest {

	/** What a secret looks like when it lands in a property that wants a number. */
	private static final String SECRET = "s3cr3t-not-a-number";

	private static final Duration TIMEOUT = Duration.ofSeconds(15);

	@TempDir
	Path tmp;

	@Test
	@DisplayName("an isolated listener failing in Boot's binder is logged by its identity and exception types")
	void isolatedListenerFailureIsLoggedByTypeOnly(final CapturedOutput output) {
		final MockEnvironment environment = new MockEnvironment().withProperty("leaky.port", SECRET);
		try (AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext()) {
			context.setEnvironment(environment);
			context.registerBean(SecretsReloadedListenerFactory.class);
			context.registerBean(BindingListener.class, () -> new BindingListener(environment));
			context.refresh();

			context.publishEvent(new SecretsReloadedEvent(Path.of("/var/lib/bluestep/secrets")));
		}

		assertThat(output)
				.contains("Listener " + BindingListener.class.getName() + "#onSecretsReloaded failed")
				.contains("org.springframework.boot.context.properties.bind.BindException <- ")
				.doesNotContain(SECRET)
				.doesNotContain("\tat ");
	}

	@Test
	@DisplayName("the reloader's backstop logs a listener it could not isolate by exception types only")
	void reloaderBackstopLogsByTypeOnly(final CapturedOutput output) throws Exception {
		final Path mount = Files.createDirectories(tmp.resolve("secrets"));
		final KubeletSecretVolume volume = KubeletSecretVolume.create(mount, Map.of("API_KEY", "first"));
		final ConfigurableEnvironment environment = new StandardEnvironment();
		environment.getPropertySources().addFirst(
				new ConfigTreePropertySource("configtree", mount, Option.AUTO_TRIM_TRAILING_NEW_LINE));
		// A publisher outside the factory's reach, failing with a message that quotes the new value.
		final ConfigTreeSecretsReloader reloader = new ConfigTreeSecretsReloader(environment, event -> {
			throw new IllegalStateException("cannot use " + environment.getProperty("API_KEY"),
					new IllegalArgumentException("still " + environment.getProperty("API_KEY")));
		}, tmp.resolve("not-mounted"), Duration.ofMillis(100));
		reloader.start();
		try {
			volume.swap(Map.of("API_KEY", SECRET));
			final long deadline = System.nanoTime() + TIMEOUT.toNanos();
			while (!output.getAll().contains("listener for " + mount + " threw")) {
				assertThat(System.nanoTime() - deadline).as("the backstop logged within " + TIMEOUT).isNegative();
				Thread.sleep(10);
			}
		} finally {
			reloader.stop();
		}

		assertThat(output)
				.contains("threw (java.lang.IllegalStateException <- java.lang.IllegalArgumentException)")
				.doesNotContain(SECRET)
				.doesNotContain("\tat ");
	}

	/** Binds a property that holds a secret where a number belongs, as a careless listener might. */
	static final class BindingListener {

		private final ConfigurableEnvironment environment;

		BindingListener(final ConfigurableEnvironment environment) {
			this.environment = environment;
		}

		@EventListener
		void onSecretsReloaded(final SecretsReloadedEvent event) {
			Binder.get(environment).bind("leaky.port", Integer.class);
		}
	}
}
