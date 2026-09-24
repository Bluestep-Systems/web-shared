package dev.bluestep.global.dto.usage;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jdk8.Jdk8Module;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;

import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.dataformat.cbor.CBORMapper;

/**
 * Pins every wire key of the usage metering DTOs.
 *
 * <p>Producers and web-global bind the same records, but a deployed pod keeps sending the keys its
 * jar was built with, so a renamed key or retyped component here would compile clean in every
 * consumer and silently mis-bind every push from a pod still on the previous release. These tests
 * fail instead. The pinned JSON literals are the contract as prose.</p>
 *
 * <p>Jackson 3 is what both consumers' Boot 4 mappers use, JSON for reads and CBOR for pushes.
 * The storage read shapes are also pinned against a Jackson 2 mapper configured the way
 * web-global's msgpack converter is ({@code Jdk8Module}, {@code JavaTimeModule}, ISO dates),
 * since that converter can answer the same GET.</p>
 */
class UsageWireContractTest {

	private static final ObjectMapper JSON = JsonMapper.builder().build();
	private static final ObjectMapper CBOR = CBORMapper.builder().build();
	private static final com.fasterxml.jackson.databind.ObjectMapper JACKSON2 =
			com.fasterxml.jackson.databind.json.JsonMapper.builder()
					.addModule(new Jdk8Module())
					.addModule(new JavaTimeModule())
					.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
					.build();

	private static final OffsetDateTime PERIOD = OffsetDateTime.of(2026, 9, 7, 0, 0, 0, 0, ZoneOffset.UTC);
	private static final LocalDate DAY = LocalDate.of(2026, 9, 7);

	/** Every value distinct, so a swapped pair of keys cannot cancel out. */
	private static final String PINNED_WINDOW_JSON = """
			{"t":1788696900000,"s":"U1000001","c":"http","h":11,"p":12,"q":13,"u":14,
			 "d":15,"a":16,"i":17,"j":18,"o":19,"x":20}
			""";

	private static final String PINNED_BATCH_JSON = """
			{"b":"a3a189c2-3a9e-4f6b-9be1-0d0a54c9a11f","n":"b6p-07","w":[%s]}
			""".formatted(PINNED_WINDOW_JSON);

	private static final String PINNED_STORAGE_BATCH_JSON = """
			{"namespace":"b6p-07","sampledAt":"2026-09-07T00:00:00Z","samples":[
			 {"schemaName":"U1000001","levels":{"db":31,"file_logical":32,"file_physical":33}}]}
			""";

	private static UsageWindow window() {
		return new UsageWindow(1_788_696_900_000L, "U1000001", "http",
				11, 12, 13, 14, 15, 16, 17, 18, 19, 20);
	}

	private static UsageBatchRequest batch() {
		return new UsageBatchRequest("a3a189c2-3a9e-4f6b-9be1-0d0a54c9a11f", "b6p-07",
				List.of(window()));
	}

	private static StorageSampleBatchRequest storageBatch() {
		return new StorageSampleBatchRequest("b6p-07", PERIOD,
				List.of(new StorageSample("U1000001",
						Map.of("db", 31L, "file_logical", 32L, "file_physical", 33L))));
	}

	private static StorageUsageResponse response() {
		return new StorageUsageResponse("U1000001", DAY, DAY.plusDays(6),
				List.of(new StorageLevel("db", PERIOD, 41)),
				List.of(new StorageUsageDay(DAY, "db", 1, 43, 45)));
	}

	private static Set<String> keysOf(final JsonNode node) {
		final Set<String> keys = new TreeSet<>();
		node.properties().forEach(entry -> keys.add(entry.getKey()));
		return keys;
	}

	@Test
	void usageWindow_serializesEveryComponentUnderItsPinnedKey() {
		final JsonNode node = JSON.valueToTree(window());

		assertEquals(Set.of("t", "s", "c", "h", "p", "q", "u", "d", "a", "i", "j", "o", "x"),
				keysOf(node), "the exact key set is the wire contract");
		assertEquals(1_788_696_900_000L, node.get("t").asLong());
		assertEquals("U1000001", node.get("s").asString());
		assertEquals("http", node.get("c").asString());
		assertEquals(11, node.get("h").asLong());
		assertEquals(12, node.get("p").asLong());
		assertEquals(13, node.get("q").asLong());
		assertEquals(14, node.get("u").asLong());
		assertEquals(15, node.get("d").asLong());
		assertEquals(16, node.get("a").asLong());
		assertEquals(17, node.get("i").asLong());
		assertEquals(18, node.get("j").asLong());
		assertEquals(19, node.get("o").asLong());
		assertEquals(20, node.get("x").asLong());
	}

