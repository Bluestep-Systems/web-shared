package dev.bluestep.secretfiles.spring;

import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.springframework.core.env.PropertyResolver;

/**
 * A secret that must always have a value: one Spring property, read at construction and re-read on
 * every {@link #reload()}.
 *
 * <p>Get one from the auto-configured {@link RotatingSecrets}, which reloads it on every
 * {@link SecretsReloadedEvent}; the static factories here make one that reloads only when told to.</p>
 *
 * <p>Read {@link #current()} at the moment the value is used — once per request, held in a local where
 * one request uses it twice — so a rotation applies from the next use on. A changed value replaces the
 * current one at once: there is no grace window in which the previous value is still honoured.</p>
 *
 * <p>Blank is refused both ways, but differently. At startup it throws: a service that came up with an
 * empty credential would either refuse every caller or, worse, compare against {@code ""}. At runtime
 * it is logged as an error and the current value kept: the service is already serving with a value that
 * worked, and swapping in an empty one would lock every caller out or open the door, where keeping the
 * old one costs only the rotation until the Secret is corrected.</p>
 *
 * <p>Never logs a value; only the property name and the setting an operator controls.
 * {@link #toString()} names the property only.</p>
 */
public final class RotatingSecret {

	private static final Log LOG = LogFactory.getLog(RotatingSecret.class);

	private final PropertyResolver properties;
	private final String property;
	private final Optional<String> setting;
	private final AtomicReference<String> value;

	private RotatingSecret(final PropertyResolver properties, final String property, final Optional<String> setting,
			final String initial) {
		this.properties = properties;
		this.property = property;
		this.setting = setting;
		this.value = new AtomicReference<>(initial);
	}

	/**
	 * Reads {@code property} now, refusing to start without a value.
	 *
	 * @param properties where the property is resolved, normally the application's Environment
	 * @param property   the Spring property holding the secret, such as {@code oauth2.agent.token}
	 * @return the secret, holding the property's current value
	 * @throws IllegalStateException if the property is absent or blank; the message names the property
	 */
	public static RotatingSecret required(final PropertyResolver properties, final String property) {
		return required(properties, property, Optional.empty());
	}

	/**
	 * Reads {@code property} now, refusing to start without a value.
	 *
	 * @param properties where the property is resolved, normally the application's Environment
	 * @param property   the Spring property holding the secret, such as {@code oauth2.agent.token}
	 * @param setting    what an operator sets to provide it, such as {@code OAUTH2_AGENT_TOKEN} — the
	 *                   environment variable, which is also the file name in the mounted directory; named
	 *                   in the startup refusal and the runtime error
	 * @return the secret, holding the property's current value
	 * @throws IllegalStateException if the property is absent or blank; the message names the property
	 *                               and {@code setting}
	 */
	public static RotatingSecret required(final PropertyResolver properties, final String property,
			final String setting) {
		return required(properties, property, Optional.of(setting));
	}

	/**
	 * Reads {@code property} now; absent or blank is a legitimate state rather than a refusal.
	 *
	 * @param properties where the property is resolved, normally the application's Environment
	 * @param property   the Spring property holding the secret
	 * @return the secret, empty if the property is absent or blank
	 */
	public static OptionalRotatingSecret optional(final PropertyResolver properties, final String property) {
		return new OptionalRotatingSecret(properties, property);
	}

	private static RotatingSecret required(final PropertyResolver properties, final String property,
			final Optional<String> setting) {
		Objects.requireNonNull(properties, "properties");
		Objects.requireNonNull(property, "property");
		final String initial = properties.getProperty(property);
		if (initial == null || initial.isBlank()) {
			throw new IllegalStateException(property + " must be configured." + setting
					.map(key -> " Set " + key + ", as an environment variable or as a file in the mounted secret "
							+ "directory.")
					.orElse(""));
		}
		return new RotatingSecret(properties, property, setting, initial);
	}

	/**
	 * The value in force now. Read it per use rather than caching it, or a rotation never reaches the
	 * caller.
	 *
	 * @return the current value; never blank
	 */
	public String current() {
		return value.get();
	}

	/**
	 * The property this secret is read from.
	 *
	 * @return the property name given at construction
	 */
	public String property() {
		return property;
	}

	/**
	 * Re-reads the property and, if it holds a different non-blank value, makes that current.
	 *
	 * <p>Synchronized so two reloads cannot land out of order: the second to read the property is the
	 * second to store, so the newest value always wins.</p>
	 *
	 * @return what happened; {@link SecretReload#REFUSED_BLANK} has already been logged as an error
	 * @throws IllegalArgumentException if the property's value holds a placeholder that no longer
	 *                                  resolves; the current value is kept
	 */
	public synchronized SecretReload reload() {
		final String next = properties.getProperty(property);
		if (next == null || next.isBlank()) {
			LOG.error(property + " is now blank or absent, so the value it held stays in force. "
					+ setting.map(key -> "Set " + key + " again").orElse("Restore it")
					+ "; until then it has not been rotated.");
			return SecretReload.REFUSED_BLANK;
		}
		if (next.equals(value.getAndSet(next))) {
			return SecretReload.UNCHANGED;
		}
		LOG.info(property + " was rotated; the new value is in force and the previous one is no longer accepted");
		return SecretReload.ROTATED;
	}

	/**
	 * Names the property, never the value.
	 *
	 * @return {@code RotatingSecret[property]}
	 */
	@Override
	public String toString() {
		return "RotatingSecret[" + property + "]";
	}
}
