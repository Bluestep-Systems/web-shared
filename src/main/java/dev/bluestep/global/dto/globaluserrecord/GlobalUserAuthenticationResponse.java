package dev.bluestep.global.dto.globaluserrecord;

import java.util.Objects;
import java.util.Optional;

import org.jspecify.annotations.Nullable;

/**
 * The answer to a {@link GlobalUserAuthenticationRequest}.
 *
 * <p><b>No credential, in any form.</b> Not the stored value, not a digest of it, not its metadata.
 * The record is the same credential-free {@link GlobalUserRecordResponse} every read returns.</p>
 *
 * @param outcome      what the check found
 * @param record       the account, present on {@link GlobalUserAuthenticationOutcome#MATCH} and on
 *                     nothing else: an answer that verified nothing identifies nobody
 * @param needsRewrite on a match, whether the stored credential has fallen behind and the caller, which
 *                     now holds the verified password, should store a fresh one-way value through the
 *                     credential operation as a re-encode. Today that means the breach verdict it
 *                     records was taken against an earlier breach corpus, or it records none. This
 *                     service never writes a credential itself. Always {@code false} on any other
 *                     outcome.
 */
public record GlobalUserAuthenticationResponse(
		GlobalUserAuthenticationOutcome outcome,
		@Nullable GlobalUserRecordResponse record,
		boolean needsRewrite
) {

	/**
	 * Holds the three outcomes to their shapes: a record and a rewrite only on a match, and a match never
	 * without its record. A response that breaks this is a bug at the service, refused where it is bound
	 * rather than read as something it is not.
	 */
	public GlobalUserAuthenticationResponse {
		Objects.requireNonNull(outcome, "outcome");
		if (outcome == GlobalUserAuthenticationOutcome.MATCH) {
			if (record == null) {
				throw new IllegalArgumentException("a MATCH carries the account record");
			}
		} else if (record != null || needsRewrite) {
			throw new IllegalArgumentException(outcome + " carries no record and no rewrite");
		}
	}

	/** A verified password, with the account it belongs to. */
	public static GlobalUserAuthenticationResponse match(GlobalUserRecordResponse record, boolean needsRewrite) {
		return new GlobalUserAuthenticationResponse(GlobalUserAuthenticationOutcome.MATCH, record, needsRewrite);
	}

	/** Nothing to sign in as. */
	public static GlobalUserAuthenticationResponse noMatch() {
		return new GlobalUserAuthenticationResponse(GlobalUserAuthenticationOutcome.NO_MATCH, null, false);
	}

	/** The account's credential is reversible, so the caller checks it. */
	public static GlobalUserAuthenticationResponse legacyCredential() {
		return new GlobalUserAuthenticationResponse(GlobalUserAuthenticationOutcome.LEGACY_CREDENTIAL, null, false);
	}

	/** {@link #record}, as an {@code Optional}. */
	public Optional<GlobalUserRecordResponse> optRecord() {
		return Optional.ofNullable(record);
	}
}
