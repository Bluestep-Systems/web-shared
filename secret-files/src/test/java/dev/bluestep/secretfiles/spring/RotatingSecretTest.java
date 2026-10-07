package dev.bluestep.secretfiles.spring;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.file.Path;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
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
	@DisplayName("a value that turns blank or absent at runtime is refused, logged by name, and the current one kept")
	void blankAtRuntimeKeepsCurrent(final CapturedOutput output) {
		final RotatingSecret secret = RotatingSecret.required(environment, PROPERTY, "SERVICE_KEY");

		environment.setProperty(PROPERTY, "");
		assertThat(secret.reload()).isEqualTo(SecretReload.REFUSED_BLANK);
		assertThat(secret.current()).isEqualTo(FIRST);

		environment.getPropertySources().remove(MockPropertySource.MOCK_PROPERTIES_PROPERTY_SOURCE_NAME);
		assertThat(secret.reload()).as("an absent property is refused the same way")
				.isEqualTo(SecretReload.REFUSED_BLANK);
		assertThat(secret.current()).isEqualTo(FIRST);

		assertThat(output).contains("ERROR").contains(PROPERTY).contains("SERVICE_KEY").doesNotContain(FIRST);
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
	@DisplayName("one secret that fails to reload is logged by name and does not stop the others")
	void registryIsolatesAFailingSecret(final CapturedOutput output) {
		environment.setProperty("broken.key", FIRST);
		final RotatingSecrets secrets = new RotatingSecrets(environment);
		final RotatingSecret broken = secrets.required("broken.key");
		final RotatingSecret healthy = secrets.required(PROPERTY);

		// A placeholder that no longer resolves makes getProperty throw.
		environment.setProperty("broken.key", "${no.such.property}");
		environment.setProperty(PROPERTY, SECOND);
		secrets.onSecretsReloaded(RELOAD);

		assertThat(broken.current()).isEqualTo(FIRST);
		assertThat(healthy.current()).as("registered after the broken one, still reloaded").isEqualTo(SECOND);
		assertThat(output).contains("Reloading broken.key failed").doesNotContain(SECOND);
	}
}
