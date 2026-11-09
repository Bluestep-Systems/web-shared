package dev.bluestep.global.dto.globaluserrecord;

import com.fasterxml.jackson.annotation.JsonEnumDefaultValue;

/**
 * What a tenant-less password check found (6.4.0). Four answers: {@link GlobalUserAuthenticationOutcome}'s three,
 * plus {@link #DISABLED}, because this check is the sign-in decision for its caller. A new enum rather than a
 * fourth constant on the 6.0.0 one, which older consumers could not bind.
 *
 * <p>{@link #UNKNOWN} is the fifth constant and never an answer the app gives: it is what an outcome added after
 * 6.4.0 binds as for this consumer ({@link UntenantedAuthenticationResponse#outcome}), and it is a refusal.</p>
 */
public enum UntenantedAuthenticationOutcome {
	/** The candidate is the password of an enabled account. */
	MATCH,
	/** Nothing to sign in as: no such name, no credential, a wrong candidate, or a refused stored value. */
	NO_MATCH,
	/** The credential is still reversible; nothing was checked. The account must be converted first. */
	LEGACY_CREDENTIAL,
	/** The candidate is the password, and the account is disabled. Only after the password verified. */
	DISABLED,
	/**
	 * An outcome this consumer's contract does not know (one a later release adds, say a second factor). Never
	 * sent by the app. <b>Denied</b>: nothing signs in on it, and it carries no record.
	 */
	@JsonEnumDefaultValue
	UNKNOWN
}
