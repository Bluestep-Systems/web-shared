package dev.bluestep.global.dto.historymanager;

import java.util.Set;

import com.fasterxml.jackson.annotation.JsonEnumDefaultValue;
import com.fasterxml.jackson.annotation.JsonFormat;

/**
 * Why a history-manager grant or revocation could not be made (6.4.0): the body of its {@code 409}.
 *
 * <p>Forward binding: a reason a newer app adds binds as {@link Reason#UNKNOWN} here rather than failing the whole
 * refusal (per property, {@code READ_UNKNOWN_ENUM_VALUES_USING_DEFAULT_VALUE}, so it holds whatever the reader's
 * mapper is configured with). The answer is still a refusal.</p>
 *
 * @param reasons every reason that applies, never empty; may hold {@link Reason#UNKNOWN} when read by a consumer
 *                older than the app
 */
public record HistoryManagerGrantRefusal(
		@JsonFormat(with = JsonFormat.Feature.READ_UNKNOWN_ENUM_VALUES_USING_DEFAULT_VALUE) Set<Reason> reasons) {

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
		GRANTEE_NOT_SUPER,
		/**
		 * A reason this consumer's contract does not know: what a reason added after 6.4.0 binds as. Never sent by
		 * the app; a reader treats it as "refused, for a reason it cannot name".
		 */
		@JsonEnumDefaultValue
		UNKNOWN
	}
}
