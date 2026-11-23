package dev.bluestep.global.dto.myaccount;

import com.fasterxml.jackson.annotation.JsonCreator;

/**
 * Why web-global would not answer a My Account call (from 6.14.0): the body of its {@code 403}.
 *
 * <p>Forward binding, as {@code SelfTokenRefusal}'s: a reason a newer app adds binds as {@link Reason#UNKNOWN}, which
 * the gateway answers as web-global being unavailable, never as an answer.</p>
 *
 * @param reason  what stands in the way
 * @param message a sentence for the person, naming no other user and no secret
 */
public record MyAccountRefusal(Reason reason, String message) {

	/** An omitted reason or message binds to null whatever the null-marking says; read as UNKNOWN and "". */
	public MyAccountRefusal {
		reason = reason == null ? Reason.UNKNOWN : reason;
		message = message == null ? "" : message;
	}

	/** One thing that stands in the way. */
	public enum Reason {
		/** 403: no account carries the acting key, or it is turned off. Read fresh, never from a cache. */
		ACCOUNT_DISABLED,
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