	@Test
	void usageBatchRequest_serializesEveryComponentUnderItsPinnedKey() {
		final JsonNode node = JSON.valueToTree(batch());

		assertEquals(Set.of("b", "n", "w"), keysOf(node), "the exact key set is the wire contract");
		assertEquals("a3a189c2-3a9e-4f6b-9be1-0d0a54c9a11f", node.get("b").asString());
		assertEquals("b6p-07", node.get("n").asString());
		assertEquals(1, node.get("w").size());
	}

	/** The direction the ingest endpoint exercises: what a producer-built payload binds to. */
	@Test
	void pinnedUsageJson_bindsBackToTheRecords() {
		assertEquals(batch(), JSON.readValue(PINNED_BATCH_JSON, UsageBatchRequest.class));
		assertEquals(window(), JSON.readValue(PINNED_WINDOW_JSON, UsageWindow.class));
	}

	/** A quiet minute may omit {@code "w"} entirely; absence means the empty batch, not null. */
	@Test
	void omittedWindows_bindAsEmpty() {
		final UsageBatchRequest bound = JSON.readValue(
				"{\"b\":\"a3a189c2-3a9e-4f6b-9be1-0d0a54c9a11f\",\"n\":\"b6p-07\"}",
				UsageBatchRequest.class);

		assertTrue(bound.windows().isEmpty());
	}

	@Test
	void storageSampleBatch_serializesUnderComponentNames() {
		final JsonNode node = JSON.valueToTree(storageBatch());

		assertEquals(Set.of("namespace", "sampledAt", "samples"), keysOf(node));
		final JsonNode sample = node.get("samples").get(0);
		assertEquals(Set.of("schemaName", "levels"), keysOf(sample));
		assertEquals(Set.of("db", "file_logical", "file_physical"), keysOf(sample.get("levels")),
				"meter keys travel verbatim as object keys");
		assertEquals(storageBatch(), JSON.readValue(PINNED_STORAGE_BATCH_JSON,
				StorageSampleBatchRequest.class));
	}

	/** An omitted {@code samples} is the heartbeat: it binds as the empty pass. */
	@Test
	void omittedSamples_bindAsTheHeartbeat() {
		final StorageSampleBatchRequest bound = JSON.readValue(
				"{\"namespace\":\"b6p-07\",\"sampledAt\":\"2026-09-07T00:00:00Z\"}",
				StorageSampleBatchRequest.class);

		assertTrue(bound.samples().isEmpty());
	}

	/** An omitted {@code levels} binds as empty, which validation then refuses by name. */
	@Test
	void omittedLevels_bindAsEmpty() {
		final StorageSample bound = JSON.readValue("{\"schemaName\":\"U1000001\"}", StorageSample.class);

		assertTrue(bound.levels().isEmpty());
	}

	@Test
	void storageUsageResponse_pinsKeys() {
		final JsonNode node = JSON.valueToTree(response());

		assertEquals(Set.of("schemaName", "from", "to", "latest", "days"), keysOf(node));
		assertEquals(Set.of("meter", "sampledAt", "bytes"), keysOf(node.get("latest").get(0)));
		assertEquals(Set.of("day", "meter", "sampleCount", "maxBytes", "byteHours"),
				keysOf(node.get("days").get(0)));
		assertEquals("2026-09-07", node.get("from").asString(), "dates are ISO strings");
		assertEquals("2026-09-13", node.get("to").asString());
		assertEquals("2026-09-07", node.get("days").get(0).get("day").asString());
	}

	/** The wire format producers actually push — same mapper family Spring registers. */
	@Test
	void cbor_roundTripsEveryShape() {
		assertEquals(batch(), CBOR.readValue(CBOR.writeValueAsBytes(batch()), UsageBatchRequest.class));
		assertEquals(storageBatch(), CBOR.readValue(CBOR.writeValueAsBytes(storageBatch()),
				StorageSampleBatchRequest.class));
		assertEquals(response(), CBOR.readValue(CBOR.writeValueAsBytes(response()),
				StorageUsageResponse.class));
		final StorageUsageResponse neverSampled =
				new StorageUsageResponse("U1000001", DAY, DAY, List.of(), List.of());
		assertEquals(neverSampled, CBOR.readValue(CBOR.writeValueAsBytes(neverSampled),
				StorageUsageResponse.class));
	}

