package dev.bluestep.global.dto.ai;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeSet;

import org.hibernate.validator.messageinterpolation.ParameterMessageInterpolator;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.annotation.JsonProperty;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;

import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.dataformat.cbor.CBORMapper;

/**
 * {@link AiCompletionRequest#requestMeta()} ({@code "rm"}, 6.3.0) and {@link AiRequestMeta}: validation, absence,
 * the cap, alignment left to web-global, and wire compatibility with 6.2.x in both directions.
 */
class AiCompletionRequestRequestMetaTest {

	private static final ObjectMapper JSON = JsonMapper.builder().build();
	private static final ObjectMapper CBOR = CBORMapper.builder().build();
	private static final Validator VALIDATOR = validator();

	/**
	 * Emitted by the real {@code dev.bluestep:web-shared:6.2.1} jar (from the Gradle cache) for
	 * {@code of("trk-62", 1500, 500, 1234, 2, "end_turn", -, u, r)}. Regenerate only against that jar.
	 */
	private static final String COMPLETION_6_2_1 =
			"{\"t\":\"trk-62\",\"i\":1500,\"o\":500,\"l\":1234,\"n\":2,\"s\":\"end_turn\",\"e\":null,"
					+ "\"u\":{\"text_output\":500,\"text_input\":400},\"a\":null,\"ai\":null,\"ci\":null,\"ao\":null,"
					+ "\"r\":[{\"text_output\":200,\"text_input\":100},{\"text_output\":300,\"text_input\":300}]}";

	private static Validator validator() {
		try (ValidatorFactory factory = Validation.byDefaultProvider().configure()
				.messageInterpolator(new ParameterMessageInterpolator())
				.buildValidatorFactory()) {
			return factory.getValidator();
		}
	}

	private static Set<String> violatedPaths(Object bean) {
		Set<String> paths = new TreeSet<>();
		for (ConstraintViolation<Object> violation : VALIDATOR.validate(bean)) {
			paths.add(violation.getPropertyPath().toString());
		}
		return paths;
	}

	private static AiRequestMeta meta(long t, long f) {
		return new AiRequestMeta(t, f, "claude-haiku-5-5", "req_011", false);
	}

	private static List<Map<String, Long>> twoRequests() {
		return List.of(Map.of(UnitKeys.TEXT_INPUT, 4L), Map.of(UnitKeys.TEXT_INPUT, 6L));
	}

	private static AiCompletionRequest withMeta(@Nullable List<Map<String, Long>> requests, List<AiRequestMeta> meta) {
		return new AiCompletionRequest("trk", 10, 5, 100L, 2, null, null, Map.of(UnitKeys.TEXT_INPUT, 10L), null,
				null, null, null, requests, meta);
	}

	@Test
	void validMeta_hasNoViolations_andIsAccessibleInOrder() {
		List<AiRequestMeta> meta = List.of(meta(1_000L, 2_000L),
				new AiRequestMeta(2_000L, 2_000L, "claude-haiku-5-5", null, true));
		AiCompletionRequest request = AiCompletionRequest.of("trk", 10, 5, 100L, 2, Optional.empty(),
				Optional.empty(), Map.of(UnitKeys.TEXT_INPUT, 10L), twoRequests(), meta);

		assertEquals(Set.of(), violatedPaths(request));
		assertEquals(Optional.of(meta), request.optRequestMeta());
		assertEquals(Optional.of("req_011"), request.requestMeta().get(0).optProviderRequestId());
		assertEquals(Optional.empty(), request.requestMeta().get(1).optProviderRequestId());
		assertTrue(request.requestMeta().get(1).partial());
	}

