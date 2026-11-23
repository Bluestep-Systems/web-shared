package dev.bluestep.global.dto.globaluserrecord;

/**
 * What a password check against a global user's stored credential found.
 *
 * <p>Three answers, and adding a fourth is a contract change: a consumer on an older jar cannot bind a
 * value it has never heard of.</p>
 */
public enum GlobalUserAuthenticationOutcome {

	/** The account resolves in the organization and the candidate is its password. */
	MATCH,

	/**
	 * Nothing to sign in as: no account by that name resolves in the organization, the account has no
	 * credential, the candidate is not its password, or the stored one-way value is one this service
	 * refuses to spend the work on (a cost above the ceilings, or a value it cannot parse). One answer
	 * for all of them, because the sign-in that asked treats them alike.
	 */
	NO_MATCH,

	/**
	 * The account resolves in the organization but its credential is still in a reversible generation
	 * ({@code \n1} or unmarked), which this service does not read. The caller checks that row itself,
	 * as it always has, and its next successful sign-in converts it to one-way. Nothing was verified,
	 * so no record comes with this answer.
	 */
	LEGACY_CREDENTIAL
}
