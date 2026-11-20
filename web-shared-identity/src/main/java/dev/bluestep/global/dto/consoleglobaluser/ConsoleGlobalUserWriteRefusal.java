package dev.bluestep.global.dto.consoleglobaluser;

import com.fasterxml.jackson.annotation.JsonCreator;

/**
 * Why web-global refused one of the Console's global-user writes (from 6.18.0): the body of the 403, 409 or 412 that
 * {@code PUT /console-global-users/{classid}/{seqnum}/disabled} and
 * {@code DELETE /console-global-users/{classid}/{seqnum}/access-tokens/{id}} answer, and from 6.20.0 also the 403,
 * 409, 412, 422 or 503 of {@code PUT /console-global-users/{classid}/{seqnum}/endorsements}. Nothing was written. A
 * plain 404 (no body) is an account no global user carries.
 *
 * <p>Forward binding, as {@code MyAccountRefusal}'s: a reason a newer app adds binds as {@link Reason#UNKNOWN}, which
 * the gateway answers as web-global being unavailable, never as a refusal it understood.</p>
 *
 * @param reason  what stands in the way
 * @param message a sentence for the person, naming no key and no secret
 */
public record ConsoleGlobalUserWriteRefusal(Reason reason, String message) {

	/** An omitted reason or message binds to null whatever the null-marking says; read as UNKNOWN and "". */
	public ConsoleGlobalUserWriteRefusal {
		reason = reason == null ? Reason.UNKNOWN : reason;
		message = message == null ? "" : message;
	}

	/** One thing that stands in the way, and the status web-global answers it with. */
	public enum Reason {
		/** 403: the acting account is not an enabled fleet super (a super with no reseller), read fresh. */
		NOT_PERMITTED,
		/** 403: the acting account named itself; a second fleet super must enable or disable it. */
		SELF_MODIFICATION,
		/**
		 * 409: turning this account off would leave no enabled fleet super; or (6.20.0) removing its ENDORSE would leave
		 * no enabled fleet super holding ENDORSE.
		 */
		LAST_HOLDER,
		/**
		 * 403: no step-up ticket, or one web-global no longer honours (unknown, expired, another account's, used up, or
		 * issued before the acting account's password changed).
		 */
		STEP_UP_REQUIRED,
		/** 412: the account has changed since the Console read it (its version or its credential counter moved). */
		VERSION_MISMATCH,
		/**
		 * 403: web-global's Console writes are switched off ({@code global.console-writes.enabled}; for the endorsement
		 * write, {@code global.endorsements.write.enabled}).
		 */
		WRITES_DISABLED,
		/**
		 * 403 (6.20.0): the acting account's own endorsements, read fresh, do not include ENDORSE. Changing another
		 * account's endorsements needs it, as in the monolith.
		 */
		ENDORSE_REQUIRED,
		/** 422 (6.20.0): an id the change asks the account to hold is not a catalog endorsement. */
		UNKNOWN_ENDORSEMENT,
		/**
		 * 422 (6.20.0): a token the change asks to keep is not one of the account's unrecognized tokens now. Tokens can
		 * be kept or removed, never added.
		 */
		KEEP_NOT_STORED,
		/**
		 * 422 (6.20.0): the endorsements would be longer than 2991 UTF-8 bytes, which no longer fits the column once
		 * enciphered.
		 */
		TOO_LONG,
		/**
		 * 503 (6.20.0): web-global cannot read or write endorsements safely now: it holds no key, the account's or the
		 * acting account's stored endorsements do not decrypt to text, or the new value did not read back as written.
		 * Nothing was written.
		 */
		ENDORSEMENTS_UNAVAILABLE,
		/** A reason this consumer's contract does not know. Never sent by the app. */
		UNKNOWN;

		/** The reason a wire value names, by exact constant name; anything else is {@link #UNKNOWN}. */
		@JsonCreator(mode = JsonCreator.Mode.DELEGATING)
		public static Reason fromWire(String name) {
			for (Reason reason : values()) {
				if (reason.name().equals(name)) {
					return reason;
				}
			}
			return UNKNOWN;
		}
	}
}
