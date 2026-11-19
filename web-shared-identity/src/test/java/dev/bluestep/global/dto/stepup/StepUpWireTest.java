package dev.bluestep.global.dto.stepup;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import org.hibernate.validator.messageinterpolation.ParameterMessageInterpolator;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import dev.bluestep.global.dto.globaluserrecord.GlobalUserRecordResponse;
import dev.bluestep.global.dto.globaluserrecord.UntenantedAuthenticationOutcome;
import dev.bluestep.global.dto.globaluserrecord.UntenantedAuthenticationResponse;
import dev.bluestep.global.dto.selftoken.SelfTokenRefusal;
import dev.bluestep.global.dto.tenantaccess.GlobalUserKey;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

/** The 6.15.0 step-up shapes. Every fixture is fictional; no ticket here was ever issued. */
@DisplayName("6.15.0 step-up shapes")
class StepUpWireTest {

	private static final ObjectMapper JSON = JsonMapper.builder()
			.enable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
			.build();

	/** 43 base64url characters, the shape of a ticket; made up. */
	private static final String TICKET = "AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA-_0";
	private static final Instant EXPIRES = Instant.parse("2026-11-19T12:10:00Z");

	private static GlobalUserRecordResponse account() {
		return new GlobalUserRecordResponse(new GlobalUserKey(222_222L, -7L), "Ada", "Example", "ada", null, null,
				true, false, null, null, false, null, List.of(), 3L);
	}

	private static Validator validator() {
		try (ValidatorFactory factory = Validation.byDefaultProvider().configure()
				.messageInterpolator(new ParameterMessageInterpolator())
				.buildValidatorFactory()) {
			return factory.getValidator();
		}
	}

	@Test
	@DisplayName("a ticket has exactly the documented wire, round trips, and never prints")
	void ticketWire() {
		StepUpTicketResponse ticket = new StepUpTicketResponse(TICKET, EXPIRES);

		String json = JSON.writeValueAsString(ticket);

		assertEquals("{\"stepUpTicket\":\"" + TICKET + "\",\"expiresAt\":\"2026-11-19T12:10:00Z\"}", json);
		assertEquals(ticket, JSON.readValue(json, StepUpTicketResponse.class));
		assertFalse(ticket.toString().contains(TICKET), ticket.toString());
	}

	@Test
	@DisplayName("a password step-up carries the 6.4.0 answer unchanged, and a ticket exactly on a match")
	void passwordAnswer() {
		StepUpAuthenticationResponse match = new StepUpAuthenticationResponse(
				UntenantedAuthenticationResponse.match(account(), false), new StepUpTicketResponse(TICKET, EXPIRES));
		String json = JSON.writeValueAsString(match);

		assertEquals(match, JSON.readValue(json, StepUpAuthenticationResponse.class));
		assertTrue(json.startsWith("{\"authentication\":{\"outcome\":\"MATCH\""), json);
		assertEquals(Optional.of(new StepUpTicketResponse(TICKET, EXPIRES)), match.optStepUp());

		for (UntenantedAuthenticationResponse refused : List.of(UntenantedAuthenticationResponse.noMatch(),
				UntenantedAuthenticationResponse.disabled(), UntenantedAuthenticationResponse.legacyCredential())) {
			StepUpAuthenticationResponse answer = new StepUpAuthenticationResponse(refused, null);
			assertEquals(answer, JSON.readValue(JSON.writeValueAsString(answer), StepUpAuthenticationResponse.class));
			assertThrows(IllegalArgumentException.class,
					() -> new StepUpAuthenticationResponse(refused, new StepUpTicketResponse(TICKET, EXPIRES)),
					refused.outcome().name());
		}
		assertThrows(IllegalArgumentException.class, () -> new StepUpAuthenticationResponse(
				UntenantedAuthenticationResponse.match(account(), false), null));
	}

	@Test
	@DisplayName("an outcome this reader does not know is a denial that keeps no ticket")
	void unknownOutcomeDropsTheTicket() {
		String json = "{\"authentication\":{\"outcome\":\"SECOND_FACTOR\",\"record\":null,\"needsRewrite\":false},"
				+ "\"stepUp\":{\"stepUpTicket\":\"" + TICKET + "\",\"expiresAt\":\"2026-11-19T12:10:00Z\"}}";

		StepUpAuthenticationResponse read = JSON.readValue(json, StepUpAuthenticationResponse.class);

		assertEquals(UntenantedAuthenticationOutcome.UNKNOWN, read.authentication().outcome());
		assertNull(read.stepUp());
	}

	@Test
	@DisplayName("an SSO request validates its bounds and never prints the token")
	void ssoRequest() {
		Validator validator = validator();
		SsoStepUpRequest ok = new SsoStepUpRequest("microsoft", "header.payload.signature");

		assertTrue(validator.validate(ok).isEmpty());
		assertFalse(ok.toString().contains("payload"), ok.toString());
		assertEquals(ok, JSON.readValue(JSON.writeValueAsString(ok), SsoStepUpRequest.class));
		assertFalse(validator.validate(new SsoStepUpRequest("microsoft", "")).isEmpty());
		assertFalse(validator.validate(new SsoStepUpRequest("", "x")).isEmpty());
		assertFalse(validator.validate(new SsoStepUpRequest("microsoft",
				"x".repeat(SsoStepUpRequest.MAX_ID_TOKEN_LENGTH + 1))).isEmpty());
	}

	@Test
	@DisplayName("refusal reasons bind forward, and the new mint reasons are named exactly")
	void refusals() {
		assertEquals(StepUpRefusal.Reason.ID_TOKEN_INVALID,
				JSON.readValue("{\"reason\":\"ID_TOKEN_INVALID\",\"message\":\"m\"}", StepUpRefusal.class).reason());
		assertEquals(StepUpRefusal.Reason.UNKNOWN,
				JSON.readValue("{\"reason\":\"LATER\",\"message\":\"m\"}", StepUpRefusal.class).reason());
		assertEquals(StepUpRefusal.Reason.UNKNOWN,
				JSON.readValue("{\"reason\":\"1\",\"message\":\"m\"}", StepUpRefusal.class).reason());
		assertEquals(SelfTokenRefusal.Reason.STEP_UP_REQUIRED, SelfTokenRefusal.Reason.fromWire("STEP_UP_REQUIRED"));
		assertEquals(SelfTokenRefusal.Reason.EXPIRY_TOO_FAR, SelfTokenRefusal.Reason.fromWire("EXPIRY_TOO_FAR"));
	}

	@Test
	@DisplayName("a ticket header is exactly 43 base64url characters")
	void ticketSpelling() {
		assertTrue(StepUpHeaders.wellFormed(TICKET));
		for (String bad : List.of("", TICKET + "A", TICKET.substring(1), TICKET.replace('A', '+'),
				TICKET.replace('A', '='), " " + TICKET.substring(1), TICKET.substring(1) + "\n")) {
			assertFalse(StepUpHeaders.wellFormed(bad), bad);
		}
	}
}
