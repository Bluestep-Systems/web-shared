package dev.bluestep.global.dto.consoleglobaluser;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

import org.hibernate.validator.messageinterpolation.ParameterMessageInterpolator;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

/** The 6.20.0 Console endorsement write shape. Every fixture is fictional. */
@DisplayName("6.20.0 Console endorsement write shape")
class ConsoleGlobalUserEndorsementsWireTest {

	private static final ObjectMapper JSON = JsonMapper.builder()
			.enable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
			.build();

	private static Validator validator() {
		try (ValidatorFactory factory = Validation.byDefaultProvider().configure()
				.messageInterpolator(new ParameterMessageInterpolator())
				.buildValidatorFactory()) {
			return factory.getValidator();
		}
	}

	@Test
	@DisplayName("a request keeping a subset has exactly the documented wire and round trips")
	void subsetWire() {
		ConsoleGlobalUserEndorsementsRequest request = new ConsoleGlobalUserEndorsementsRequest("4-2",
				List.of("ENGINEER", "TECH"), List.of("customThing"));
		String json = JSON.writeValueAsString(request);

		assertEquals("{\"expectedVersion\":\"4-2\",\"endorsements\":[\"ENGINEER\",\"TECH\"],"
				+ "\"keepUnrecognized\":[\"customThing\"]}", json);
		assertEquals(request, JSON.readValue(json, ConsoleGlobalUserEndorsementsRequest.class));
	}

	@Test
	@DisplayName("keepUnrecognized absent or null keeps every token; an empty list keeps none")
	void keepAllAndKeepNone() {
		ConsoleGlobalUserEndorsementsRequest all = new ConsoleGlobalUserEndorsementsRequest("7", List.of());
		assertEquals(Optional.empty(), all.optKeepUnrecognized());
		assertEquals(all, JSON.readValue("{\"expectedVersion\":\"7\",\"endorsements\":[]}",
				ConsoleGlobalUserEndorsementsRequest.class));
		assertEquals(all, JSON.readValue("{\"expectedVersion\":\"7\",\"endorsements\":[],\"keepUnrecognized\":null}",
				ConsoleGlobalUserEndorsementsRequest.class));
		assertEquals(all, JSON.readValue(JSON.writeValueAsString(all), ConsoleGlobalUserEndorsementsRequest.class));

		ConsoleGlobalUserEndorsementsRequest none = JSON.readValue(
				"{\"expectedVersion\":\"7\",\"endorsements\":[\"TECH\"],\"keepUnrecognized\":[]}",
				ConsoleGlobalUserEndorsementsRequest.class);
		assertEquals(Optional.of(List.of()), none.optKeepUnrecognized());
		assertEquals(none, JSON.readValue(JSON.writeValueAsString(none), ConsoleGlobalUserEndorsementsRequest.class));
	}

	@Test
	@DisplayName("an omitted version or list, or a null entry, is refused, never read as none")
	void omittedMembersAreRefused() {
		assertThrows(RuntimeException.class, () -> JSON.readValue("{\"expectedVersion\":\"4\"}",
				ConsoleGlobalUserEndorsementsRequest.class));
		assertThrows(RuntimeException.class, () -> JSON.readValue("{\"endorsements\":[]}",
				ConsoleGlobalUserEndorsementsRequest.class));
		assertThrows(RuntimeException.class, () -> JSON.readValue(
				"{\"expectedVersion\":\"4\",\"endorsements\":[null]}", ConsoleGlobalUserEndorsementsRequest.class));
		assertThrows(RuntimeException.class, () -> JSON.readValue(
				"{\"expectedVersion\":\"4\",\"endorsements\":[],\"keepUnrecognized\":[null]}",
				ConsoleGlobalUserEndorsementsRequest.class));
	}

	@Test
	@DisplayName("the lists are copied: a caller's later change does not reach the request")
	void copied() {
		List<String> ids = new ArrayList<>(List.of("TECH"));
		ConsoleGlobalUserEndorsementsRequest request = new ConsoleGlobalUserEndorsementsRequest("1", ids);
		ids.add("ENGINEER");
		assertEquals(List.of("TECH"), request.endorsements());
	}

	@Test
	@DisplayName("a blank or overlong version, or overlong lists, fail validation")
	void validated() {
		Validator validator = validator();
		assertTrue(validator.validate(new ConsoleGlobalUserEndorsementsRequest("4-2", List.of("TECH"),
				List.of("x"))).isEmpty());
		assertEquals(1, validator.validate(new ConsoleGlobalUserEndorsementsRequest(" ", List.of())).size());
		assertEquals(1, validator.validate(new ConsoleGlobalUserEndorsementsRequest(
				"1".repeat(ConsoleGlobalUserDisabledRequest.MAX_VERSION + 1), List.of())).size());
		assertEquals(1, validator.validate(new ConsoleGlobalUserEndorsementsRequest("1",
				Collections.nCopies(ConsoleGlobalUserEndorsementsRequest.MAX_ENDORSEMENTS + 1, "TECH"))).size());
		assertEquals(1, validator.validate(new ConsoleGlobalUserEndorsementsRequest("1", List.of(),
				Collections.nCopies(ConsoleGlobalUserEndorsementsRequest.MAX_KEPT + 1, "x"))).size());
	}

	@Test
	@DisplayName("the 6.20.0 reasons round trip; a 6.18.0 reader's forward binding reads any of them as UNKNOWN")
	void newReasons() {
		for (ConsoleGlobalUserWriteRefusal.Reason reason : List.of(ConsoleGlobalUserWriteRefusal.Reason.ENDORSE_REQUIRED,
				ConsoleGlobalUserWriteRefusal.Reason.UNKNOWN_ENDORSEMENT,
				ConsoleGlobalUserWriteRefusal.Reason.KEEP_NOT_STORED, ConsoleGlobalUserWriteRefusal.Reason.TOO_LONG,
				ConsoleGlobalUserWriteRefusal.Reason.ENDORSEMENTS_UNAVAILABLE)) {
			ConsoleGlobalUserWriteRefusal refusal = new ConsoleGlobalUserWriteRefusal(reason, "No.");
			assertEquals(refusal, JSON.readValue(JSON.writeValueAsString(refusal), ConsoleGlobalUserWriteRefusal.class));
		}
		assertEquals(ConsoleGlobalUserWriteRefusal.Reason.UNKNOWN,
				ConsoleGlobalUserWriteRefusal.Reason.fromWire("ENDORSE_REQUIRED_SOMEDAY"));
	}
}
