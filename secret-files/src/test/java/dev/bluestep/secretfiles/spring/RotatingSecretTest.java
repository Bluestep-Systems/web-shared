package dev.bluestep.secretfiles.spring;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.file.Path;
import java.time.Duration;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import org.jspecify.annotations.Nullable;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.core.env.PropertySource;
import org.springframework.mock.env.MockEnvironment;
import org.springframework.mock.env.MockPropertySource;

/**
 * {@link RotatingSecret}, {@link OptionalRotatingSecret} and the {@link RotatingSecrets} registry over
 * an Environment the test controls. Every value used here is distinctive, so "no value in the log" is
 * checked by searching the captured output for it.
 */
@DisplayName("RotatingSecret")
@ExtendWith(OutputCaptureExtension.class)
class RotatingSecretTest {

	private static final String PROPERTY = "service.key";
	private static final String FIRST = "first-value-7f3a";
	private static final String SECOND = "second-value-91c2";
	private static final SecretsReloadedEvent RELOAD = new SecretsReloadedEvent(Path.of("/var/lib/bluestep/secrets"));
	private static final Duration TIMEOUT = Duration.ofSeconds(10);

	private final MockEnvironment environment = new MockEnvironment().withProperty(PROPERTY, FIRST);

	@Test
	@DisplayName("a blank value at startup refuses to start, naming the property and what to set")
	void blankAtStartupThrows() {
		environment.setProperty(PROPERTY, " ");

		assertThatThrownBy(() -> RotatingSecret.required(environment, PROPERTY, "SERVICE_KEY"))
				.isInstanceOf(IllegalStateException.class)
				.hasMessageContaining(PROPERTY)
				.hasMessageContaining("SERVICE_KEY");
	}

	@Test
	@DisplayName("an absent property at startup refuses to start, naming the property")
	void absentAtStartupThrows() {
		assertThatThrownBy(() -> RotatingSecret.required(new MockEnvironment(), PROPERTY))
				.isInstanceOf(IllegalStateException.class)
				.hasMessageContaining(PROPERTY);
	}

	@Test
	@DisplayName("a rotated value is in force at once, with no grace for the previous one, and is not logged")
	void rotationSwapsImmediately(final CapturedOutput output) {
		final RotatingSecret secret = RotatingSecret.required(environment, PROPERTY, "SERVICE_KEY");
		assertThat(secret.current()).isEqualTo(FIRST);

		environment.setProperty(PROPERTY, SECOND);

		assertThat(secret.reload()).isEqualTo(SecretReload.ROTATED);
		assertThat(secret.current()).isEqualTo(SECOND);
		assertThat(output).contains(PROPERTY + " was rotated").doesNotContain(FIRST).doesNotContain(SECOND);
		assertThat(secret.toString()).contains(PROPERTY).doesNotContain(SECOND);
	}

	@Test
	@DisplayName("a reload that finds the same value changes nothing")
	void sameValueIsUnchanged() {
		final RotatingSecret secret = RotatingSecret.required(environment, PROPERTY);

		assertThat(secret.reload()).isEqualTo(SecretReload.UNCHANGED);
		assertThat(secret.current()).isEqualTo(FIRST);
	}

	@Test
	@DisplayName("a value that turns blank at runtime withdraws the secret: current() throws, logged by name, until restored")
	void blankAtRuntimeWithdraws(final CapturedOutput output) {
		final RotatingSecret secret = RotatingSecret.required(environment, PROPERTY, "SERVICE_KEY");

		environment.setProperty(PROPERTY, "");
		assertThat(secret.reload()).isEqualTo(SecretReload.WITHDRAWN);
		assertThatThrownBy(secret::current)
				.as("the value it held is no longer honoured")
				.isInstanceOf(SecretWithdrawnException.class)
				.isInstanceOf(IllegalStateException.class)
				.hasMessageContaining(PROPERTY)
				.hasMessageContaining("SERVICE_KEY")
				.hasMessageNotContaining(FIRST)
				.satisfies(e -> assertThat(((SecretWithdrawnException) e).property()).isEqualTo(PROPERTY));
		assertThat(secret.reload()).as("still withdrawn, nothing new").isEqualTo(SecretReload.UNCHANGED);

		environment.setProperty(PROPERTY, SECOND);
		assertThat(secret.reload()).isEqualTo(SecretReload.ROTATED);
		assertThat(secret.current()).as("a later non-blank value restores it").isEqualTo(SECOND);

		assertThat(output).contains("ERROR").contains(PROPERTY + " is now blank or absent, so it is withdrawn")
				.contains("SERVICE_KEY").contains(PROPERTY + " was restored").doesNotContain(FIRST).doesNotContain(SECOND);
	}

