package dev.bluestep.global.dto;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.HexFormat;
import java.util.Optional;

import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.fasterxml.jackson.datatype.jdk8.Jdk8Module;
import org.msgpack.jackson.dataformat.MessagePackFactory;

import dev.bluestep.global.dto.ai.AiCompletionRequest;
import dev.bluestep.global.dto.ai.AiDenialCode;
import dev.bluestep.global.dto.ai.AiPreflightRequest;
import dev.bluestep.global.dto.ai.AiPreflightResponse;

/**
 * Cross-version wire compatibility with web-shared <b>1.2.2</b>.
 *
 * <p>2.0.0 is a source- and binary-breaking release, but it must not be a <em>wire</em>
 * breaking one: the web monolith stays on 1.2.2 and keeps calling the AI gate throughout.
 * A caller that cannot be recompiled in lockstep is the whole reason the migration chose
 * {@code Optional} over a nullable component in the first place — the claim is that the
 * two produce an identical wire, and this class is where that claim is actually checked
 * rather than asserted.</p>
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

		assertEquals("acme", request.schemaName());
		assertEquals("org-1", request.organizationId());
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
	 * msgpack is the transport the monolith actually uses today, and it is served by a
	 * hand-built Jackson 2 mapper because no Jackson 3 msgpack dataformat exists. That
	 * mapper needs {@code Jdk8Module} registered explicitly — without it these payloads
	 * bind literal nulls into the records instead of empty Optionals.
	 */
	@Test
	void legacyPreflightRequestMsgpackBindsToEmptyOptionals() throws Exception {
		AiPreflightRequest request =
				MSGPACK.readValue(hex(PREFLIGHT_REQ_NULLS_MP), AiPreflightRequest.class);

		assertEquals("acme", request.schemaName());
		assertEquals("org-1", request.organizationId());
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

		AiCompletionRequest completion = new AiCompletionRequest(
				"trk-7", 100, 200, 1500L, 3, Optional.empty(), Optional.empty(),
				Optional.empty(), Optional.empty(), Optional.empty(), Optional.empty());
		assertEquals(COMPLETION_REQ_NULLS, JACKSON3.writeValueAsString(completion));
		assertArrayEquals(hex(COMPLETION_REQ_NULLS_MP), MSGPACK.writeValueAsBytes(completion));
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
