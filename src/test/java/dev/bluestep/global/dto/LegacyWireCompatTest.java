package dev.bluestep.global.dto;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.fasterxml.jackson.datatype.jdk8.Jdk8Module;
import org.msgpack.jackson.dataformat.MessagePackFactory;

import dev.bluestep.global.dto.ai.AiCompletionRequest;
import dev.bluestep.global.dto.ai.AiDenialCode;
import dev.bluestep.global.dto.ai.AiPreflightRequest;
import dev.bluestep.global.dto.ai.AiPreflightResponse;
import dev.bluestep.global.dto.ai.UnitKeys;

/**
 * Cross-version wire compatibility with web-shared <b>1.2.2</b>.
 *
 * <p>2.0.0 was source- and binary-breaking but not <em>wire</em> breaking, and this class is where
 * that claim was actually checked rather than asserted. 3.0.0 narrows the claim rather than keeping
 * it: the completion request gains one key, {@code "u"}, and every other payload is still emitted
 * byte-identically. What has to hold now is that the added key is <em>additive</em> — every legacy
 * key keeps its name, its position and its value — so a reader that does not know it ignores it,
 * rather than binding shifted values silently.</p>
 *
 * <p><b>Why this still matters after the monolith moves to 3.0.0.</b> web rolls out per namespace
 * against a single web-global, so callers on the old shape and the new shape report to the same gate
 * concurrently, and for as long as the stagger lasts. The compatibility being pinned here is not a
 * transient upgrade window; it is a steady state the gate has to serve.</p>
 *
 * <p><b>4.0.0 renamed these components and changed nothing here.</b> {@code schemaName} became
 * {@code tenantId} and {@code organizationId} became {@code unitId}. The assertions below now read
 * the new accessors off byte-identical 1.2.2 payloads, which is the whole proof: {@link
 * AiPreflightRequest} pins every component to a one-character {@code @JsonProperty}, so the
 * vocabulary change stopped at the Java source. If someone ever aligns those annotations with the
 * component names, these tests fail — which is the point.</p>
 *
 * <p><b>Provenance of the golden payloads.</b> Every constant below was captured by running
 * the real {@code dev.bluestep:web-shared:1.2.2} artifact — not by hand-writing what 1.2.2
 * was assumed to emit. 1.2.2 declares no {@code @JsonInclude}, so Jackson's default
 * inclusion applies and a null component is written as an explicit null key rather than
 * being omitted; both shapes are covered regardless, since
 * {@code OptionalWireContractTest.absentKeyBindsToEmpty} pins the omitted case.</p>
 *
 * <p>Regenerate these only against a real 1.2.2 jar. Editing one to make a test pass would
 * silently redefine the contract this class exists to hold still.</p>
 */
class LegacyWireCompatTest {

	/** Mirrors web-global's primary Boot 4 mapper for JSON. */
	private static final tools.jackson.databind.ObjectMapper JACKSON3 =
			tools.jackson.databind.json.JsonMapper.builder().build();

	/** Mirrors the Jackson 2 mapper web-global's MessagePackConfig hand-builds. */
	private static final ObjectMapper MSGPACK = JsonMapper.builder(new MessagePackFactory())
			.addModule(new Jdk8Module())
			.build();

	/** Mirrors a Jackson 2 JSON mapper, as the monolith itself runs. */
	private static final ObjectMapper JACKSON2 = JsonMapper.builder().addModule(new Jdk8Module()).build();

	// ---- goldens emitted by web-shared 1.2.2 -------------------------------------------

	private static final String PREFLIGHT_REQ_NULLS =
			"{\"s\":\"acme\",\"o\":\"org-1\",\"u\":\"user-9\",\"f\":null,\"t\":null,"
					+ "\"p\":\"anthropic\",\"m\":\"claude-opus-4\"}";
	private static final String PREFLIGHT_REQ_NULLS_MP =
			"87a173a461636d65a16fa56f72672d31a175a6757365722d39a166c0a174c0a170a9616e7468726f706963"
					+ "a16dad636c617564652d6f7075732d34";