	@Test
	@DisplayName("a property that disappears at runtime withdraws the secret the same way")
	void absentAtRuntimeWithdraws() {
		final RotatingSecret secret = RotatingSecret.required(environment, PROPERTY);
		final PropertySource<?> mock =
				environment.getPropertySources().remove(MockPropertySource.MOCK_PROPERTIES_PROPERTY_SOURCE_NAME);

		assertThat(secret.reload()).isEqualTo(SecretReload.WITHDRAWN);
		assertThatThrownBy(secret::current).isInstanceOf(SecretWithdrawnException.class)
				.hasMessageContaining(PROPERTY).hasMessageNotContaining(FIRST);

		environment.getPropertySources().addFirst(mock);
		assertThat(secret.reload()).isEqualTo(SecretReload.ROTATED);
		assertThat(secret.current()).isEqualTo(FIRST);
	}

	@Test
	@DisplayName("a placeholder that stops resolving at runtime withdraws the secret, rather than keeping the old value")
	void unresolvablePlaceholderAtRuntimeWithdraws(final CapturedOutput output) {
		final RotatingSecret secret = RotatingSecret.required(environment, PROPERTY, "SERVICE_KEY");

		environment.setProperty(PROPERTY, "${no.such.property}");

		assertThat(secret.reload()).isEqualTo(SecretReload.WITHDRAWN);
		assertThatThrownBy(secret::current).isInstanceOf(SecretWithdrawnException.class);
		assertThat(output).contains("ERROR").contains(PROPERTY + " is now a placeholder that no longer resolves")
				.doesNotContain(FIRST);
	}

	@Test
	@DisplayName("an optional secret whose placeholder stops resolving is withdrawn to empty")
	void optionalUnresolvablePlaceholderWithdraws(final CapturedOutput output) {
		final OptionalRotatingSecret secret = RotatingSecret.optional(environment, PROPERTY);
		assertThat(secret.current()).contains(FIRST);

		environment.setProperty(PROPERTY, "${no.such.property}");

		assertThat(secret.reload()).isEqualTo(SecretReload.WITHDRAWN);
		assertThat(secret.current()).isEmpty();
		assertThat(output).contains(PROPERTY + " is now a placeholder that no longer resolves, so it is withdrawn")
				.doesNotContain(FIRST);
	}

	@Test
	@DisplayName("an optional secret may be absent at startup, appears when set, and is withdrawn when cleared")
	void optionalFollowsAbsence(final CapturedOutput output) {
		final MockEnvironment empty = new MockEnvironment();
		final OptionalRotatingSecret secret = RotatingSecret.optional(empty, PROPERTY);
		assertThat(secret.current()).isEmpty();

		empty.setProperty(PROPERTY, FIRST);
		assertThat(secret.reload()).isEqualTo(SecretReload.ROTATED);
		assertThat(secret.current()).contains(FIRST);

		empty.setProperty(PROPERTY, "  ");
		assertThat(secret.reload())
				.as("absence is legitimate for an optional secret, so a withdrawal is applied, not refused")
				.isEqualTo(SecretReload.WITHDRAWN);
		assertThat(secret.current()).isEmpty();
		assertThat(secret.reload()).isEqualTo(SecretReload.UNCHANGED);

		assertThat(output).contains(PROPERTY + " is now blank or absent, so it is withdrawn").doesNotContain(FIRST);
	}

	@Test
	@DisplayName("the registry reloads every secret it handed out on SecretsReloadedEvent")
	void registryReloadsOnEvent() {
		environment.setProperty("other.key", FIRST);
		final RotatingSecrets secrets = new RotatingSecrets(environment);
		final RotatingSecret required = secrets.required(PROPERTY, "SERVICE_KEY");
		final OptionalRotatingSecret optional = secrets.optional("other.key");

		environment.setProperty(PROPERTY, SECOND);
		environment.setProperty("other.key", SECOND);
		secrets.onSecretsReloaded(RELOAD);

		assertThat(required.current()).isEqualTo(SECOND);
		assertThat(optional.current()).contains(SECOND);
	}

