package dev.bluestep.global.dto.ai;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import org.hibernate.validator.messageinterpolation.ParameterMessageInterpolator;
import org.junit.jupiter.api.Test;

import dev.bluestep.global.dto.aiunitrate.AiUnitRateRequest;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;

import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.dataformat.cbor.CBORMapper;

/** Reserved {@code !} marker keys: accepted in a completion report, never where a key selects a rate row. */
class AiCompletionMarkerKeyTest {

	private static final ObjectMapper JSON = JsonMapper.builder().build();
	private static final ObjectMapper CBOR = CBORMapper.builder().build();
	private static final Validator VALIDATOR = validator();

	private static Validator validator() {
		try (ValidatorFactory factory = Validation.byDefaultProvider().configure()
				.messageInterpolator(new ParameterMessageInterpolator())
				.buildValidatorFactory()) {
			return factory.getValidator();
		}
	}

	private static AiCompletionRequest request(Map<String, Long> u, List<Map<String, Long>> r) {
		return AiCompletionRequest.of("trk", 10, 5, 100L, 2, Optional.empty(), Optional.empty(), u, r);
	}

	private static Map<String, Long> withKey(String key) {
		return Map.of(UnitKeys.TEXT_INPUT, 1L, key, 1L);
	}

	@Test
	void marker_passesInUnitAmounts() {
		AiCompletionRequest request = request(withKey(UnitKeys.INVALID_BREAKDOWN_MARKER), List.of());
		assertTrue(VALIDATOR.validate(request).isEmpty());
	}

	@Test
	void marker_passesInARequestElement() {
		AiCompletionRequest request = request(Map.of(UnitKeys.TEXT_INPUT, 1L),
				List.of(withKey(UnitKeys.INVALID_BREAKDOWN_MARKER)));
		assertTrue(VALIDATOR.validate(request).isEmpty());
	}

	@Test
	void malformedMarkers_stillFail_inBothPlaces() {
		for (String bad : List.of("!", "!!x", "!Bad", "bad!", "!x!", "! x", "!_x", "!x_")) {
			assertFalse(VALIDATOR.validate(request(withKey(bad), List.of())).isEmpty(), "u: " + bad);
			assertFalse(VALIDATOR.validate(request(Map.of(UnitKeys.TEXT_INPUT, 1L), List.of(withKey(bad))))
					.isEmpty(), "r: " + bad);
		}
	}

	@Test
	void markerLengthCap_isStillEnforced() {
		String tooLong = UnitKeys.MARKER_PREFIX + "k".repeat(UnitKeys.MAX_KEY_LENGTH);
		assertFalse(VALIDATOR.validate(request(withKey(tooLong), List.of())).isEmpty());
	}

	@Test
	void unitRateRequest_stillRejectsTheMarker() {
		AiUnitRateRequest marker = new AiUnitRateRequest("anthropic", "m", UnitKeys.INVALID_BREAKDOWN_MARKER,
				RateVariant.DEFAULT, "TEXT", "TOKEN", 3L, null, null, null);
		assertEquals(Set.of("unitKey"), VALIDATOR.validate(marker).stream()
				.map(v -> v.getPropertyPath().toString()).collect(java.util.stream.Collectors.toSet()));

		AiUnitRateRequest ok = new AiUnitRateRequest("anthropic", "m", UnitKeys.TEXT_INPUT,
				RateVariant.DEFAULT, "TEXT", "TOKEN", 3L, null, null, null);
		assertTrue(VALIDATOR.validate(ok).isEmpty());
	}

	@Test
	void keyPattern_isUnchanged_andMarkerIsNotAKnownKey() {
		assertEquals("^[a-z0-9]+(_[a-z0-9]+)*$", UnitKeys.KEY_PATTERN);
		assertFalse(UnitKeys.INVALID_BREAKDOWN_MARKER.matches(UnitKeys.KEY_PATTERN));
		assertFalse(UnitKeys.KNOWN.contains(UnitKeys.INVALID_BREAKDOWN_MARKER));
		assertEquals("!invalid_token_breakdown", UnitKeys.INVALID_BREAKDOWN_MARKER);
	}

	@Test
	void roundTrip_json_andCbor_keepTheMarker() throws Exception {
		AiCompletionRequest request = request(withKey(UnitKeys.INVALID_BREAKDOWN_MARKER),
				List.of(withKey(UnitKeys.INVALID_BREAKDOWN_MARKER)));

		assertEquals(request, JSON.readValue(JSON.writeValueAsString(request), AiCompletionRequest.class));
		assertEquals(request, CBOR.readValue(CBOR.writeValueAsBytes(request), AiCompletionRequest.class));
		assertTrue(JSON.readTree(JSON.writeValueAsString(request)).get("u")
				.has(UnitKeys.INVALID_BREAKDOWN_MARKER));
	}
}
