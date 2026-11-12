package dev.bluestep.global.dto.ai;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import org.hibernate.validator.messageinterpolation.ParameterMessageInterpolator;
import org.junit.jupiter.api.Test;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.dataformat.cbor.CBORMapper;

/** {@link AiCompletionRequest#requestUnitAmounts()}: validation, absence, the cap and the wire shape. */
class AiCompletionRequestPerRequestAmountsTest {

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

	private static Set<String> violatedPaths(Object bean) {
		Set<String> paths = new java.util.TreeSet<>();
		for (ConstraintViolation<Object> violation : VALIDATOR.validate(bean)) {
			paths.add(violation.getPropertyPath().toString());
		}
		return paths;
	}

	private static AiCompletionRequest withRequests(List<Map<String, Long>> requests) {
		return AiCompletionRequest.of("trk", 10, 5, 100L, 2, Optional.empty(), Optional.empty(),
				Map.of(UnitKeys.TEXT_INPUT, 10L), requests);
	}

	@Test
	void validReport_hasNoViolations_andIsAccessibleInOrder() {
		AiCompletionRequest request = withRequests(
				List.of(Map.of(UnitKeys.TEXT_INPUT, 4L), Map.of(UnitKeys.TEXT_INPUT, 6L)));

		assertTrue(VALIDATOR.validate(request).isEmpty());
		assertEquals(Optional.of(List.of(Map.of(UnitKeys.TEXT_INPUT, 4L), Map.of(UnitKeys.TEXT_INPUT, 6L))),
				request.optRequestUnitAmounts());
	}

	@Test
	void absent_isAbsent_notEmpty() {
		AiCompletionRequest request = AiCompletionRequest.of("trk", 10, 5, 100L, 2, Optional.empty(),
				Optional.empty(), Map.of(UnitKeys.TEXT_INPUT, 10L));

		assertEquals(null, request.requestUnitAmounts());
		assertEquals(Optional.empty(), request.optRequestUnitAmounts());
		assertTrue(VALIDATOR.validate(request).isEmpty());
	}

	@Test
	void emptyList_isPresentAndEmpty() {
		assertEquals(Optional.of(List.of()), withRequests(List.of()).optRequestUnitAmounts());
	}

	@Test
	void oldTwelveArgumentConstructor_leavesTheListAbsent() {
		AiCompletionRequest request = new AiCompletionRequest("trk", 1, 2, 3L, 1, null, null, null, null, null,
				null, null);

		assertEquals(Optional.empty(), request.optRequestUnitAmounts());
	}

	@Test
	void copiesAreImmutableAndDetached() {
		Map<String, Long> element = new HashMap<>(Map.of(UnitKeys.TEXT_INPUT, 4L));
		List<Map<String, Long>> source = new ArrayList<>(List.of(element));
		AiCompletionRequest request = withRequests(source);

		element.put(UnitKeys.TEXT_OUTPUT, 9L);
		source.add(Map.of());

		assertEquals(List.of(Map.of(UnitKeys.TEXT_INPUT, 4L)), request.requestUnitAmounts());
		assertThrows(UnsupportedOperationException.class, () -> request.requestUnitAmounts().add(Map.of()));
		assertThrows(UnsupportedOperationException.class,
				() -> request.requestUnitAmounts().get(0).put("x", 1L));
	}

	@Test
	void nullElement_isRejectedNamingTheIndex() {
		List<Map<String, Long>> requests = new ArrayList<>();
		requests.add(Map.of(UnitKeys.TEXT_INPUT, 1L));
		requests.add(null);

		IllegalArgumentException thrown = assertThrows(IllegalArgumentException.class, () -> withRequests(requests));
		assertTrue(thrown.getMessage().contains("index 1"), thrown.getMessage());
	}

