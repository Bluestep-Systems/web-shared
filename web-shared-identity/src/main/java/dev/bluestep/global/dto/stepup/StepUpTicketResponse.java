package dev.bluestep.global.dto.stepup;

import java.time.Instant;
import java.util.Objects;

/**
 * A step-up ticket just issued (6.15.0). {@link #stepUpTicket} is the raw ticket, and this answer is the only time it
 * is ever returned; {@link #toString()} leaves it out.
 *
 * @param stepUpTicket the ticket, 43 base64url characters ({@link StepUpHeaders#wellFormed})
 * @param expiresAt    when web-global stops honouring it: ten minutes after the factor was proven
 */
public record StepUpTicketResponse(String stepUpTicket, Instant expiresAt) {

	public StepUpTicketResponse {
		Objects.requireNonNull(stepUpTicket, "stepUpTicket");
		Objects.requireNonNull(expiresAt, "expiresAt");
	}

	/** Never the ticket. */
	@Override
	public String toString() {
		return "StepUpTicketResponse[stepUpTicket=<redacted>, expiresAt=" + expiresAt + "]";
	}
}
