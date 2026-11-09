package dev.bluestep.global.dto.globaluserrecord;

import java.util.Objects;
import java.util.Optional;

import org.jspecify.annotations.Nullable;

/**
 * The answer to an {@link UntenantedAuthenticationRequest}. No credential in any form.
 *
 * @param outcome      what the check found
 * @param record       the account, on {@link UntenantedAuthenticationOutcome#MATCH} and nothing else
 * @param needsRewrite on a match, whether the caller (holding the verified password) should store a fresh
 *                     one-way value; {@code false} otherwise. Release 1's gateway may ignore it (the monolith
 *                     re-encodes on its next sign-in).
 */
public record UntenantedAuthenticationResponse(
		UntenantedAuthenticationOutcome outcome,
		@Nullable GlobalUserRecordResponse record,
		boolean needsRewrite
) {

	public UntenantedAuthenticationResponse {
		Objects.requireNonNull(outcome, "outcome");
		if (outcome == UntenantedAuthenticationOutcome.MATCH) {
			if (record == null) {
				throw new IllegalArgumentException("a MATCH carries the account record");
			}
		} else if (record != null || needsRewrite) {
			throw new IllegalArgumentException(outcome + " carries no record and no rewrite");
		}
	}

	public static UntenantedAuthenticationResponse match(GlobalUserRecordResponse record, boolean needsRewrite) {
		return new UntenantedAuthenticationResponse(UntenantedAuthenticationOutcome.MATCH, record, needsRewrite);
	}

	public static UntenantedAuthenticationResponse noMatch() {
		return new UntenantedAuthenticationResponse(UntenantedAuthenticationOutcome.NO_MATCH, null, false);
	}

	public static UntenantedAuthenticationResponse legacyCredential() {
		return new UntenantedAuthenticationResponse(UntenantedAuthenticationOutcome.LEGACY_CREDENTIAL, null, false);
	}

	public static UntenantedAuthenticationResponse disabled() {
		return new UntenantedAuthenticationResponse(UntenantedAuthenticationOutcome.DISABLED, null, false);
	}

	public Optional<GlobalUserRecordResponse> optRecord() {
		return Optional.ofNullable(record);
	}
}
