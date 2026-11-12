package dev.bluestep.global.dto.domain;

import java.util.Locale;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

import jakarta.validation.constraints.Pattern;

/**
 * A host name a domain of the fleet is served at (from 6.0.0): unique across the fleet, the key a
 * by-host lookup resolves, the host an ingress routes. The value of the monolith's
 * {@code domainname.value} and web-global's {@code tenant_domain.host}.
 *
 * <p>On the wire it is the bare string, exactly as a plain {@code String} component wrote it, so
 * retyping a component to this changes no byte.</p>
 *
 * <h2>Validation: requests only</h2>
 *
 * <p>A host a caller writes must match {@link #HOST_PATTERN}, the monolith's
 * {@code DomainName.isValidDomain}: lower-case labels of letters, digits and inner hyphens, at most 63
 * each and 253 in all (inside the {@code varchar(255)} column), at least one dot, and a last label that
 * is not all digits (so never an IP address). Every such host can become an ingress host with a publicly
 * issued certificate. The rule is a bean-validation constraint on {@link #value}, so it fires only where
 * a request is validated ({@code @Valid}), and a request breaking it is a {@code 400}.</p>
 *
 * <p>Building one, in code or from JSON, accepts any string: older monolith builds write the column
 * unchecked, so a stored host may break the rule (stray spaces, upper case), and reading it answers the
 * host as stored rather than failing the read.</p>
 *
 * <h2>Comparing hosts</h2>
 *
 * <p>DNS names are case-insensitive, and a stored host may carry stray padding. Two hosts name the same
 * host when their {@link #normalized()} forms are equal; {@link #sameHostAs} says so. That method is the
 * one place the rule lives: a lookup or a host-taken check compares normalized forms, never raw values.</p>
 *
 * @param value the host, as stored or as written
 */
public record DomainHost(
		@JsonValue
		@Pattern(regexp = DomainHost.HOST_PATTERN)
		String value) {

	/**
	 * A host name the fleet can serve: the monolith's {@code DomainName.isValidDomain} as one expression.
	 * The look-aheads hold the 253 total and the last label's letter; each label is at most 63.
	 */
	public static final String HOST_PATTERN = "(?=.{1,253}$)(?=.*\\.[-a-z0-9]*[a-z][-a-z0-9]*$)"
			+ "[a-z0-9]([-a-z0-9]{0,61}[a-z0-9])?(\\.[a-z0-9]([-a-z0-9]{0,61}[a-z0-9])?)+";

	private static final java.util.regex.Pattern HOST = java.util.regex.Pattern.compile(HOST_PATTERN);

	/**
	 * The host {@code value} spells; Jackson binds a JSON string through this. Any string is accepted
	 * (see the class doc); {@link #isServable()} or bean validation checks it.
	 *
	 * @param value the host, as stored or as written
	 * @return the host
	 */
	@JsonCreator(mode = JsonCreator.Mode.DELEGATING)
	public static DomainHost of(String value) {
		return new DomainHost(value);
	}

	/**
	 * Whether the fleet could serve this host as it is spelled: what the constraint on {@link #value}
	 * checks, for a caller that holds one outside a validated request.
	 *
	 * @return {@code true} when {@link #value} matches {@link #HOST_PATTERN}
	 */
	public boolean isServable() {
		return HOST.matcher(value).matches();
	}

	/**
	 * The form two hosts are compared in: surrounding whitespace stripped, lower-cased in the root locale.
	 * Every comparison of hosts goes through this (or {@link #sameHostAs}), so a stored host with stray
	 * padding or upper case still matches the host it means. A servable host is its own normalized form.
	 *
	 * @return this host, stripped and lower-cased
	 */
	public DomainHost normalized() {
		return new DomainHost(value.strip().toLowerCase(Locale.ROOT));
	}

	/**
	 * Whether this and {@code other} name the same host: their {@link #normalized()} forms are equal.
	 *
	 * @param other the host to compare with
	 * @return {@code true} when both normalize to the same value
	 */
	public boolean sameHostAs(DomainHost other) {
		return normalized().equals(other.normalized());
	}

	/** The host itself, so a log line or message prints it as stored. */
	@Override
	public String toString() {
		return value;
	}
}