	private static final String COMPLETION_REQ_NULLS =
			"{\"t\":\"trk-7\",\"i\":100,\"o\":200,\"l\":1500,\"n\":3,\"s\":null,\"e\":null,"
					+ "\"a\":null,\"ai\":null,\"ci\":null,\"ao\":null}";
	private static final String COMPLETION_REQ_NULLS_MP =
			"8ba174a574726b2d37a16964a16fccc8a16ccd05dca16e03a173c0a165c0a161c0a26169c0a26369c0a2616fc0";

	private static final String COMPLETION_REQ_FULL =
			"{\"t\":\"trk-8\",\"i\":100,\"o\":200,\"l\":1500,\"n\":3,\"s\":\"end_turn\",\"e\":\"boom\","
					+ "\"a\":12,\"ai\":34,\"ci\":56,\"ao\":78}";

	private static final String PREFLIGHT_RESP_AUTHORIZED =
			"{\"a\":true,\"t\":\"trk-1\",\"d\":null,\"i\":4}";
	private static final String PREFLIGHT_RESP_AUTHORIZED_MP =
			"84a161c3a174a574726b2d31a164c0a16904";

	private static byte[] hex(String s) {
		return HexFormat.of().parseHex(s);
	}

	// ---- inbound: what the monolith sends, read by 2.0.0 --------------------------------

	@Test
	void legacyPreflightRequestJsonBindsToEmptyOptionals() throws Exception {
		AiPreflightRequest request = JACKSON3.readValue(PREFLIGHT_REQ_NULLS, AiPreflightRequest.class);

		assertEquals("acme", request.tenantId());
		assertEquals("org-1", request.unitId());
		assertEquals(Optional.empty(), request.flag());
		assertEquals(Optional.empty(), request.triggeringProcess());
		assertEquals("claude-opus-4", request.model());
	}

	@Test
	void legacyCompletionRequestJsonBindsToEmptyOptionals() throws Exception {
		AiCompletionRequest request = JACKSON3.readValue(COMPLETION_REQ_NULLS, AiCompletionRequest.class);

		assertEquals("trk-7", request.trackingId());
		assertEquals(100, request.totalInputTokens());
		assertEquals(Optional.empty(), request.stopReason());
		assertEquals(Optional.empty(), request.errorMessage());
		assertEquals(Optional.empty(), request.audioSeconds());
		assertEquals(Optional.empty(), request.audioInputTokens());
		assertEquals(Optional.empty(), request.cachedInputTokens());
		assertEquals(Optional.empty(), request.audioOutputTokens());
		assertEquals(Optional.empty(), request.unitAmounts(),
				"1.2.2 has no amount vector to send, and an absent one must arrive as absent rather than as an "
						+ "empty map — the legacy lane is read precisely when this is absent, and an empty map "
						+ "would instead mean a current caller that metered nothing");
	}

	@Test
	void legacyCompletionRequestJsonBindsPresentValues() throws Exception {
		AiCompletionRequest request = JACKSON3.readValue(COMPLETION_REQ_FULL, AiCompletionRequest.class);

		assertEquals(Optional.of("end_turn"), request.stopReason());
		assertEquals(Optional.of("boom"), request.errorMessage());
		assertEquals(Optional.of(12), request.audioSeconds());
		assertEquals(Optional.of(78), request.audioOutputTokens());
	}

	/**
	 * msgpack is served by a hand-built Jackson 2 mapper because no Jackson 3 msgpack
	 * dataformat exists. That mapper needs {@code Jdk8Module} registered explicitly — without
	 * it these payloads bind literal nulls into the records instead of empty Optionals.
	 *
	 * <p>The monolith itself has moved to CBOR ({@code AiUsageGateClient}), so msgpack is no
	 * longer the transport it speaks; web-global still advertises it on
	 * {@code application/x-msgpack}, and the hand-built mapper is the one place where Jackson
	 * 2's unknown-property strictness still applies, so it stays pinned here.</p>
	 */
	@Test
	void legacyPreflightRequestMsgpackBindsToEmptyOptionals() throws Exception {
		AiPreflightRequest request =
				MSGPACK.readValue(hex(PREFLIGHT_REQ_NULLS_MP), AiPreflightRequest.class);

		assertEquals("acme", request.tenantId());
		assertEquals("org-1", request.unitId());
		assertEquals(Optional.empty(), request.flag());
		assertEquals(Optional.empty(), request.triggeringProcess());
	}

