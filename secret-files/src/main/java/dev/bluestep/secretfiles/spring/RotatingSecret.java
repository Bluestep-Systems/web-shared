package dev.bluestep.secretfiles.spring;

import java.util.Objects;
import java.util.Optional;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.springframework.core.env.PropertyResolver;

/**
 * A secret that must have a value: one Spring property, read at construction and re-read on every
 * {@link #reload()}.
 *
 * <p>Get one from the auto-configured {@link RotatingSecrets}, which reloads it on every
 * {@link SecretsReloadedEvent}; the static factories here make one that reloads only when told to.</p>
 *
 * <p>Read {@link #current()} at the moment the value is used — once per request, held in a local where
 * one request uses it twice — so a rotation applies from the next use on. A changed value replaces the
 * current one at once: there is no grace window in which the previous value is still honoured.</p>
 *
 * <p>Blank fails closed both ways. At startup it throws: a service that came up with an empty
 * credential would either refuse every caller or, worse, compare against {@code ""}. At runtime the
 * secret is <b>withdrawn</b>: removing a compromised secret from the mounted Secret is how an operator
 * revokes it, so the value it held stops being honoured at once. {@link #current()} then throws
 * {@link SecretWithdrawnException} — catch it at the authentication boundary and deny — and the
 * withdrawal is logged as an error naming the property. A property whose placeholder no longer resolves
 * is withdrawn the same way. A later non-blank value restores the secret.</p>
 *
 * <p>Never logs a value; only the property name and the setting an operator controls.
 * {@link #toString()} names the property only.</p>
 */
public final class RotatingSecret {

	private static final Log LOG = LogFactory.getLog(RotatingSecret.class);

	private final PropertyResolver properties;
	private final String property;
	private final Optional<String> setting;
	/** The value in force; empty while withdrawn, or until {@link #initialize()} has run. Written under {@code this}. */
	private volatile Optional<String> value = Optional.empty();

	private RotatingSecret(final PropertyResolver properties, final String property, final Optional<String> setting) {
		this.properties = Objects.requireNonNull(properties, "properties");
		this.property = Objects.requireNonNull(property, "property");
		this.setting = setting;
	}

	/**
	 * Reads {@code property} now, refusing to start without a value.
	 *
	 * @param properties where the property is resolved, normally the application's Environment
	 * @param property   the Spring property holding the secret, such as {@code oauth2.agent.token}
	 * @return the secret, holding the property's current value
	 * @throws IllegalStateException    if the property is absent or blank; the message names the property
	 * @throws IllegalArgumentException if its value holds a placeholder that does not resolve
	 */
	public static RotatingSecret required(final PropertyResolver properties, final String property) {
		return unread(properties, property, Optional.empty()).initialize();
	}

	/**
	 * Reads {@code property} now, refusing to start without a value.
	 *
	 * @param properties where the property is resolved, normally the application's Environment
	 * @param property   the Spring property holding the secret, such as {@code oauth2.agent.token}
	 * @param setting    what an operator sets to provide it, such as {@code OAUTH2_AGENT_TOKEN} — the
	 *                   environment variable, which is also the file name in the mounted directory; named
	 *                   in the startup refusal, the withdrawal error and {@link SecretWithdrawnException}
	 * @return the secret, holding the property's current value
	 * @throws IllegalStateException    if the property is absent or blank; the message names the property
	 *                                  and {@code setting}
	 * @throws IllegalArgumentException if its value holds a placeholder that does not resolve
	 */
	public static RotatingSecret required(final PropertyResolver properties, final String property,
			final String setting) {
		return unread(properties, property, Optional.of(setting)).initialize();
	}

	/**
	 * Reads {@code property} now; absent or blank is a legitimate state rather than a refusal.
	 *
	 * @param properties where the property is resolved, normally the application's Environment
	 * @param property   the Spring property holding the secret
	 * @return the secret, empty if the property is absent or blank
	 * @throws IllegalArgumentException if its value holds a placeholder that does not resolve
	 */
	public static OptionalRotatingSecret optional(final PropertyResolver properties, final String property) {
		return new OptionalRotatingSecret(properties, property).initialize();
	}

	/**
	 * A secret that has not read its property yet, for {@link RotatingSecrets} to register before
	 * {@link #initialize()} reads it.
	 */
	static RotatingSecret unread(final PropertyResolver properties, final String property,
			final Optional<String> setting) {
		return new RotatingSecret(properties, property, Objects.requireNonNull(setting, "setting"));
	}

	/**
	 * The startup read: makes the property's value current, or refuses.
	 *
	 * @return this secret
	 * @throws IllegalStateException    if the property is absent or blank
	 * @throws IllegalArgumentException if its value holds a placeholder that does not resolve
	 */
	synchronized RotatingSecret initialize() {
		final Optional<String> initial = read(properties, property);
		if (initial.isEmpty()) {
			throw new IllegalStateException(property + " must be configured." + setting
					.map(key -> " Set " + key + ", as an environment variable or as a file in the mounted secret "
							+ "directory.")
					.orElse(""));
		}
		value = initial;
		return this;
	}

	/**
	 * The value in force now. Read it per use rather than caching it, or a rotation never reaches the
	 * caller.
	 *
	 * @return the current value; never blank
	 * @throws SecretWithdrawnException if the secret has been withdrawn: deny whatever it guards
	 */
	public String current() {
		return value.orElseThrow(() -> new SecretWithdrawnException(property, setting));
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
	 * Re-reads the property and makes what it holds current: a different non-blank value rotates the
	 * secret, and blank, absent or a placeholder that no longer resolves withdraws it.
	 *
	 * <p>Synchronized so two reloads cannot land out of order: the second to read the property is the
	 * second to store, so the newest state always wins.</p>
	 *
	 * @return what happened; {@link SecretReload#WITHDRAWN} has already been logged as an error
	 */
	public synchronized SecretReload reload() {
		Optional<String> next;
		String withdrawnBecause = "blank or absent";
		try {
			next = read(properties, property);
		} catch (IllegalArgumentException e) {
			next = Optional.empty();
			withdrawnBecause = "a placeholder that no longer resolves";
		}
		final Optional<String> previous = value;
		value = next;
		if (next.equals(previous)) {
			return SecretReload.UNCHANGED;
		}
		if (next.isEmpty()) {
			LOG.error(property + " is now " + withdrawnBecause + ", so it is withdrawn: the value it held is no "
					+ "longer in force, and whatever it guards denies everyone until "
					+ setting.map(key -> key + " is set again").orElse("it is restored") + ".");
			return SecretReload.WITHDRAWN;
		}
		LOG.info(property + (previous.isEmpty() ? " was restored" : " was rotated")
				+ "; the new value is in force and any previous one is no longer accepted");
		return SecretReload.ROTATED;
	}

	/**
	 * The property's value, empty when blank or absent.
	 *
	 * @throws IllegalArgumentException if the value holds a placeholder that does not resolve
	 */
	static Optional<String> read(final PropertyResolver properties, final String property) {
		return Optional.ofNullable(properties.getProperty(property)).filter(text -> !text.isBlank());
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
