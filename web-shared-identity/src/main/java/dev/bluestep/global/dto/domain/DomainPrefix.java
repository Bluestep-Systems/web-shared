package dev.bluestep.global.dto.domain;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

import jakarta.validation.constraints.Pattern;

/**
 * A site's domain prefix (from 6.0.0): the {@code foo} of {@code foo.<suffix>}, the part of a host a site
 * create chooses. The value of the monolith's {@code domainname.domainprefix} and web-global's
 * {@code tenant_domain.domainprefix}, and what a {@code reserved_domain} row holds back from every site.
 *
 * <p>A prefix is never served or resolved on its own; it is matched against the reserved list, and a
 * host is built from it. On the wire it is the bare string, exactly as a plain {@code String} component
 * wrote it, so retyping a component to this changes no byte.</p>
 *
 * <h2>Validation: requests only</h2>
 *
 * <p>A prefix a caller writes must match {@link #PREFIX_PATTERN}, the monolith's {@code SiteDomainPrefix}
 * rule: 1 to {@value #MAX_LENGTH} characters of lower-case letters, digits, {@code .} and {@code -}, in
 * dot-separated labels that are never empty, never start or end with a hyphen, and are at most 63 long
 * ({@code DomainName.isValidLabels}). A prefix may hold dots ({@code a.b} under a suffix). The rule is a
 * bean-validation constraint on {@link #value}, so it fires only where a request is validated
 * ({@code @Valid}), and a request breaking it is a {@code 400}.</p>
 *
 * <p>Building one, in code or from JSON, accepts any string: older monolith builds write the columns
 * unchecked (a stored prefix may be blank), and reading one answers the prefix as stored rather than
 * failing the read.</p>
 *
 * <h2>Comparing prefixes</h2>
 *
 * <p>The reserved list is matched without regard to case, as the monolith matches it
 * ({@code upper(domainname) = upper(?)}); that comparison happens in the database, against
 * {@link #value} as written.</p>
 *
 * @param value the prefix, as stored or as written
 */
public record DomainPrefix(
		@JsonValue
		@Pattern(regexp = DomainPrefix.PREFIX_PATTERN)
		String value) {

	/** The longest prefix the monolith's New-Site form, and so its {@code SiteDomainPrefix}, accepts. */
	public static final int MAX_LENGTH = 64;

	/**
	 * A prefix a site may be created with: the monolith's {@code SiteDomainPrefix} rule as one expression.
	 * The look-ahead holds the {@value #MAX_LENGTH} total; each label is at most 63.
	 */
	public static final String PREFIX_PATTERN = "(?=.{1," + MAX_LENGTH + "}$)"
			+ "[a-z0-9]([-a-z0-9]{0,61}[a-z0-9])?(\\.[a-z0-9]([-a-z0-9]{0,61}[a-z0-9])?)*";

	private static final java.util.regex.Pattern PREFIX = java.util.regex.Pattern.compile(PREFIX_PATTERN);

	/**
	 * The prefix {@code value} spells; Jackson binds a JSON string through this. Any string is accepted
	 * (see the class doc); {@link #isValid()} or bean validation checks it.
	 *
	 * @param value the prefix, as stored or as written
	 * @return the prefix
	 */
	@JsonCreator(mode = JsonCreator.Mode.DELEGATING)
	public static DomainPrefix of(String value) {
		return new DomainPrefix(value);
	}

	/**
	 * Whether a site could be created with this prefix as it is spelled: what the constraint on
	 * {@link #value} checks, for a caller that holds one outside a validated request.
	 *
	 * @return {@code true} when {@link #value} matches {@link #PREFIX_PATTERN}
	 */
	public boolean isValid() {
		return PREFIX.matcher(value).matches();
	}

	/** The prefix itself, so a log line or message prints it as stored. */
	@Override
	public String toString() {
		return value;
	}
}
