package dev.bluestep.secretfiles.spring;

import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.springframework.core.env.PropertyResolver;

/**
 * A secret whose absence is a legitimate state — a credential for a peer that is not deployed
 * everywhere, a reader that does not exist in this environment. Absent means "this feature refuses
 * everyone", never "this feature is open".
 *
 * <p>Unlike a {@link RotatingSecret}, a value that goes blank at runtime is applied, not refused: for
 * an optional secret that is a withdrawal, and a credential the operator withdrew must stop working at
 * once rather than linger until restart. It is logged as a warning naming the property.</p>
 *
 * <p>Get one from {@link RotatingSecrets#optional}, which reloads it on every
 * {@link SecretsReloadedEvent}, or from {@link RotatingSecret#optional}, which reloads only when told
 * to. Never logs a value.</p>
 */
public final class OptionalRotatingSecret {

	private static final Log LOG = LogFactory.getLog(OptionalRotatingSecret.class);

	private final PropertyResolver properties;
	private final String property;
	private final AtomicReference<Optional<String>> value;

	OptionalRotatingSecret(final PropertyResolver properties, final String property) {
		this.properties = Objects.requireNonNull(properties, "properties");
		this.property = Objects.requireNonNull(property, "property");
		this.value = new AtomicReference<>(read());
	}

	/**
	 * The value in force now, if there is one. Read it per use.
	 *
	 * @return the current value, never blank; empty when the property is absent or blank
	 */
	public Optional<String> current() {
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
	 * Re-reads the property and makes whatever it now holds current, absence included.
	 *
	 * @return {@link SecretReload#UNCHANGED}, {@link SecretReload#ROTATED} (a new or first value) or
	 *         {@link SecretReload#WITHDRAWN} (the value is gone, which has been logged as a warning)
	 * @throws IllegalArgumentException if the property's value holds a placeholder that no longer
	 *                                  resolves; the current value is kept
	 */
	public synchronized SecretReload reload() {
		final Optional<String> next = read();
		final Optional<String> previous = value.getAndSet(next);
		if (next.equals(previous)) {
			return SecretReload.UNCHANGED;
		}
		if (next.isEmpty()) {
			LOG.warn(property + " is now blank or absent, so it is withdrawn: whatever it guarded now refuses "
					+ "everyone until it is set again");
			return SecretReload.WITHDRAWN;
		}
		LOG.info(property + (previous.isEmpty() ? " is now set" : " was rotated")
				+ "; the new value is in force and any previous one is no longer accepted");
		return SecretReload.ROTATED;
	}

	private Optional<String> read() {
		return Optional.ofNullable(properties.getProperty(property)).filter(text -> !text.isBlank());
	}

	/**
	 * Names the property, never the value.
	 *
	 * @return {@code OptionalRotatingSecret[property]}
	 */
	@Override
	public String toString() {
		return "OptionalRotatingSecret[" + property + "]";
	}
}
