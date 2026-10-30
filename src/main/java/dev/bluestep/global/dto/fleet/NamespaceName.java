package dev.bluestep.global.dto.fleet;

import java.util.regex.Pattern;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

/**
 * The name of a Kubernetes namespace of the fleet (from 6.0.0): the key of web-global's
 * {@code fleet_namespace}, and what a tenant's {@code namespace} names.
 *
 * <p>On the wire it is the bare string, exactly as a plain {@code String} component wrote it, so
 * retyping a component to this changes no byte.</p>
 *
 * <h2>Validation</h2>
 *
 * <p>The name must be a Kubernetes DNS label (RFC 1123): 1 to {@value #MAX_LENGTH} characters of
 * lower-case letters, digits and {@code -}, starting and ending with a letter or digit. That is
 * narrower than the {@code varchar(255)} columns that store it, and every name production holds
 * satisfies it. A name that does not is refused where it is built, so a request carrying one fails to
 * bind (a {@code 400}) rather than reaching a table.</p>
 *
 * @param value the name, as Kubernetes spells it
 */
public record NamespaceName(@JsonValue String value) {

	/** The longest name Kubernetes allows for a namespace: a DNS label's 63 characters. */
	public static final int MAX_LENGTH = 63;

	private static final Pattern DNS_LABEL = Pattern.compile("[a-z0-9]([-a-z0-9]*[a-z0-9])?");

	/**
	 * Refuses a name that is not a DNS label.
	 *
	 * @throws IllegalArgumentException when {@code value} is longer than {@value #MAX_LENGTH} characters
	 *                                  or is not a DNS label
	 */
	public NamespaceName {
		if (value.length() > MAX_LENGTH || !DNS_LABEL.matcher(value).matches()) {
			throw new IllegalArgumentException("not a Kubernetes namespace name: \"" + value + "\"");
		}
	}

	/**
	 * The name {@code value} spells; Jackson binds a JSON string through this.
	 *
	 * @param value the name, as Kubernetes spells it
	 * @return the name
	 * @throws IllegalArgumentException when {@code value} is not a namespace name
	 */
	@JsonCreator(mode = JsonCreator.Mode.DELEGATING)
	public static NamespaceName of(String value) {
		return new NamespaceName(value);
	}

	/** The name itself, so a log line or message prints it as Kubernetes does. */
	@Override
	public String toString() {
		return value;
	}
}
