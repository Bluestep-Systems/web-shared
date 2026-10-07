package dev.bluestep.secretfiles.spring;

import java.util.Objects;
import java.util.Optional;

/**
 * Thrown by {@link RotatingSecret#current()} once the secret has been withdrawn: its property went
 * blank or absent, or now holds a placeholder that no longer resolves. Removing a compromised secret
 * from the mounted Secret is how an operator revokes it, so a withdrawn secret has no value at all
 * rather than the one it last held.
 *
 * <p>Catch it where the secret is checked — an authentication filter, a client about to send a
 * credential — and deny: refuse the caller, skip the call. It is unchecked so code that does not catch
 * it still fails closed. A later non-blank value restores the secret and {@code current()} returns it
 * again.</p>
 *
 * <p>The message names the property and the setting an operator controls, never a value.</p>
 */
public final class SecretWithdrawnException extends IllegalStateException {

	private static final long serialVersionUID = 1L;

	/** The withdrawn property; never a value. */
	private final String property;

	SecretWithdrawnException(final String property, final Optional<String> setting) {
		super(Objects.requireNonNull(property, "property") + " has been withdrawn (it is blank, absent or no "
				+ "longer resolves); deny whatever it guards. "
				+ setting.map(key -> "Set " + key + " again").orElse("Restore it") + " to bring it back.");
		this.property = property;
	}

	/**
	 * The Spring property whose secret was withdrawn.
	 *
	 * @return the property name, never a value
	 */
	public String property() {
		return property;
	}
}
