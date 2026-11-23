package dev.bluestep.global.dto.consoleglobaluser;

import java.util.Objects;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * The Console's enable or disable of one global user (from 6.18.0): the body of
 * {@code PUT /console-global-users/{classid}/{seqnum}/disabled}. The acting fleet super rides in
 * {@code B6P-Acting-Global-User} ({@code MyAccountHeaders.ACTING_GLOBAL_USER_HEADER}) and their step-up ticket in
 * {@code B6P-Step-Up-Ticket} ({@code StepUpHeaders.STEP_UP_TICKET_HEADER}), never in the body.
 *
 * <p>Only the disabled flag: this is not the whole-record write, and nothing here can reach the account's credential,
 * super flag, reseller, e-mail, username or linked sign-ins.</p>
 *
 * @param expectedVersion the {@link ConsoleGlobalUser#version} the Console read the account at, unchanged; a write
 *                        against any other version is refused ({@link ConsoleGlobalUserWriteRefusal.Reason#VERSION_MISMATCH})
 * @param disabled        true to turn the account off, false to turn it back on. Required: a body that omits it is
 *                        refused (400), never read as false
 */
public record ConsoleGlobalUserDisabledRequest(@NotBlank @Size(max = MAX_VERSION) String expectedVersion,
		boolean disabled) {

	/** The longest version tag accepted: two decimal longs and a dash, with room to spare. */
	public static final int MAX_VERSION = 64;

	/** Requires the version (an omitted one binds to null whatever the null-marking says). */
	public ConsoleGlobalUserDisabledRequest {
		Objects.requireNonNull(expectedVersion, "expectedVersion");
	}
}