	@Test
	void absent_isAbsent_fromEveryOlderConstructorAndFactory() {
		assertEquals(Optional.empty(), AiCompletionRequest.of("trk", 1, 2, 3L, 1, Optional.empty(), Optional.empty(),
				Map.of()).optRequestMeta());
		assertEquals(Optional.empty(), AiCompletionRequest.of("trk", 1, 2, 3L, 1, Optional.empty(), Optional.empty(),
				Map.of(), List.of()).optRequestMeta());
		assertEquals(Optional.empty(), new AiCompletionRequest("trk", 1, 2, 3L, 1, null, null, null, null, null,
				null, null).optRequestMeta());

		AiCompletionRequest sixTwo = new AiCompletionRequest("trk", 1, 2, 3L, 1, null, null, null, null, null,
				null, null, twoRequests());
		assertEquals(Optional.of(twoRequests()), sixTwo.optRequestUnitAmounts(),
				"the 6.2.x constructor keeps the amounts it was given");
		assertEquals(Optional.empty(), sixTwo.optRequestMeta());
	}

	@Test
	void copyIsImmutableAndDetached() {
		List<AiRequestMeta> source = new ArrayList<>(List.of(meta(1L, 2L)));
		AiCompletionRequest request = withMeta(List.of(Map.of(UnitKeys.TEXT_INPUT, 10L)), source);

		source.add(meta(3L, 4L));

		assertEquals(List.of(meta(1L, 2L)), request.requestMeta());
		assertThrows(UnsupportedOperationException.class, () -> request.requestMeta().add(meta(5L, 6L)));
	}

	/** A null element is not thrown on: it becomes {@link AiRequestMeta#ABSENT}, the amounts survive, and validation
	 * reports its missing fields at its index. */
	@Test
	void nullElement_bindsAsAbsent_andIsAViolation() {
		List<AiRequestMeta> meta = new ArrayList<>();
		meta.add(meta(1L, 2L));
		meta.add(null);

		AiCompletionRequest request = new AiCompletionRequest("trk", 10, 5, 100L, 2, null, null,
				Map.of(UnitKeys.TEXT_INPUT, 10L), null, null, null, null, twoRequests(), meta);

		assertEquals(Set.of("requestMeta[1].startedAt", "requestMeta[1].finishedAt", "requestMeta[1].model",
				"requestMeta[1].partial"), violatedPaths(request));
		assertEquals(List.of(meta(1L, 2L), AiRequestMeta.ABSENT), request.requestMeta());
		assertEquals(Optional.of(twoRequests()), request.optRequestUnitAmounts());
	}

	@Test
	void finishedBeforeStarted_isAViolation_neverAThrow() {
		AiCompletionRequest request = withMeta(List.of(Map.of(UnitKeys.TEXT_INPUT, 10L)), List.of(meta(2_000L, 1_999L)));

		assertEquals(Set.of("requestMeta[0].finishedNotBeforeStarted"), violatedPaths(request));
		assertEquals(Set.of(), violatedPaths(withMeta(List.of(Map.of(UnitKeys.TEXT_INPUT, 10L)),
				List.of(meta(2_000L, 2_000L)))), "an instantaneous request is valid");
	}

	@Test
	void timestampsMustBePositive() {
		assertEquals(Set.of("startedAt"), violatedPaths(new AiRequestMeta(0L, 5L, "m", null, false)));
		assertEquals(Set.of("finishedAt", "finishedNotBeforeStarted"),
				violatedPaths(new AiRequestMeta(5L, 0L, "m", null, false)));
		assertEquals(Set.of("startedAt", "finishedAt"), violatedPaths(new AiRequestMeta(-5L, -1L, "m", null, false)));
	}

	@Test
	void modelIsRequiredAndBounded() {
		assertEquals(Set.of("model"), violatedPaths(new AiRequestMeta(1L, 2L, " ", null, false)));
		assertEquals(Set.of(), violatedPaths(
				new AiRequestMeta(1L, 2L, "m".repeat(AiRequestMeta.MAX_MODEL_LENGTH), null, false)));
		assertEquals(Set.of("model"), violatedPaths(
				new AiRequestMeta(1L, 2L, "m".repeat(AiRequestMeta.MAX_MODEL_LENGTH + 1), null, false)));
	}

	@Test
	void providerRequestIdIsOptionalAndBounded() {
		String atCap = "r".repeat(AiRequestMeta.MAX_PROVIDER_REQUEST_ID_LENGTH);
		assertEquals(Set.of(), violatedPaths(new AiRequestMeta(1L, 2L, "m", atCap, false)));
		assertEquals(Set.of("providerRequestId"), violatedPaths(new AiRequestMeta(1L, 2L, "m", atCap + "r", false)));
	}

