package dev.bluestep.global.dto.domain;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

import jakarta.validation.constraints.Pattern;

/**
 * The domain a site's {@link DomainPrefix} is put under to build its host (from 6.0.0): the
 * {@code <suffix>} of {@code foo.<suffix>}. The value of a reseller's {@code defaultdomain}, which the
 * monolith builds its tenants' site hosts under ({@code SiteDomainPrefix.domainUnder}), falling back to the
 * platform's own default domain when the reseller names none.
 *
 * <p>A suffix is not a {@link DomainHost}: nothing routes it or looks it up by itself, and it need not be
 * served. It is spelled like one, since a host is built from it. On the wire it is the bare string,
 * exactly as a plain {@code String} component wrote it, so retyping a component to this changes no
 * byte.</p>
 *
 * <h2>Blank means none</h2>
 *
 * <p>The monolith's console stores an emptied field as an empty string, and the reseller column holds
 * both {@code NULL} and {@code ''}, each meaning "no suffix of its own; use the platform's". A blank suffix
 * is that same "none" spelled as a value. It stays a value rather than being folded into absence, so a
 * reseller read and written back keeps exactly the column it had, and the wire carries {@code ""} where it
 * always did; {@link #isNone()} says which it is.</p>
 *
 * <h2>Validation: requests only</h2>
 *
 * <p>A suffix a caller writes must match {@link #SUFFIX_PATTERN}: blank, or a host name as
 * {@link DomainHost#HOST_PATTERN} spells one (the monolith's {@code DomainName.isValidDomain}). The rule is
 * a bean-validation constraint on {@link #value}, so it fires only where a request is validated
 * ({@code @Valid}), and a request breaking it is a {@code 400}.</p>
 *
 * <p>Building one, in code or from JSON, accepts any string: older monolith builds wrote the column
 * unchecked, and reading such a row answers the suffix as stored rather than failing the read.</p>
 *
 * @param value the suffix, as stored or as written; empty for none
 */
public record DomainSuffix(
		@JsonValue
		@Pattern(regexp = DomainSuffix.SUFFIX_PATTERN)
		String value) {

	/** A suffix a caller may write: empty (none), or a host name as {@link DomainHost#HOST_PATTERN} spells one. */
	public static final String SUFFIX_PATTERN = "(" + DomainHost.HOST_PATTERN + ")?";

	private static final java.util.regex.Pattern SUFFIX = java.util.regex.Pattern.compile(SUFFIX_PATTERN);

	/**
	 * The suffix {@code value} spells; Jackson binds a JSON string through this. Any string is accepted
	 * (see the class doc); {@link #isValid()} or bean validation checks it.
	 *
	 * @param value the suffix, as stored or as written
	 * @return the suffix
	 */
	@JsonCreator(mode = JsonCreator.Mode.DELEGATING)
	public static DomainSuffix of(String value) {
		return new DomainSuffix(value);
	}

	/**
	 * Whether this is the blank suffix that means "none; use the platform's default domain".
	 *
	 * @return {@code true} when {@link #value} is empty or only whitespace
	 */
	public boolean isNone() {
		return value.isBlank();
	}

	/**
	 * Whether a caller could write this suffix as it is spelled: what the constraint on {@link #value}
	 * checks, for a caller that holds one outside a validated request.
	 *
	 * @return {@code true} when {@link #value} matches {@link #SUFFIX_PATTERN}
	 */
	public boolean isValid() {
		return SUFFIX.matcher(value).matches();
	}

	/** The suffix itself, so a log line or message prints it as stored. */
	@Override
	public String toString() {
		return value;
	}
}
