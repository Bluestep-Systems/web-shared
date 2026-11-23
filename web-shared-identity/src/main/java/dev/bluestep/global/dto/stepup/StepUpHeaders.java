package dev.bluestep.global.dto.stepup;

import java.util.regex.Pattern;

/**
 * The header a browser token mint carries its step-up ticket in (from 6.15.0), and the ticket's one spelling. A
 * constants holder, so no interface.
 */
public final class StepUpHeaders {

	/** The ticket web-global issued at the step-up ({@link StepUpTicketResponse#stepUpTicket}). */
	public static final String STEP_UP_TICKET_HEADER = "B6P-Step-Up-Ticket";

	/** 32 bytes in unpadded base64url: exactly 43 characters of the URL-safe alphabet. */
	private static final Pattern TICKET = Pattern.compile("[A-Za-z0-9_-]{43}");

	private StepUpHeaders() {
	}

	/** Whether {@code value} is spelled as a ticket is; says nothing about whether it was issued. */
	public static boolean wellFormed(String value) {
		return TICKET.matcher(value).matches();
	}
}