	@Test
	void elementViolations_cascadeFromTheCompletion() {
		AiCompletionRequest request = withMeta(twoRequests(),
				List.of(meta(1L, 2L), new AiRequestMeta(1L, 2L, "", "r".repeat(201), false)));

		assertEquals(Set.of("requestMeta[1].model", "requestMeta[1].providerRequestId"), violatedPaths(request));
	}

	@Test
	void listCap_isEnforced() {
		List<AiRequestMeta> atCap = new ArrayList<>();
		for (int i = 0; i < AiCompletionRequest.MAX_REQUESTS; i++) {
			atCap.add(meta(1L, 2L));
		}
		assertEquals(Set.of(), violatedPaths(withMeta(null, atCap)));

		List<AiRequestMeta> overCap = new ArrayList<>(atCap);
		overCap.add(meta(1L, 2L));
		assertEquals(Set.of("requestMeta"), violatedPaths(withMeta(null, overCap)));
	}

	/** web-global checks the alignment and records the amounts with a marker when it fails; the DTO must not 400. */
	@Test
	void alignmentWithR_isNotEnforcedHere() {
		assertEquals(Set.of(), violatedPaths(withMeta(null, List.of(meta(1L, 2L)))), "rm without r");
		assertEquals(Set.of(), violatedPaths(withMeta(twoRequests(), List.of(meta(1L, 2L)))), "rm shorter than r");
		assertEquals(Set.of(), violatedPaths(withMeta(List.of(Map.of(UnitKeys.TEXT_INPUT, 10L)),
				List.of(meta(1L, 2L), meta(3L, 4L)))), "rm longer than r");
	}

	/** A bad value must bind and surface as a violation, so web-global can still record the amounts. */
	@Test
	void badValuesBind_andAreReportedAsViolations() throws Exception {
		String json = "{\"t\":\"trk\",\"i\":1,\"o\":2,\"l\":3,\"n\":1,\"u\":{\"text_input\":1},"
				+ "\"r\":[{\"text_input\":1}],\"rm\":[{\"t\":5,\"f\":4,\"id\":null,\"p\":false}]}";

		AiCompletionRequest request = JSON.readValue(json, AiCompletionRequest.class);

		assertEquals(Set.of("requestMeta[0].finishedNotBeforeStarted", "requestMeta[0].model"),
				violatedPaths(request));
		assertEquals(Optional.of(List.of(Map.of(UnitKeys.TEXT_INPUT, 1L))), request.optRequestUnitAmounts());
	}

	/**
	 * Missing, null and null-element metadata must bind under Jackson 3 defaults (which refuse a missing or null
	 * primitive) in JSON and in CBOR, keep the amounts, and be reported by validation, so web-global records the turn
	 * with {@code !invalid_request_meta} instead of refusing it.
	 */
	@Test
	void missingOrNullMetadata_binds_inJsonAndCbor_andIsAViolation() throws Exception {
		assertTrue(JSON.isEnabled(DeserializationFeature.FAIL_ON_NULL_FOR_PRIMITIVES), "Jackson 3 defaults");
		Map<String, Set<String>> cases = Map.of(
				"null", Set.of("requestMeta[0].startedAt", "requestMeta[0].finishedAt", "requestMeta[0].model",
						"requestMeta[0].partial"),
				"{\"f\":6,\"m\":\"m\",\"p\":false}", Set.of("requestMeta[0].startedAt"),
				"{\"t\":5,\"m\":\"m\",\"p\":false}", Set.of("requestMeta[0].finishedAt"),
				"{\"t\":5,\"f\":6,\"m\":\"m\"}", Set.of("requestMeta[0].partial"),
				"{\"t\":5,\"f\":6,\"p\":true}", Set.of("requestMeta[0].model"),
				"{\"t\":null,\"f\":null,\"m\":null,\"id\":null,\"p\":null}", Set.of("requestMeta[0].startedAt",
						"requestMeta[0].finishedAt", "requestMeta[0].model", "requestMeta[0].partial"),
				"{}", Set.of("requestMeta[0].startedAt", "requestMeta[0].finishedAt", "requestMeta[0].model",
						"requestMeta[0].partial"));
		for (Map.Entry<String, Set<String>> c : cases.entrySet()) {
			String json = "{\"t\":\"trk\",\"i\":1,\"o\":2,\"l\":3,\"n\":1,\"u\":{\"text_input\":1},"
					+ "\"r\":[{\"text_input\":1}],\"rm\":[" + c.getKey() + "]}";
			AiCompletionRequest viaJson = JSON.readValue(json, AiCompletionRequest.class);
			AiCompletionRequest viaCbor = CBOR.readValue(CBOR.writeValueAsBytes(JSON.readTree(json)),
					AiCompletionRequest.class);
			for (AiCompletionRequest request : List.of(viaJson, viaCbor)) {
				assertEquals(c.getValue(), violatedPaths(request), c.getKey());
				assertEquals(Optional.of(List.of(Map.of(UnitKeys.TEXT_INPUT, 1L))), request.optRequestUnitAmounts(),
						c.getKey());
				assertEquals(1, request.requestMeta().size(), c.getKey());
			}
		}
	}