	@Test
	void nullAndNegativeAmountsInAnElement_areRejectedNamingTheKey() {
		Map<String, Long> withNull = new HashMap<>();
		withNull.put(UnitKeys.TEXT_INPUT, null);
		assertTrue(assertThrows(IllegalArgumentException.class, () -> withRequests(List.of(withNull)))
				.getMessage().contains(UnitKeys.TEXT_INPUT));

		assertTrue(assertThrows(IllegalArgumentException.class,
				() -> withRequests(List.of(Map.of(UnitKeys.TEXT_INPUT, -1L)))).getMessage()
				.contains(UnitKeys.TEXT_INPUT));
	}

	@Test
	void badKeyInAnElement_isAConstraintViolation() {
		assertEquals(Set.of(), violatedPaths(withRequests(List.of(Map.of(UnitKeys.TEXT_INPUT, 1L)))));

		assertTrue(!violatedPaths(withRequests(List.of(Map.of("Not A Key!", 1L)))).isEmpty());
		assertTrue(!violatedPaths(withRequests(List.of(Map.of("", 1L)))).isEmpty());
		assertTrue(!violatedPaths(withRequests(List.of(
				Map.of("k".repeat(UnitKeys.MAX_KEY_LENGTH + 1), 1L)))).isEmpty());
	}

	@Test
	void tooManyKeysInOneElement_isAConstraintViolation() {
		Map<String, Long> atCap = new HashMap<>();
		for (int i = 0; i < AiCompletionRequest.MAX_UNIT_KEYS; i++) {
			atCap.put("key_" + i, 1L);
		}
		assertTrue(violatedPaths(withRequests(List.of(atCap))).isEmpty());

		Map<String, Long> overCap = new HashMap<>(atCap);
		overCap.put("one_more", 1L);
		assertTrue(!violatedPaths(withRequests(List.of(overCap))).isEmpty());
	}

	@Test
	void listCap_isEnforced() {
		List<Map<String, Long>> atCap = new ArrayList<>();
		for (int i = 0; i < AiCompletionRequest.MAX_REQUESTS; i++) {
			atCap.add(Map.of(UnitKeys.TEXT_INPUT, 0L));
		}
		assertTrue(violatedPaths(withRequests(atCap)).isEmpty());

		List<Map<String, Long>> overCap = new ArrayList<>(atCap);
		overCap.add(Map.of(UnitKeys.TEXT_INPUT, 0L));
		assertEquals(Set.of("requestUnitAmounts"), violatedPaths(withRequests(overCap)));
	}

	@Test
	void sumInvariant_isNotEnforcedHere() {
		AiCompletionRequest mismatched = withRequests(List.of(Map.of(UnitKeys.TEXT_INPUT, 999L)));

		assertTrue(VALIDATOR.validate(mismatched).isEmpty(), "web-global owns the sum check");
	}

	@Test
	void jsonRoundTrip_usesKeyR_inOrder_andAbsentBindsAbsent() throws Exception {
		AiCompletionRequest request = withRequests(
				List.of(Map.of(UnitKeys.TEXT_INPUT, 4L), Map.of(UnitKeys.TEXT_INPUT, 6L)));

		String json = JSON.writeValueAsString(request);
		JsonNode tree = JSON.readTree(json);
		assertTrue(tree.has("r"));
		assertEquals(4L, tree.get("r").get(0).get(UnitKeys.TEXT_INPUT).asLong());
		assertEquals(6L, tree.get("r").get(1).get(UnitKeys.TEXT_INPUT).asLong());
		assertEquals(request, JSON.readValue(json, AiCompletionRequest.class));
		assertEquals(request, CBOR.readValue(CBOR.writeValueAsBytes(request), AiCompletionRequest.class));

		AiCompletionRequest withoutR = JSON.readValue(
				"{\"t\":\"trk\",\"i\":1,\"o\":2,\"l\":3,\"n\":1,\"u\":{\"" + UnitKeys.TEXT_INPUT + "\":1}}",
				AiCompletionRequest.class);
		assertEquals(Optional.empty(), withoutR.optRequestUnitAmounts());
	}
}
