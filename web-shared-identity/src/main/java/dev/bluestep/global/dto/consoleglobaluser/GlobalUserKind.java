package dev.bluestep.global.dto.consoleglobaluser;

/**
 * What kind of account a global user is, as the Console's directory filters it (from 6.17.0): decided from two
 * facts of the record alone, whether it is a super and whether it names a reseller. The one rule, so the filter
 * web-global evaluates and the {@code resellerRestricted} flag the gateway derives cannot disagree.
 */
public enum GlobalUserKind {

	/** A super with no reseller key: sees the whole fleet. */
	FLEET_SUPER,

	/** A super whose reseller key is set (named or not): restricted to that reseller. */
	RESELLER_SUPER,

	/** Not a super. */
	USER;

	/**
	 * The kind of an account.
	 *
	 * @param superUser   whether the account is a super
	 * @param hasReseller whether the account's reseller key is set, whether or not web-global can name the reseller
	 */
	public static GlobalUserKind of(boolean superUser, boolean hasReseller) {
		if (!superUser) {
			return USER;
		}
		return hasReseller ? RESELLER_SUPER : FLEET_SUPER;
	}
}