	/**
	 * Codex r2: a present but malformed field (out of range, wrong type, an array or object where a scalar belongs) or
	 * an element that is not an object must bind too, as an absent field, in JSON and in CBOR; 6.2.1 accepted these
	 * payloads by ignoring "rm", so throwing on them would lose usage 6.2.1 kept.
	 */
	@Test
	void malformedMetadata_binds_inJsonAndCbor_asAbsentFields() throws Exception {
		String ok = "\"f\":6,\"m\":\"m\",\"p\":false";
		Set<String> all = Set.of("requestMeta[0].startedAt", "requestMeta[0].finishedAt", "requestMeta[0].model",
				"requestMeta[0].partial");
		Map<String, Set<String>> cases = Map.ofEntries(
				Map.entry("{\"t\":9223372036854775808," + ok + "}", Set.of("requestMeta[0].startedAt")),
				Map.entry("{\"t\":-9223372036854775809," + ok + "}", Set.of("requestMeta[0].startedAt")),
				Map.entry("{\"t\":1.5," + ok + "}", Set.of("requestMeta[0].startedAt")),
				Map.entry("{\"t\":\"5\"," + ok + "}", Set.of("requestMeta[0].startedAt")),
				Map.entry("{\"t\":[5]," + ok + "}", Set.of("requestMeta[0].startedAt")),
				Map.entry("{\"t\":5,\"f\":{\"x\":1},\"m\":\"m\",\"p\":false}", Set.of("requestMeta[0].finishedAt")),
				Map.entry("{\"t\":5,\"f\":6,\"m\":7,\"p\":false}", Set.of("requestMeta[0].model")),
				Map.entry("{\"t\":5,\"f\":6,\"m\":[\"m\"],\"p\":false}", Set.of("requestMeta[0].model")),
				Map.entry("{\"t\":5,\"f\":6,\"m\":\"m\",\"p\":\"unknown\"}", Set.of("requestMeta[0].partial")),
				Map.entry("{\"t\":5,\"f\":6,\"m\":\"m\",\"p\":1}", Set.of("requestMeta[0].partial")),
				Map.entry("{\"t\":5,\"f\":6,\"m\":\"m\",\"id\":42,\"p\":false}", Set.of()),
				Map.entry("5", all), Map.entry("\"x\"", all), Map.entry("[]", all), Map.entry("[1,2]", all),
				Map.entry("true", all));
		for (Map.Entry<String, Set<String>> c : cases.entrySet()) {
			String json = "{\"t\":\"trk\",\"i\":1,\"o\":2,\"l\":3,\"n\":1,\"u\":{\"text_input\":1},"
					+ "\"r\":[{\"text_input\":1}],\"rm\":[" + c.getKey() + "]}";
			AiCompletionRequest viaJson = JSON.readValue(json, AiCompletionRequest.class);
			AiCompletionRequest viaCbor = CBOR.readValue(CBOR.writeValueAsBytes(JSON.readTree(json)),
					AiCompletionRequest.class);
			for (AiCompletionRequest request : List.of(viaJson, viaCbor)) {
				assertEquals(c.getValue(), violatedPaths(request), c.getKey());
				assertEquals(Optional.of(List.of(Map.of(UnitKeys.TEXT_INPUT, 1L))), request.optRequestUnitAmounts(),
						c.getKey());
				assertEquals(Optional.empty(), request.requestMeta().get(0).optProviderRequestId(), c.getKey());
			}
		}
		AiCompletionRequest exact = JSON.readValue("{\"t\":\"trk\",\"i\":1,\"o\":2,\"l\":3,\"n\":1,"
				+ "\"rm\":[{\"t\":9223372036854775807,\"f\":9223372036854775807,\"m\":\"m\",\"id\":\"req\",\"p\":true}]}",
				AiCompletionRequest.class);
		assertEquals(List.of(new AiRequestMeta(Long.MAX_VALUE, Long.MAX_VALUE, "m", "req", true)), exact.requestMeta(),
				"the edge of a long is still read");
	}

