package dev.bluestep.global.dto.consoleglobaluser;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

import org.jspecify.annotations.Nullable;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * The Console's change to one global user's platform endorsements (from 6.20.0): the body of
 * {@code PUT /console-global-users/{classid}/{seqnum}/endorsements}. The acting fleet super rides in
 * {@code B6P-Acting-Global-User} ({@code MyAccountHeaders.ACTING_GLOBAL_USER_HEADER}) and their step-up ticket in
 * {@code B6P-Step-Up-Ticket} ({@code StepUpHeaders.STEP_UP_TICKET_HEADER}), never in the body.
 *
 * <p>Only the endorsements: this is not the whole-record write, and nothing here can reach the account's credential,
 * super flag, reseller, e-mail, username or linked sign-ins. Nor the stored ciphertext: web-global reads and writes it
 * itself.</p>
 *
 * <p><b>No free text, ever.</b> {@code endorsements} names catalog ids only ({@code PlatformEndorsement} ids, matched
 * exactly; any other is refused {@link ConsoleGlobalUserWriteRefusal.Reason#UNKNOWN_ENDORSEMENT}).
 * {@code keepUnrecognized} can only keep or drop tokens the account already holds (any other is refused
 * {@link ConsoleGlobalUserWriteRefusal.Reason#KEEP_NOT_STORED}).</p>
 *
 * @param expectedVersion  the {@link ConsoleGlobalUser#version} the Console read the account at, unchanged; a write
 *                         against any other version is refused
 *                         ({@link ConsoleGlobalUserWriteRefusal.Reason#VERSION_MISMATCH})
 * @param endorsements     the complete set of catalog ids the account is to hold afterwards: every one not listed is
 *                         removed. Order carries no meaning and a repeat is tolerated. Required (an empty list holds
 *                         none)
 * @param keepUnrecognized which of the account's unrecognized tokens ({@link ConsoleEndorsements#unrecognized()}) to
 *                         keep: absent (null, or left off the wire) keeps every one now stored; a list keeps exactly
 *                         those, each of which must be stored now (an empty one keeps none). Order carries no meaning
 *                         and a repeat is tolerated
 */
public record ConsoleGlobalUserEndorsementsRequest(
		@NotBlank @Size(max = ConsoleGlobalUserDisabledRequest.MAX_VERSION) String expectedVersion,
		@Size(max = MAX_ENDORSEMENTS) List<String> endorsements,
		@Size(max = MAX_KEPT) @Nullable List<String> keepUnrecognized) {

	/** The most ids {@link #endorsements} may list, repeats included: the catalog several times over. */
	public static final int MAX_ENDORSEMENTS = 100;

	/**
	 * The most tokens {@link #keepUnrecognized} may list, repeats included: what the stored text can hold (2991 UTF-8
	 * bytes, at least two per token with its separator) with room to spare.
	 */
	public static final int MAX_KEPT = 1500;

	/**
	 * Requires the version and the list (an omitted one binds to null whatever the null-marking says), and refuses a
	 * null entry in either list.
	 */
	public ConsoleGlobalUserEndorsementsRequest {
		Objects.requireNonNull(expectedVersion, "expectedVersion");
		Objects.requireNonNull(endorsements, "endorsements");
		endorsements = List.copyOf(endorsements);
		keepUnrecognized = keepUnrecognized == null ? null : List.copyOf(keepUnrecognized);
	}

	/** A change that keeps every unrecognized token now stored. */
	public ConsoleGlobalUserEndorsementsRequest(String expectedVersion, List<String> endorsements) {
		this(expectedVersion, endorsements, null);
	}

	/** {@link #keepUnrecognized}, as an {@code Optional}: empty keeps every unrecognized token now stored. */
	public Optional<List<String>> optKeepUnrecognized() {
		return Optional.ofNullable(keepUnrecognized);
	}
}