	/** Either mapper family can answer the storage GET; both must write the same tree. */
	@Test
	void jackson2AndJackson3WriteTheSameStorageWire() throws Exception {
		final StorageUsageResponse empty =
				new StorageUsageResponse("U1000001", DAY, DAY, List.of(), List.of());
		for (final StorageUsageResponse response : List.of(response(), empty)) {
			assertEquals(JSON.readTree(JSON.writeValueAsString(response)),
					JSON.readTree(JACKSON2.writeValueAsString(response)));
			assertEquals(response, JACKSON2.readValue(JSON.writeValueAsString(response),
					StorageUsageResponse.class));
			assertEquals(response, JSON.readValue(JACKSON2.writeValueAsString(response),
					StorageUsageResponse.class));
		}
	}

	/** A Java caller's null list means empty, as an omitted key does on the wire. */
	@Test
	void nullListsFoldToEmpty() {
		final StorageUsageResponse response = new StorageUsageResponse("U1000001", DAY, DAY, null, null);

		assertTrue(response.latest().isEmpty());
		assertTrue(response.days().isEmpty());
		assertTrue(new StorageSample("U1000001", null).levels().isEmpty());
	}

	private static UsageSummaryResponse summary() {
		return new UsageSummaryResponse(PERIOD, PERIOD.plusDays(14), List.of("http", "file"),
				List.of(new UsageSummaryRow("U1000001", 51, 52, 53, 54, 55, 56, 57, 58, 59, 60)));
	}

	@Test
	void usageSummaryResponse_pinsKeys() {
		final JsonNode node = JSON.valueToTree(summary());

		assertEquals(Set.of("from", "to", "categories", "tenants"), keysOf(node));
		assertEquals(Set.of("schemaName", "hits", "pageMillis", "pageMillisMax", "cpuMillis",
				"dbMillis", "allocBytes", "requestBytes", "requestBytesMax", "responseBytes",
				"responseBytesMax"), keysOf(node.get("tenants").get(0)));
		assertEquals("2026-09-07T00:00:00Z", node.get("from").asString(), "instants are ISO strings");
		final JsonNode row = node.get("tenants").get(0);
		assertEquals(51, row.get("hits").asLong());
		assertEquals(53, row.get("pageMillisMax").asLong());
		assertEquals(60, row.get("responseBytesMax").asLong());
	}

	/** Both mapper families and CBOR must carry the summary unchanged, empty or not. */
	@Test
	void usageSummaryResponse_roundTripsEveryMapper() throws Exception {
		final UsageSummaryResponse empty =
				new UsageSummaryResponse(PERIOD, PERIOD.plusDays(1), List.of("http"), List.of());
		for (final UsageSummaryResponse response : List.of(summary(), empty)) {
			assertEquals(response, CBOR.readValue(CBOR.writeValueAsBytes(response),
					UsageSummaryResponse.class));
			assertEquals(JSON.readTree(JSON.writeValueAsString(response)),
					JSON.readTree(JACKSON2.writeValueAsString(response)));
			assertEquals(response, JACKSON2.readValue(JSON.writeValueAsString(response),
					UsageSummaryResponse.class));
		}
		final UsageSummaryResponse nulls = new UsageSummaryResponse(PERIOD, PERIOD, null, null);
		assertTrue(nulls.categories().isEmpty());
		assertTrue(nulls.tenants().isEmpty());
	}

	private static final String PINNED_DB_BATCH_JSON = """
			{"namespace":"b6p-07","windowStart":"2026-09-07T00:00:00Z","windowEnd":"2026-09-07T00:05:00Z",
			 "deallocDelta":71,"statsReset":true,"entries":72,"entriesMax":73,"tenants":[
			 {"schemaName":"U1000001","calls":61,"execMicros":62,"sharedBlksRead":63,"sharedBlksHit":64}]}
			""";

	private static DbUsageBatchRequest dbBatch() {
		return new DbUsageBatchRequest("b6p-07", PERIOD, PERIOD.plusMinutes(5), 71, true, 72, 73,
				List.of(new DbUsageRow("U1000001", 61, 62, 63, 64)));
	}

