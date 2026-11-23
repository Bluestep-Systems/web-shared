package dev.bluestep.secretfiles.spring;

import java.util.Objects;
import java.util.Optional;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.springframework.core.env.PropertyResolver;

/**
 * A secret whose absence is a legitimate state — a credential for a peer that is not deployed
 * everywhere, a reader that does not exist in this environment. Absent means "this feature refuses
 * everyone", never "this feature is open".
 *
 * <p>A value that goes blank or absent at runtime, or whose placeholder stops resolving, is a
 * withdrawal and is applied at once: {@link #current()} is empty from then on, so a credential the
 * operator withdrew stops working rather than lingering until restart. It is logged as a warning naming
 * the property. This is the same fail-closed rule as {@link RotatingSecret}, whose withdrawn state
 * throws {@link SecretWithdrawnException} instead, since it has no empty value to offer.</p>
 *
 * <p>Get one from {@link RotatingSecrets#optional}, which reloads it on every
 * {@link SecretsReloadedEvent}, or from {@link RotatingSecret#optional}, which reloads only when told
 * to. Never logs a value.</p>
 */
public final class OptionalRotatingSecret {

	private static final Log LOG = LogFactory.getLog(OptionalRotatingSecret.class);

	private final PropertyResolver properties;
	private final String property;
	/** The value in force; empty when absent, or until {@link #initialize()} has run. Written under {@code this}. */
	private volatile Optional<String> value = Optional.empty();

	/** An optional secret that has not read its property yet; {@link #initialize()} reads it. */
	OptionalRotatingSecret(final PropertyResolver properties, final String property) {
		this.properties = Objects.requireNonNull(properties, "properties");
		this.property = Objects.requireNonNull(property, "property");
	}

	/**
	 * The startup read: makes whatever the property holds current, silently.
	 *
	 * @return this secret
	 * @throws IllegalArgumentException if its value holds a placeholder that does not resolve
	 */
	synchronized OptionalRotatingSecret initialize() {
		value = RotatingSecret.read(properties, property);
		return this;
	}

	/**
	 * The value in force now, if there is one. Read it per use.
	 *
	 * @return the current value, never blank; empty when the property is absent or blank, or its
	 *         placeholder no longer resolves
	 */
	public Optional<String> current() {
		return value;
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
	 * Re-reads the property and makes whatever it now holds current, absence included. A placeholder
	 * that no longer resolves counts as absence.
	 *
	 * @return {@link SecretReload#UNCHANGED}, {@link SecretReload#ROTATED} (a new or first value) or
	 *         {@link SecretReload#WITHDRAWN} (the value is gone, which has been logged as a warning)
	 */
	public synchronized SecretReload reload() {
		Optional<String> next;
		String withdrawnBecause = "blank or absent";
		try {
			next = RotatingSecret.read(properties, property);
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
			LOG.warn(property + " is now " + withdrawnBecause + ", so it is withdrawn: whatever it guarded now "
					+ "refuses everyone until it is set again");
			return SecretReload.WITHDRAWN;
		}
		LOG.info(property + (previous.isEmpty() ? " is now set" : " was rotated")
				+ "; the new value is in force and any previous one is no longer accepted");
		return SecretReload.ROTATED;
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
