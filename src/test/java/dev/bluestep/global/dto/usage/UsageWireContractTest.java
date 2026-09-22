package dev.bluestep.global.dto.usage;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.TreeSet;

import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jdk8.Jdk8Module;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;

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
			 {"schemaName":"U1000001","dbBytes":31,"fileLogicalBytes":32,"filePhysicalBytes":33}]}
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
				List.of(new StorageSample("U1000001", 31, 32, 33)));
	}

	private static StorageUsageResponse tenantResponse() {
		return new StorageUsageResponse("U1000001", DAY, DAY.plusDays(6),
				Optional.of(new StorageLevel(PERIOD, 41, 42)),
				List.of(new StorageUsageDay(DAY, 1, 43, 44, 45, 46)));
	}

	private static InternalStorageUsageResponse internalResponse() {
		return new InternalStorageUsageResponse(tenantResponse(), Optional.of(51L),
				List.of(new StoragePhysicalDay(DAY, 52, 53)));
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
		assertEquals(Set.of("schemaName", "dbBytes", "fileLogicalBytes", "filePhysicalBytes"),
				keysOf(node.get("samples").get(0)));
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

	/** The tenant shape has no physical-bytes key anywhere — not null, absent. */
	@Test
	void storageUsageResponse_pinsKeysAndCarriesNoPhysicalFigures() {
		final JsonNode node = JSON.valueToTree(tenantResponse());

		assertEquals(Set.of("schemaName", "from", "to", "latest", "days"), keysOf(node));
		assertEquals(Set.of("sampledAt", "dbBytes", "fileLogicalBytes"), keysOf(node.get("latest")));
		assertEquals(Set.of("day", "sampleCount", "maxDbBytes", "maxFileLogicalBytes", "dbByteHours",
				"fileLogicalByteHours"), keysOf(node.get("days").get(0)));
		assertEquals("2026-09-07", node.get("from").asString(), "dates are ISO strings");
		assertEquals("2026-09-13", node.get("to").asString());
		assertEquals("2026-09-07", node.get("days").get(0).get("day").asString());
	}

	@Test
	void internalStorageUsageResponse_wrapsTheTenantShape() {
		final JsonNode node = JSON.valueToTree(internalResponse());

		assertEquals(Set.of("usage", "latestFilePhysicalBytes", "physicalDays"), keysOf(node));
		assertEquals(JSON.valueToTree(tenantResponse()), node.get("usage"),
				"the wrapped usage is byte-for-byte the tenant answer");
		assertEquals(Set.of("day", "maxFilePhysicalBytes", "filePhysicalByteHours"),
				keysOf(node.get("physicalDays").get(0)));
	}

	/** The wire format producers actually push — same mapper family Spring registers. */
	@Test
	void cbor_roundTripsEveryShape() {
		assertEquals(batch(), CBOR.readValue(CBOR.writeValueAsBytes(batch()), UsageBatchRequest.class));
		assertEquals(storageBatch(), CBOR.readValue(CBOR.writeValueAsBytes(storageBatch()),
				StorageSampleBatchRequest.class));
		assertEquals(tenantResponse(), CBOR.readValue(CBOR.writeValueAsBytes(tenantResponse()),
				StorageUsageResponse.class));
		final StorageUsageResponse neverSampled =
				new StorageUsageResponse("U1000001", DAY, DAY, Optional.empty(), List.of());
		assertEquals(neverSampled, CBOR.readValue(CBOR.writeValueAsBytes(neverSampled),
				StorageUsageResponse.class));
		assertEquals(internalResponse(), CBOR.readValue(CBOR.writeValueAsBytes(internalResponse()),
				InternalStorageUsageResponse.class));
	}

	/** Either mapper family can answer the storage GET; both must write the same tree. */
	@Test
	void jackson2AndJackson3WriteTheSameStorageWire() throws Exception {
		assertEquals(JSON.readTree(JSON.writeValueAsString(tenantResponse())),
				JSON.readTree(JACKSON2.writeValueAsString(tenantResponse())));
		assertEquals(JSON.readTree(JSON.writeValueAsString(internalResponse())),
				JSON.readTree(JACKSON2.writeValueAsString(internalResponse())));
		assertEquals(internalResponse(), JACKSON2.readValue(
				JSON.writeValueAsString(internalResponse()), InternalStorageUsageResponse.class));
	}
}