	/** The optX() accessors are how a reader sees a component before validation has proved it present. */
	@Test
	void optAccessors_reportPresence() {
		AiRequestMeta empty = new AiRequestMeta(null, null, null, null, null);
		assertFalse(empty.optStartedAt().isPresent());
		assertFalse(empty.optFinishedAt().isPresent());
		assertEquals(Optional.empty(), empty.optModel());
		assertEquals(Optional.empty(), empty.optPartial());
		assertTrue(empty.isFinishedNotBeforeStarted(), "a missing timestamp is @NotNull's to report, not this rule's");
		AiRequestMeta full = meta(1L, 2L);
		assertEquals(1L, full.optStartedAt().getAsLong());
		assertEquals(2L, full.optFinishedAt().getAsLong());
		assertEquals(Optional.of("claude-haiku-5-5"), full.optModel());
		assertEquals(Optional.of(false), full.optPartial());
	}

	@Test
	void jsonShape_usesShortKeys_andNoDerivedProperty() throws Exception {
		AiCompletionRequest request = withMeta(twoRequests(),
				List.of(meta(1_000L, 2_000L), new AiRequestMeta(2_000L, 3_000L, "gpt-5", null, true)));

		JsonNode tree = JSON.readTree(JSON.writeValueAsString(request));
		JsonNode first = tree.get("rm").get(0);
		List<String> keys = new ArrayList<>();
		first.properties().forEach(entry -> keys.add(entry.getKey()));

		assertEquals(List.of("t", "f", "m", "id", "p"), keys, "the derived validity check is not a wire property");
		assertEquals(1_000L, first.get("t").asLong());
		assertEquals(2_000L, first.get("f").asLong());
		assertEquals("claude-haiku-5-5", first.get("m").asString());
		assertEquals("req_011", first.get("id").asString());
		assertFalse(first.get("p").asBoolean());
		assertTrue(tree.get("rm").get(1).get("id").isNull());
		assertTrue(tree.get("rm").get(1).get("p").asBoolean());
	}

	@Test
	void roundTrip_jsonAndCbor() throws Exception {
		AiCompletionRequest request = withMeta(twoRequests(),
				List.of(meta(1_762_400_000_000L, 1_762_400_003_456L),
						new AiRequestMeta(1_762_400_003_500L, 1_762_400_009_000L, "gpt-5", null, true)));

		assertEquals(request, JSON.readValue(JSON.writeValueAsString(request), AiCompletionRequest.class));
		assertEquals(request, CBOR.readValue(CBOR.writeValueAsBytes(request), AiCompletionRequest.class),
				"epoch millis are longs; a transport that narrowed them would misplace the request's hour");
	}

	// ---- wire compatibility with 6.2.x ---------------------------------------------------

