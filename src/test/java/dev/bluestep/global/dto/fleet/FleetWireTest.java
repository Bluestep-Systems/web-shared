package dev.bluestep.global.dto.fleet;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.LocalDateTime;
import java.util.Set;
import java.util.stream.Collectors;

import org.hibernate.validator.messageinterpolation.ParameterMessageInterpolator;
import org.junit.jupiter.api.Test;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

/**
 * Pins 6.0.0's fleet shapes: their wire keys, absent against explicit null, and the lockout's message
 * bounds. Strict on unknown properties, so a derived getter leaking onto the wire fails the round trip.
 */
class FleetWireTest {

	private static final ObjectMapper JSON = JsonMapper.builder()
			.enable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
			.build();

	private static final Validator VALIDATOR = validator();

	private static Validator validator() {
		try (ValidatorFactory factory = Validation.byDefaultProvider().configure()
				.messageInterpolator(new ParameterMessageInterpolator())
				.buildValidatorFactory()) {
			return factory.getValidator();
		}
	}

	private static Set<String> violatedPaths(Object bean) {
		return VALIDATOR.validate(bean).stream()
				.map(ConstraintViolation::getPropertyPath)
				.map(Object::toString)
				.collect(Collectors.toUnmodifiableSet());
	}

	private static FleetLockout lockout(String mainMessage) {
		return new FleetLockout(2, false, LocalDateTime.of(2026, 10, 30, 22, 0), 90, "soon", mainMessage, null);
	}

	@Test
	void namespace_pinsItsKeysIncludingIsPrivate() {
		FleetNamespaceResponse sent =
				new FleetNamespaceResponse(NamespaceName.of("web-a"), "Web A", false, true, true, 7L);

		String json = JSON.writeValueAsString(sent);

		assertEquals("{\"name\":\"web-a\",\"label\":\"Web A\",\"legacy\":false,\"isPrivate\":true,"
				+ "\"modified\":true,\"version\":7}", json);
		assertEquals(sent, JSON.readValue(json, FleetNamespaceResponse.class));
	}

	@Test
	void namespace_anOmittedLabelAndAnExplicitNullBothBindAsAbsent() {
		String body = "\"name\":\"web-a\",\"legacy\":true,\"isPrivate\":false,\"modified\":false,\"version\":0";

		FleetNamespaceResponse omitted = JSON.readValue("{" + body + "}", FleetNamespaceResponse.class);

		assertEquals(omitted, JSON.readValue("{" + body + ",\"label\":null}", FleetNamespaceResponse.class));
		assertTrue(omitted.optLabel().isEmpty());
	}

	@Test
	void modifiedRequest_isItsOneKey() {
		assertEquals("{\"modified\":false}", JSON.writeValueAsString(new NamespaceModifiedRequest(false)));
		assertEquals(new NamespaceModifiedRequest(true), JSON.readValue("{\"modified\":true}",
				NamespaceModifiedRequest.class));
	}

	@Test
	void lockout_roundTripsAndPinsItsKeys() {
		FleetLockoutUpdateRequest sent = new FleetLockoutUpdateRequest(lockout("down for upgrade"));

		String json = JSON.writeValueAsString(sent);

		assertEquals("{\"lockout\":{\"status\":2,\"autoOff\":false,\"lockedOut\":\"2026-10-30T22:00:00\","
				+ "\"durationMinutes\":90,\"preMessage\":\"soon\",\"mainMessage\":\"down for upgrade\","
				+ "\"postMessage\":null}}", json);
		assertEquals(sent, JSON.readValue(json, FleetLockoutUpdateRequest.class));
	}

	@Test
	void lockout_anOmittedKeyAndAnExplicitNullBothBindAsAbsent() {
		FleetLockout omitted = JSON.readValue("{\"status\":0,\"autoOff\":false}", FleetLockout.class);
		FleetLockout nulled = JSON.readValue("{\"status\":0,\"autoOff\":false,\"lockedOut\":null,"
				+ "\"durationMinutes\":null,\"preMessage\":null,\"mainMessage\":null,\"postMessage\":null}",
				FleetLockout.class);

		assertEquals(omitted, nulled);
		assertTrue(omitted.optLockedOut().isEmpty());
		assertTrue(omitted.optDurationMinutes().isEmpty());
		assertTrue(omitted.optMainMessage().isEmpty());
	}

	@Test
	void lockoutUpdate_boundsTheMessagesAndRequiresTheLockout() {
		assertEquals(Set.of(), violatedPaths(new FleetLockoutUpdateRequest(lockout("m".repeat(2000)))));
		assertEquals(Set.of("lockout.mainMessage"),
				violatedPaths(new FleetLockoutUpdateRequest(lockout("m".repeat(2001)))));
		assertEquals(Set.of("lockout"), violatedPaths(JSON.readValue("{}", FleetLockoutUpdateRequest.class)));
	}

	@Test
	void reservedDomain_pinsItsKeysAndAbsence() {
		assertEquals("{\"domain\":\"admin\",\"holder\":\"Fleet\"}",
				JSON.writeValueAsString(new ReservedDomainResponse("admin", "Fleet")));
		assertTrue(JSON.readValue("{\"domain\":\"admin\"}", ReservedDomainResponse.class).optHolder().isEmpty());
	}
}
