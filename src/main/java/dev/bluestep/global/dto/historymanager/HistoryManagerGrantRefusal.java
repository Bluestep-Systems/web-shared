package dev.bluestep.global.dto.historymanager;

import java.util.Set;

/**
 * Why a history-manager grant or revocation could not be made (6.4.0): the body of its {@code 409}.
 *
 * @param reasons every reason that applies, never empty
 */
public record HistoryManagerGrantRefusal(Set<Reason> reasons) {

	/** Copies, so a refusal cannot change once built. */
	public HistoryManagerGrantRefusal {
		reasons = Set.copyOf(reasons);
		if (reasons.isEmpty()) {
			throw new IllegalArgumentException("a refusal gives at least one reason");
		}
	}

	/** One thing that stands in the way. */
	public enum Reason {
		/** The actor is not an enabled super: no such account, disabled, or not a super. */
		ACTOR_NOT_SUPER,
		/** The actor is the grantee: the endorsement is given by another super. */
		SELF_GRANT,
		/** No account carries the grantee's key. */
		GRANTEE_ABSENT,
		/** The grantee is not an enabled super. */
		GRANTEE_NOT_SUPER
	}
}
