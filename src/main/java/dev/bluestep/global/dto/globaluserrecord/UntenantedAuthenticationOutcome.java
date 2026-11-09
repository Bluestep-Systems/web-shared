package dev.bluestep.global.dto.globaluserrecord;

/**
 * What a tenant-less password check found (6.4.0). Four answers: {@link GlobalUserAuthenticationOutcome}'s three,
 * plus {@link #DISABLED}, because this check is the sign-in decision for its caller. A new enum rather than a
 * fourth constant on the 6.0.0 one, which older consumers could not bind.
 */
public enum UntenantedAuthenticationOutcome {
	/** The candidate is the password of an enabled account. */
	MATCH,
	/** Nothing to sign in as: no such name, no credential, a wrong candidate, or a refused stored value. */
	NO_MATCH,
	/** The credential is still reversible; nothing was checked. The account must be converted first. */
	LEGACY_CREDENTIAL,
	/** The candidate is the password, and the account is disabled. Only after the password verified. */
	DISABLED
}