	@Test
	void dbUsageBatch_serializesUnderComponentNames() {
		final JsonNode node = JSON.valueToTree(dbBatch());

		assertEquals(Set.of("namespace", "windowStart", "windowEnd", "deallocDelta", "statsReset",
				"entries", "entriesMax", "tenants"), keysOf(node),
				"exact key set: the entriesWithinCapacity @AssertTrue getter must not serialize");
		assertEquals(Set.of("schemaName", "calls", "execMicros", "sharedBlksRead", "sharedBlksHit"),
				keysOf(node.get("tenants").get(0)));
		assertEquals(dbBatch(), JSON.readValue(PINNED_DB_BATCH_JSON, DbUsageBatchRequest.class));
	}

	/** An omitted {@code tenants} is the heartbeat, and CBOR — the push format — carries both shapes. */
	@Test
	void dbUsageBatch_omittedTenantsBindAsEmptyAndCborRoundTrips() {
		final DbUsageBatchRequest bound = JSON.readValue("""
				{"namespace":"b6p-07","windowStart":"2026-09-07T00:00:00Z",
				 "windowEnd":"2026-09-07T00:05:00Z","deallocDelta":0,"statsReset":false,
				 "entries":0,"entriesMax":50000}
				""", DbUsageBatchRequest.class);

		assertTrue(bound.tenants().isEmpty());
		for (final DbUsageBatchRequest batch : List.of(dbBatch(), bound)) {
			assertEquals(batch, CBOR.readValue(CBOR.writeValueAsBytes(batch), DbUsageBatchRequest.class));
		}
	}

	/**
	 * The push format's exact bytes, so a key or encoding drift on CBOR fails here rather than
	 * silently mis-binding. Decoded, this is {@link #PINNED_DB_BATCH_JSON}.
	 */
	private static final String PINNED_DB_BATCH_CBOR_HEX = """
			bf696e616d657370616365666236702d30376b77696e646f77537461727474323032362d30392d30375430303a3030\
			3a30305a6977696e646f77456e6474323032362d30392d30375430303a30353a30305a6c6465616c6c6f6344656c74\
			6118476a73746174735265736574f567656e747269657318486a656e74726965734d617818496774656e616e747381\
			bf6a736368656d614e616d656855313030303030316563616c6c73183d6a657865634d6963726f73183e6e73686172\
			6564426c6b7352656164183f6d736861726564426c6b734869741840ffff\
			""";

	@Test
	void dbUsageBatch_cborBytesArePinned() {
		assertEquals(PINNED_DB_BATCH_CBOR_HEX, HexFormat.of().formatHex(CBOR.writeValueAsBytes(dbBatch())));
		assertEquals(dbBatch(), CBOR.readValue(HexFormat.of().parseHex(PINNED_DB_BATCH_CBOR_HEX),
				DbUsageBatchRequest.class));
		assertEquals(JSON.readTree(PINNED_DB_BATCH_JSON),
				CBOR.readTree(HexFormat.of().parseHex(PINNED_DB_BATCH_CBOR_HEX)));
	}

	/** The record-level {@code @AssertTrue} is a getter, and must not leak onto the wire. */
	@Test
	void dbUsageBatch_validationGetterIsNotSerialized() {
		assertFalse(JSON.valueToTree(dbBatch()).has("entriesWithinCapacity"));
		assertFalse(CBOR.readTree(CBOR.writeValueAsBytes(dbBatch())).has("entriesWithinCapacity"));
	}

	@Test
	void dbUsageBatch_nullTenantElementIsRefusedDuringBinding() {
		assertThrows(JacksonException.class, () -> JSON.readValue(
				PINNED_DB_BATCH_JSON.replace("\"tenants\":[", "\"tenants\":[null,"), DbUsageBatchRequest.class));
	}

	/** Every primitive component is required: omitting one is a binding failure, so a 400. */
	@Test
	void dbUsageBatch_omittedPrimitiveFailsBinding() {
		assertThrows(JacksonException.class, () -> JSON.readValue(
				PINNED_DB_BATCH_JSON.replace("\"statsReset\":true,", ""), DbUsageBatchRequest.class));
		assertThrows(JacksonException.class, () -> JSON.readValue(
				PINNED_DB_BATCH_JSON.replace(",\"entriesMax\":73", ""), DbUsageBatchRequest.class));
		assertThrows(JacksonException.class, () -> JSON.readValue(
				PINNED_DB_BATCH_JSON.replace(",\"execMicros\":62", ""), DbUsageBatchRequest.class));
	}
}
