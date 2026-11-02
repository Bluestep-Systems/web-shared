package dev.bluestep.global.dto.domain;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

import dev.bluestep.global.dto.constraints.CodePointSize;

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
 * <h2>Validation: the width always, the shape by contract version</h2>
 *
 * <p>The one bean-validation constraint is the column's width, {@value #MAX_LENGTH} code points, which every
 * request that cascades into this ({@code @Valid}) is held to, as the plain {@code String} was.</p>
 *
 * <p>The shape, {@link #SUFFIX_PATTERN} (blank, or a host name as {@link DomainHost#HOST_PATTERN} spells
 * one), is <em>not</em> a bean constraint, because the records carrying a suffix are bound under every
 * contract version: older monolith builds pass an administrator's text through unchecked, and the contract
 * versions they speak must keep accepting and storing it exactly as sent. The server applies
 * {@link #isValid()} itself on the versions that promise the rule (web-global: 6.0.0 and later).</p>
 *
 * <p>Building one, in code or from JSON, accepts any string, and reading a stored one answers it as
 * stored.</p>
 *
 * @param value the suffix, as stored or as written; empty for none
 */
public record DomainSuffix(
		@JsonValue
		@CodePointSize(max = DomainSuffix.MAX_LENGTH)
		String value) {

	/** The width of the reseller's {@code defaultdomain} column, {@code varchar(256)}, in code points. */
	public static final int MAX_LENGTH = 256;

	/** A well-formed suffix: empty (none), or a host name as {@link DomainHost#HOST_PATTERN} spells one. */
	public static final String SUFFIX_PATTERN = "(" + DomainHost.HOST_PATTERN + ")?";

	private static final java.util.regex.Pattern SUFFIX = java.util.regex.Pattern.compile(SUFFIX_PATTERN);

	/**
	 * The suffix {@code value} spells; Jackson binds a JSON string through this. Any string is accepted
	 * (see the class doc).
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
	 * Whether this suffix is well formed: blank, or a host name a site prefix can be put under. The rule a
	 * server applies on the contract versions that promise it (see the class doc).
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
