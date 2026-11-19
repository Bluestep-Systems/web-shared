package dev.bluestep.global.dto.stepup;

import com.fasterxml.jackson.annotation.JsonCreator;

/**
 * Why web-global issued no step-up ticket for an ID token (6.15.0): the body of {@code POST /step-up-tickets/sso}'s
 * refusal.
 *
 * <p>Forward binding, as {@code SelfTokenRefusal}'s: a reason a newer app adds binds as {@link Reason#UNKNOWN}, which
 * the gateway treats as no ticket.</p>
 *
 * @param reason  what stands in the way
 * @param message a sentence for a log line, naming no subject and no token
 */
public record StepUpRefusal(Reason reason, String message) {

	/** An omitted reason or message binds to null whatever the null-marking says; read as UNKNOWN and "". */
	public StepUpRefusal {
		reason = reason == null ? Reason.UNKNOWN : reason;
		message = message == null ? "" : message;
	}

	/** One thing that stands in the way, and the status the app answers it with. */
	public enum Reason {
		/**
		 * 401: the token does not verify (signature, issuer, audience, expiry), names no {@code auth_time}, or its
		 * {@code auth_time} is more than ten minutes old; or it was already exchanged for a ticket.
		 */
		ID_TOKEN_INVALID,
		/** 403: no account carries the token's identity. */
		NO_LINKED_ACCOUNT,
		/** 403: the account it is linked to is turned off. */
		ACCOUNT_DISABLED,
		/** 503: web-global has no verification configured for this provider; nothing was checked. */
		PROVIDER_NOT_CONFIGURED,
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