	@Test
	@DisplayName("one secret whose reload throws is logged by name and does not stop the others")
	void registryIsolatesAFailingSecret(final CapturedOutput output) {
		environment.setProperty("broken.key", FIRST);
		final AtomicBoolean broken = new AtomicBoolean();
		// A resolver that fails outright for one property — not a placeholder, which withdraws instead.
		final MockEnvironment failing = new MockEnvironment() {
			@Override
			public @Nullable String getProperty(final String key) {
				if (broken.get() && "broken.key".equals(key)) {
					throw new IllegalStateException("resolver quoting " + SECOND);
				}
				return environment.getProperty(key);
			}
		};
		final RotatingSecrets secrets = new RotatingSecrets(failing);
		final RotatingSecret brokenSecret = secrets.required("broken.key");
		final RotatingSecret healthy = secrets.required(PROPERTY);

		broken.set(true);
		environment.setProperty(PROPERTY, SECOND);
		secrets.onSecretsReloaded(RELOAD);

		assertThat(brokenSecret.current()).as("a failed reload changes nothing").isEqualTo(FIRST);
		assertThat(healthy.current()).as("registered after the broken one, still reloaded").isEqualTo(SECOND);
		assertThat(output).contains("Reloading broken.key failed (java.lang.IllegalStateException)")
				.doesNotContain(SECOND);
	}

	@Test
	@DisplayName("the registry registers a required secret before its first read, so a reload landing in between is not missed")
	void requiredIsRegisteredBeforeItsFirstRead() throws Exception {
		final ReloadDuringFirstRead resolver = new ReloadDuringFirstRead();
		final RotatingSecrets secrets = new RotatingSecrets(resolver);
		resolver.secrets = secrets;

		final RotatingSecret secret = secrets.required(PROPERTY, "SERVICE_KEY");
		resolver.reloader.join(TIMEOUT.toMillis());

		assertThat(resolver.reloader.isAlive()).isFalse();
		assertThat(secret.current()).as("the reload that ran during the first read reached it").isEqualTo(SECOND);
	}

	@Test
	@DisplayName("the registry registers an optional secret before its first read, so a reload landing in between is not missed")
	void optionalIsRegisteredBeforeItsFirstRead() throws Exception {
		final ReloadDuringFirstRead resolver = new ReloadDuringFirstRead();
		final RotatingSecrets secrets = new RotatingSecrets(resolver);
		resolver.secrets = secrets;

		final OptionalRotatingSecret secret = secrets.optional(PROPERTY);
		resolver.reloader.join(TIMEOUT.toMillis());

		assertThat(resolver.reloader.isAlive()).isFalse();
		assertThat(secret.current()).as("the reload that ran during the first read reached it").contains(SECOND);
	}

	@Test
	@DisplayName("a secret the registry refuses at startup is not kept registered")
	void refusedSecretIsNotRegistered() {
		final AtomicInteger reads = new AtomicInteger();
		final MockEnvironment counting = new MockEnvironment() {
			@Override
			public @Nullable String getProperty(final String key) {
				reads.incrementAndGet();
				return null;
			}
		};
		final RotatingSecrets secrets = new RotatingSecrets(counting);
		assertThatThrownBy(() -> secrets.required(PROPERTY)).isInstanceOf(IllegalStateException.class);

		final int before = reads.get();
		secrets.onSecretsReloaded(RELOAD);
		assertThat(reads.get()).as("nothing left to reload").isEqualTo(before);
	}

	/**
	 * Serves {@link #FIRST} for the first read, and during that read starts a reload on another thread
	 * after the Environment has moved on to {@link #SECOND} — the event a swap publishes. It waits until
	 * that reload has either finished (it found nothing registered) or is blocked on the secret being
	 * created (it found it), so the test is deterministic either way.
	 */
	private static final class ReloadDuringFirstRead extends MockEnvironment {

		RotatingSecrets secrets;
		Thread reloader;
		private boolean first = true;

		@Override
		public @Nullable String getProperty(final String key) {
			if (!first) {
				return SECOND;
			}
			first = false;
			reloader = new Thread(() -> secrets.onSecretsReloaded(RELOAD), "reload-during-first-read");
			reloader.start();
			final long deadline = System.nanoTime() + TIMEOUT.toNanos();
			while (reloader.isAlive() && reloader.getState() != Thread.State.BLOCKED) {
				if (System.nanoTime() - deadline > 0) {
					throw new AssertionError("the reload neither finished nor blocked");
				}
				Thread.onSpinWait();
			}
			return FIRST;
		}
	}
}