	@Test
	void legacyCompletionRequestMsgpackBindsToEmptyOptionals() throws Exception {
		AiCompletionRequest request =
				MSGPACK.readValue(hex(COMPLETION_REQ_NULLS_MP), AiCompletionRequest.class);

		assertEquals("trk-7", request.trackingId());
		assertEquals(1500L, request.totalLatencyMs());
		assertEquals(Optional.empty(), request.stopReason());
		assertEquals(Optional.empty(), request.audioOutputTokens());
	}

	// ---- outbound: what 2.0.0 emits, read by a 1.2.2 client -----------------------------

	/**
	 * The reverse direction, proved by identity rather than by round-tripping through a
	 * second artifact: if 2.0.0's bytes are the bytes 1.2.2 itself produces, then a 1.2.2
	 * reader handles them exactly as it handles its own output. Nothing about the monolith
	 * has to be assumed.
	 */
	@Test
	void currentResponseJsonIsByteIdenticalToLegacy() throws Exception {
		AiPreflightResponse response =
				new AiPreflightResponse(true, "trk-1", Optional.empty(), Optional.of(4));

		assertEquals(PREFLIGHT_RESP_AUTHORIZED, JACKSON3.writeValueAsString(response),
				"Jackson 3 output must match what web-shared 1.2.2 emitted");
		assertEquals(PREFLIGHT_RESP_AUTHORIZED, JACKSON2.writeValueAsString(response),
				"Jackson 2 output must match what web-shared 1.2.2 emitted");
	}

	@Test
	void currentResponseMsgpackIsByteIdenticalToLegacy() throws Exception {
		AiPreflightResponse response =
				new AiPreflightResponse(true, "trk-1", Optional.empty(), Optional.of(4));

		assertArrayEquals(hex(PREFLIGHT_RESP_AUTHORIZED_MP), MSGPACK.writeValueAsBytes(response),
				"an empty Optional must encode as msgpack nil, exactly as the nullable component did");
	}

	@Test
	void currentRequestsAreByteIdenticalToLegacy() throws Exception {
		AiPreflightRequest preflight = new AiPreflightRequest(
				"acme", "org-1", "user-9", Optional.empty(), Optional.empty(),
				"anthropic", "claude-opus-4");
		assertEquals(PREFLIGHT_REQ_NULLS, JACKSON3.writeValueAsString(preflight));
		assertArrayEquals(hex(PREFLIGHT_REQ_NULLS_MP), MSGPACK.writeValueAsBytes(preflight));

	}

	/**
	 * The direction that actually happens under staggered deploys: a 1.2.2 payload read by 3.0.0.
	 *
	 * <p>web rolls out per namespace against a single web-global, so pre-vector callers report to the current
	 * gate concurrently and for a long time. This pins that their payload still binds, and — the part that
	 * decides how the gate prices it — that the absent vector arrives as <em>absent</em> rather than as an empty
	 * map. Those are different facts: empty would mean "a current caller metered nothing", which would send the
	 * turn down the vector branch and price a legacy report as though it had reported no charges at all.</p>
	 */
	@Test
	void legacyCompletionPayloadBindsWithTheVectorAbsent() throws Exception {
		AiCompletionRequest request = JACKSON3.readValue(COMPLETION_REQ_NULLS, AiCompletionRequest.class);

		assertEquals(Optional.empty(), request.unitAmounts(),
				"1.2.2 sends no \"u\" at all; absent must not be normalised into an empty vector");
		assertEquals("trk-7", request.trackingId());
		assertEquals(100, request.totalInputTokens());
		assertEquals(200, request.totalOutputTokens());
	}

