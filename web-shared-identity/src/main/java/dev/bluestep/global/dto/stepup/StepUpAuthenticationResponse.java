package dev.bluestep.global.dto.stepup;

import java.util.Objects;
import java.util.Optional;

import org.jspecify.annotations.Nullable;

import dev.bluestep.global.dto.globaluserrecord.UntenantedAuthenticationOutcome;
import dev.bluestep.global.dto.globaluserrecord.UntenantedAuthenticationResponse;

/**
 * The answer of {@code POST /step-up-tickets/password} (6.15.0): the 6.4.0 tenant-less password check's own answer,
 * unchanged, and the step-up ticket the match earned. A new shape around the shipped one, so no shipped shape moved.
 *
 * <p>The ticket is present exactly when the outcome is {@code MATCH}. An outcome this reader does not know binds as
 * {@code UNKNOWN} (a denial), and any ticket beside it is dropped, as {@link UntenantedAuthenticationResponse} drops the
 * record.</p>
 *
 * @param authentication the password check's answer
 * @param stepUp         the ticket, on a match only
 */
public record StepUpAuthenticationResponse(UntenantedAuthenticationResponse authentication,
		@Nullable StepUpTicketResponse stepUp) {

	public StepUpAuthenticationResponse {
		Objects.requireNonNull(authentication, "authentication");
		UntenantedAuthenticationOutcome outcome = authentication.outcome();
		if (outcome == UntenantedAuthenticationOutcome.UNKNOWN) {
			stepUp = null;
		} else if (outcome == UntenantedAuthenticationOutcome.MATCH) {
			if (stepUp == null) {
				throw new IllegalArgumentException("a MATCH carries its step-up ticket");
			}
		} else if (stepUp != null) {
			throw new IllegalArgumentException(outcome + " carries no step-up ticket");
		}
	}

	/** {@link #stepUp}, as an {@code Optional}: present on a match only. */
	public Optional<StepUpTicketResponse> optStepUp() {
		return Optional.ofNullable(stepUp);
	}
}