	/** A 6.2.x caller (web on the old jar) reporting to a 6.3.0 gate: binds, with the metadata absent. */
	@Test
	void sixTwoPayload_bindsWithMetaAbsent_inBothTransports() throws Exception {
		AiCompletionRequest viaJson = JSON.readValue(COMPLETION_6_2_1, AiCompletionRequest.class);

		assertEquals(Optional.empty(), viaJson.optRequestMeta());
		assertEquals(Optional.of(List.of(Map.of(UnitKeys.TEXT_INPUT, 100L, UnitKeys.TEXT_OUTPUT, 200L),
				Map.of(UnitKeys.TEXT_INPUT, 300L, UnitKeys.TEXT_OUTPUT, 300L))), viaJson.optRequestUnitAmounts());
		assertEquals(Set.of(), violatedPaths(viaJson));

		byte[] cbor = CBOR.writeValueAsBytes(JSON.readTree(COMPLETION_6_2_1));
		assertEquals(viaJson, CBOR.readValue(cbor, AiCompletionRequest.class));
	}

	/** 6.3.0 adds exactly "rm", last, and every 6.2.1 key keeps its name, position and value. */
	@Test
	void sixThreeOutput_isSixTwoPlusRm() throws Exception {
		AiCompletionRequest sixTwo = JSON.readValue(COMPLETION_6_2_1, AiCompletionRequest.class);
		JsonNode legacy = JSON.readTree(COMPLETION_6_2_1);
		JsonNode current = JSON.readTree(JSON.writeValueAsString(sixTwo));

		List<String> legacyKeys = new ArrayList<>();
		legacy.properties().forEach(entry -> legacyKeys.add(entry.getKey()));
		List<String> currentKeys = new ArrayList<>();
		current.properties().forEach(entry -> currentKeys.add(entry.getKey()));

		List<String> expected = new ArrayList<>(legacyKeys);
		expected.add("rm");
		assertEquals(expected, currentKeys);
		for (String key : legacyKeys) {
			assertEquals(legacy.get(key), current.get(key), "6.2.1 key \"" + key + "\" must keep its value");
		}
	}

	/**
	 * The other direction: a 6.3.0 caller reaching a gate still on 6.2.x. Holds because Jackson 3 does not fail on an
	 * unknown property by default — pinned here, since a gate that turned it on would refuse every report carrying
	 * "rm" and leave the turn unmetered. The 6.2.1 record is mirrored below; the same payload was also read by the real
	 * 6.2.1 jar, in JSON and CBOR, when this was written.
	 */
	@Test
	void sixThreePayload_bindsOnASixTwoReader_inBothTransports() throws Exception {
		AiCompletionRequest current = withMeta(twoRequests(), List.of(meta(1L, 2L), meta(3L, 4L)));

		assertFalse(JsonMapper.builder().build().isEnabled(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES));
		assertFalse(CBORMapper.builder().build().isEnabled(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES));

		SixTwoCompletion viaJson = JSON.readValue(JSON.writeValueAsString(current), SixTwoCompletion.class);
		SixTwoCompletion viaCbor = CBOR.readValue(CBOR.writeValueAsBytes(current), SixTwoCompletion.class);

		SixTwoCompletion expected = new SixTwoCompletion("trk", 10, 5, 100L, 2, null, null,
				Map.of(UnitKeys.TEXT_INPUT, 10L), null, null, null, null, twoRequests());
		assertEquals(expected, viaJson);
		assertEquals(expected, viaCbor);
	}

	/** The component list and wire keys of web-shared 6.2.1's {@code AiCompletionRequest}, without its checks. */
	record SixTwoCompletion(
			@JsonProperty("t") String trackingId,
			@JsonProperty("i") int totalInputTokens,
			@JsonProperty("o") int totalOutputTokens,
			@JsonProperty("l") long totalLatencyMs,
			@JsonProperty("n") int iterationsUsed,
			@JsonProperty("s") @Nullable String stopReason,
			@JsonProperty("e") @Nullable String errorMessage,
			@JsonProperty("u") @Nullable Map<String, Long> unitAmounts,
			@JsonProperty("a") @Nullable Integer audioSeconds,
			@JsonProperty("ai") @Nullable Integer audioInputTokens,
			@JsonProperty("ci") @Nullable Integer cachedInputTokens,
			@JsonProperty("ao") @Nullable Integer audioOutputTokens,
			@JsonProperty("r") @Nullable List<Map<String, Long>> requestUnitAmounts) {
	}
}