	/**
	 * The completion request is the one payload 3.0.0 does <em>not</em> emit byte-identically, and this states
	 * exactly how it differs rather than dropping the claim.
	 *
	 * <p>It gained {@code "u"}, the amount vector. The claim being checked is that every legacy key keeps its
	 * name, its position and its value, so the addition is a key an old reader ignores rather than a reshuffle
	 * that makes it bind wrong values silently.</p>
	 *
	 * <p>Checked structurally — key list against key list — rather than by deleting a substring from the output
	 * and comparing it to a constant built by inserting that same substring. That form cannot fail: it is a
	 * property of two hand-written strings, not of the serializer.</p>
	 */
	@Test
	void currentCompletionRequestAddsTheVectorAndChangesNothingElse() throws Exception {
		AiCompletionRequest completion = new AiCompletionRequest(
				"trk-7", 100, 200, 1500L, 3, Optional.empty(), Optional.empty(), Optional.empty(),
				Optional.empty(), Optional.empty(), Optional.empty(), Optional.empty());

		JsonNode legacy = JACKSON2.readTree(COMPLETION_REQ_NULLS);
		JsonNode current =
				JACKSON2.readTree(JACKSON3.writeValueAsString(completion));

		List<String> legacyKeys = new ArrayList<>();
		legacy.fieldNames().forEachRemaining(legacyKeys::add);
		List<String> currentKeys = new ArrayList<>();
		current.fieldNames().forEachRemaining(currentKeys::add);

		List<String> added = new ArrayList<>(currentKeys);
		added.removeAll(legacyKeys);
		assertEquals(List.of("u"), added, "3.0.0 may add \"u\" and nothing else");

		assertEquals(legacyKeys, currentKeys.stream().filter(k -> !"u".equals(k)).toList(),
				"every legacy key must keep its name and its position");
		for (String key : legacyKeys) {
			assertEquals(legacy.get(key), current.get(key),
					"legacy key \"" + key + "\" must keep its value and encoding");
		}
	}

	/**
	 * The payload that now carries money, pinned in both transports.
	 *
	 * <p>Every other assertion here uses an empty vector, which encodes the same however the map is handled. A
	 * populated one is what a real turn sends and what the gate prices from, so its key spellings, its integer
	 * widths and its round trip are the thing worth holding still — an amount that survives JSON but truncates
	 * or reorders in the binary transport would bill wrong with nothing else failing.</p>
	 */
	@Test
	void populatedVectorRoundTripsThroughBothTransports() throws Exception {
		AiCompletionRequest completion = AiCompletionRequest.of(
				"trk-9", 1_500, 500, 1234L, 2, Optional.of("end_turn"), Optional.empty(),
				Map.of(UnitKeys.TEXT_INPUT, 400L, UnitKeys.CACHED_INPUT, 900L,
						UnitKeys.CACHE_WRITE_5M, 200L, UnitKeys.TEXT_OUTPUT, 500L));

		Map<String, Long> expected = Map.of(UnitKeys.TEXT_INPUT, 400L, UnitKeys.CACHED_INPUT, 900L,
				UnitKeys.CACHE_WRITE_5M, 200L, UnitKeys.TEXT_OUTPUT, 500L);

		AiCompletionRequest viaJson =
				JACKSON3.readValue(JACKSON3.writeValueAsString(completion), AiCompletionRequest.class);
		assertEquals(Optional.of(expected), viaJson.unitAmounts());

		AiCompletionRequest viaMsgpack =
				MSGPACK.readValue(MSGPACK.writeValueAsBytes(completion), AiCompletionRequest.class);
		assertEquals(Optional.of(expected), viaMsgpack.unitAmounts(),
				"amounts are longs; a transport that narrowed them would bill a different number");
	}

	/**
	 * A denied response carries an enum in the component that is empty above, so the
	 * enum-valued case is pinned too rather than only the empty one.
	 */
	@Test
	void deniedResponseIsByteIdenticalToLegacy() throws Exception {
		AiPreflightResponse denied = new AiPreflightResponse(
				false, "trk-2", Optional.of(AiDenialCode.TENANT_BLOCKED), Optional.empty());

		assertEquals("{\"a\":false,\"t\":\"trk-2\",\"d\":\"TENANT_BLOCKED\",\"i\":null}",
				JACKSON3.writeValueAsString(denied));
	}
}
