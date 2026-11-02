package dev.bluestep.global.dto.fleet;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * The name of a Kubernetes namespace of the fleet (from 6.0.0): the key of web-global's
 * {@code fleet_namespace}, and what a tenant's {@code namespace} names.
 *
 * <p>On the wire it is the bare string, exactly as a plain {@code String} component wrote it, so
 * retyping a component to this changes no byte.</p>
 *
 * <h2>Validation: requests only</h2>
 *
 * <p>A name a caller sends must be a Kubernetes DNS label (RFC 1123): 1 to {@value #MAX_LENGTH}
 * characters of lower-case letters, digits and {@code -}, starting and ending with a letter or digit.
 * That rule is a bean-validation constraint on {@link #value}, so it fires only where a request is
 * validated ({@code @Valid}), and a request carrying a name that breaks it is a {@code 400}.</p>
 *
 * <p>Building one, in code or from JSON, accepts any string. The {@code varchar(255)} columns behind it
 * are wider than the rule, and older monolith builds write them unchecked, so a stored name may break
 * it. Reading such a row answers the name as stored rather than failing the whole read: one bad row
 * must not take a listing or the tenant snapshot down.</p>
 *
 * @param value the name, as stored or as Kubernetes spells it
 */
public record NamespaceName(
		@JsonValue
		@Size(max = MAX_LENGTH)
		@Pattern(regexp = NamespaceName.DNS_LABEL_PATTERN)
		String value) {

	/** The longest name Kubernetes allows for a namespace: a DNS label's 63 characters. */
	public static final int MAX_LENGTH = 63;

	/** A DNS label's shape, as {@link Pattern} matches it (whole value). */
	public static final String DNS_LABEL_PATTERN = "[a-z0-9]([-a-z0-9]*[a-z0-9])?";

	private static final java.util.regex.Pattern DNS_LABEL = java.util.regex.Pattern.compile(DNS_LABEL_PATTERN);

	/**
	 * The name {@code value} spells; Jackson binds a JSON string through this. Any string is accepted
	 * (see the class doc); {@link #isKubernetesName()} or bean validation checks it.
	 *
	 * @param value the name, as stored or as Kubernetes spells it
	 * @return the name
	 */
	@JsonCreator(mode = JsonCreator.Mode.DELEGATING)
	public static NamespaceName of(String value) {
		return new NamespaceName(value);
	}

	/**
	 * Whether this is a name Kubernetes would accept for a namespace: what the constraints on
	 * {@link #value} check, for a caller that holds one outside a validated request.
	 *
	 * @return {@code true} when {@link #value} is a DNS label of at most {@value #MAX_LENGTH} characters
	 */
	public boolean isKubernetesName() {
		return value.length() <= MAX_LENGTH && DNS_LABEL.matcher(value).matches();
	}

	/** The name itself, so a log line or message prints it as Kubernetes does. */
	@Override
	public String toString() {
		return value;
	}
}
