package dev.bluestep.global.dto.usage;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.TreeSet;

import org.junit.jupiter.api.Test;

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
 * <p>Jackson 3 only: both consumers bind these records through Boot 4's Jackson 3 mappers, JSON for
 * reads and CBOR for the pushes.</p>
 */
class UsageWireContractTest {

	private static final ObjectMapper JSON = JsonMapper.builder().build();
	private static final ObjectMapper CBOR = CBORMapper.builder().build();

	/** Every value distinct, so a swapped pair of keys cannot cancel out. */
	private static final String PINNED_WINDOW_JSON = """
			{"t":1788696900000,"s":"U1000001","c":"http","h":11,"p":12,"q":13,"u":14,
			 "d":15,"a":16,"i":17,"j":18,"o":19,"x":20}
			""";

	private static final String PINNED_BATCH_JSON = """
			{"b":"a3a189c2-3a9e-4f6b-9be1-0d0a54c9a11f","n":"b6p-07","w":[%s]}
			""".formatted(PINNED_WINDOW_JSON);

	private static final String PINNED_STORAGE_BATCH_JSON = """
			{"namespace":"b6p-07","sampledAt":1788652800000,"samples":[
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
		return new StorageSampleBatchRequest("b6p-07", 1_788_652_800_000L,
				List.of(new StorageSample("U1000001", 31, 32, 33)));
	}

	private static StorageUsageResponse storageResponse(final boolean internal) {
		final Optional<Long> physical = internal ? Optional.of(43L) : Optional.empty();
		final Optional<Long> physicalHours = internal ? Optional.of(46L) : Optional.empty();
		return new StorageUsageResponse("U1000001",
				Optional.of(new StorageLevel(OffsetDateTime.of(2026, 9, 7, 0, 0, 0, 0, ZoneOffset.UTC),
						41, 42, physical)),
				List.of(new StorageUsageDay(LocalDate.of(2026, 9, 7), 41, 42, 44, 45,
						physical, physicalHours)));
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

	/**
	 * An omitted {@code samples} must reach Bean Validation as the empty list — a throw during
	 * binding would surface as a 400 that names no field.
	 */
	@Test
	void omittedSamples_bindAsEmpty() {
		final StorageSampleBatchRequest bound = JSON.readValue(
				"{\"namespace\":\"b6p-07\",\"sampledAt\":1788652800000}", StorageSampleBatchRequest.class);

		assertTrue(bound.samples().isEmpty());
	}

	@Test
	void storageUsageResponse_pinsKeysAndDateShapes() {
		final JsonNode node = JSON.valueToTree(storageResponse(true));

		assertEquals(Set.of("schemaName", "latest", "days"), keysOf(node));
		assertEquals(Set.of("sampledAt", "dbBytes", "fileLogicalBytes", "filePhysicalBytes"),
				keysOf(node.get("latest")));
		assertEquals(Set.of("day", "maxDbBytes", "maxFileLogicalBytes", "dbByteHours",
				"fileLogicalByteHours", "maxFilePhysicalBytes", "filePhysicalByteHours"),
				keysOf(node.get("days").get(0)));
		assertEquals("2026-09-07", node.get("days").get(0).get("day").asString(),
				"a day is an ISO date string, not an epoch number or an array");
		assertEquals(43, node.get("latest").get("filePhysicalBytes").asLong());
	}

	/**
	 * The tenant-facing shape: physical figures are absent, never zero, and survive a round trip
	 * as absent.
	 */
	@Test
	void storageUsageResponse_tenantShapeCarriesNoPhysicalFigures() {
		final StorageUsageResponse tenant = storageResponse(false);
		final JsonNode node = JSON.valueToTree(tenant);

		assertTrue(node.get("latest").get("filePhysicalBytes").isNull());
		assertTrue(node.get("days").get(0).get("maxFilePhysicalBytes").isNull());
		assertTrue(node.get("days").get(0).get("filePhysicalByteHours").isNull());
		final StorageUsageResponse decoded =
				JSON.readValue(JSON.writeValueAsString(tenant), StorageUsageResponse.class);
		assertEquals(tenant, decoded);
		assertFalse(decoded.latest().orElseThrow().filePhysicalBytes().isPresent());
	}

	/** The wire format producers actually push — same mapper family Spring registers. */
	@Test
	void cbor_roundTripsEveryShape() {
		assertEquals(batch(), CBOR.readValue(CBOR.writeValueAsBytes(batch()), UsageBatchRequest.class));
		assertEquals(storageBatch(), CBOR.readValue(CBOR.writeValueAsBytes(storageBatch()),
				StorageSampleBatchRequest.class));
		assertEquals(storageResponse(true), CBOR.readValue(CBOR.writeValueAsBytes(storageResponse(true)),
				StorageUsageResponse.class));
	}
}
