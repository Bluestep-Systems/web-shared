package dev.bluestep.global.dto.selftoken;

import java.time.Instant;
import java.util.Optional;

import org.jspecify.annotations.Nullable;

import com.fasterxml.jackson.annotation.JsonCreator;

/**
 * Why a self-service token call was refused (6.5.0): the body of its {@code 400}, {@code 403} or {@code 409}.
 *
 * <p>Forward binding (ruling 2026-11-09 "WS review 2"): a reason a newer app adds binds as {@link Reason#UNKNOWN}
 * ({@link Reason#fromWire}, on any mapper). The gateway answers UNKNOWN as {@code 503 registry-unavailable}: still a
 * refusal, nothing written.</p>
 *
 * <p>The gateway's {@code 403 not-permitted} carries a reason of its own (coordinator wire decision 2026-11-10):
 * {@code expiry-exceeds-presenting} with {@code presentingExpiresAt} from {@link Reason#EXPIRY_EXCEEDS_PRESENTING}
 * and {@link #presentingExpiresAt}, or {@code restricted-mints-disabled} from
 * {@link Reason#RESTRICTED_MINT_DISABLED}.</p>
 *
 * @param reason              what stands in the way
 * @param scope               the scope the refusal is about, for {@code SCOPE_ESCALATION}, {@code SCOPE_MISSING},
 *                            {@code REWRITE_NOT_PERMITTED} and an {@code INVALID_REQUEST} over a scope value; absent
 *                            otherwise
 * @param presentingExpiresAt the presenting token's own expiry, present exactly for
 *                            {@link Reason#EXPIRY_EXCEEDS_PRESENTING}: the latest expiry a mint may ask for
 * @param message             a sentence for the person, naming no other user and no secret
 */
public record SelfTokenRefusal(Reason reason, @Nullable String scope, @Nullable Instant presentingExpiresAt,
		String message) {

	/** An omitted reason or message binds to null whatever the null-marking says; read as UNKNOWN and "". */
	public SelfTokenRefusal {
		reason = reason == null ? Reason.UNKNOWN : reason;
		message = message == null ? "" : message;
	}

	/** {@link #scope}, as an {@code Optional}. */
	public Optional<String> optScope() {
		return Optional.ofNullable(scope);
	}

	/** {@link #presentingExpiresAt}, as an {@code Optional}. */
	public Optional<Instant> optPresentingExpiresAt() {
		return Optional.ofNullable(presentingExpiresAt);
	}

	/** One thing that stands in the way, and the status the app answers it with. */
	public enum Reason {
		/** 403: the presenting token is unknown, expired, or its owner is disabled. The gateway answers 401. */
		PRESENTING_TOKEN_NOT_LIVE,
		/** 403: the presenting token is live but does not hold {@code tokens:self}. */
		SCOPE_MISSING,
		/** 400: a malformed alias, an expiry not in the future, no scope, or a scope outside the vocabulary. */
		INVALID_REQUEST,
		/** 403: a scope the presenting token does not hold ({@code scope} names the first). Also a rotation of a
		 *  token holding one. */
		SCOPE_ESCALATION,
		/** 409: the owner already has a token with this alias (expired ones included, until revoked). */
		ALIAS_TAKEN,
		/** 403: {@code kb:rewrite} for an owner who is not a history manager now. */
		REWRITE_NOT_PERMITTED,
		/** 403: a token lacking {@code mcp} while web-global's restricted-mint switch is off. */
		RESTRICTED_MINT_DISABLED,
		/** 409: the owner already holds the maximum number of live tokens (50). */
		TOKEN_LIMIT,
		/** 403: an expiry (or never) beyond the presenting token's own expiry (ruling 2026-11-10 "TK1 Q3");
		 *  {@code presentingExpiresAt} says what that is. */
		EXPIRY_EXCEEDS_PRESENTING,
		/**
		 * A reason this consumer's contract does not know: what a reason added after 6.5.0 binds as. Never sent by
		 * the app.
		 */
		UNKNOWN;

		/**
		 * The reason a wire value names, by exact constant name; anything else, a newer reason, a number or a quoted
		 * number included, is {@link #UNKNOWN}. A creator rather than {@code @JsonEnumDefaultValue}, so that
		 * Jackson's ordinal binding ({@code 1}, {@code "1"}) can never pick a real reason, and so the forward
		 * binding holds whatever the reader's mapper is configured with.
		 */
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
