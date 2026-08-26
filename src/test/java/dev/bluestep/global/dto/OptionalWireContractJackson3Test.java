package dev.bluestep.global.dto;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Optional;

import org.junit.jupiter.api.Test;

import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

import dev.bluestep.global.dto.ai.AiDenialCode;
import dev.bluestep.global.dto.ai.AiPreflightResponse;
import dev.bluestep.global.dto.ai.BudgetSchedule;
import dev.bluestep.global.dto.aitenantconfig.AiTenantConfigRequest;

/**
 * The Jackson 3 half of the {@code Optional} wire contract.
 *
 * <p>Jackson 3 ({@code tools.jackson.*}) is a different artifact family from Jackson 2
 * ({@code com.fasterxml.jackson.*}), not a newer version of it — both can sit on one
 * classpath, and in web-global on Spring Boot 4 both do: Boot auto-configures a Jackson 3
 * mapper as the primary one, while a Jackson 2 mapper survives to serve msgpack. These DTOs
 * are therefore serialized by two independent implementations, and the point of this class
 * is that a client cannot tell which one served it.</p>
 *
 * <p>The load-bearing difference from {@link OptionalWireContractTest}: Jackson 3 absorbed
 * the JDK8 datatypes into core, so {@code Optional} binds with <em>no module registered</em>.
 * {@link #optionalBindsWithNoModuleRegistered()} is the direct counterpart of
 * {@code withoutTheModuleNullsLeakIntoRecords()}, which pins the opposite outcome on
 * Jackson 2 — same bare mapper, same input, different result. That asymmetry is why
 * {@code jackson-datatype-jdk8} stays an {@code api} dependency: it is still load-bearing
 * for every Jackson 2 consumer, and silently redundant for Jackson 3 ones.</p>
 */
class OptionalWireContractJackson3Test {

	/**
	 * Deliberately bare — no module registered. Mirrors what Spring Boot 4 auto-configures,
	 * and proves the Optional support is core behaviour rather than something added on.
	 */
	private static final ObjectMapper MAPPER = JsonMapper.builder().build();

	/** The Jackson 2 mapper the msgpack island still uses, for the cross-family checks. */
	private static final com.fasterxml.jackson.databind.ObjectMapper JACKSON2 =
			com.fasterxml.jackson.databind.json.JsonMapper.builder()
					.addModule(new com.fasterxml.jackson.datatype.jdk8.Jdk8Module())
					.build();

	@Test
	void optionalBindsWithNoModuleRegistered() throws Exception {
		AiTenantConfigRequest request = MAPPER.readValue("""
				{"tenantId":"acme","maxIterations":5}""", AiTenantConfigRequest.class);

		assertEquals(Optional.empty(), request.unitId(),
				"Jackson 3 folds the JDK8 datatypes into core — an absent key must bind empty, not null");
		assertEquals(Optional.empty(), request.flag());
		assertEquals(Optional.empty(), request.maxSpendMicros());
	}

	@Test
	void absentKeyBindsToEmpty() throws Exception {
		AiTenantConfigRequest request = MAPPER.readValue("""
				{"tenantId":"acme","maxIterations":5}""", AiTenantConfigRequest.class);

		assertEquals(Optional.empty(), request.unitId());
		assertEquals(Optional.empty(), request.flag());
		assertEquals(Optional.empty(), request.maxSpendMicros());
		assertEquals(Optional.empty(), request.budgetSchedule());
		assertEquals(Optional.empty(), request.utcOffsetMinutes());
		assertEquals(Optional.empty(), request.enabled());
	}

	@Test
	void explicitJsonNullBindsToEmpty() throws Exception {
		AiTenantConfigRequest request = MAPPER.readValue("""
				{"tenantId":"acme","maxIterations":5,"unitId":null,
				 "flag":null,"maxSpendMicros":null,"budgetSchedule":null,
				 "utcOffsetMinutes":null,"enabled":null}""", AiTenantConfigRequest.class);

		assertEquals(Optional.empty(), request.unitId());
		assertEquals(Optional.empty(), request.maxSpendMicros());
		assertEquals(Optional.empty(), request.budgetSchedule());
	}

	@Test
	void presentValuesBind() throws Exception {
		AiTenantConfigRequest request = MAPPER.readValue("""
				{"tenantId":"acme","unitId":"org-1","flag":"chat",
				 "maxSpendMicros":250000,"maxIterations":5,"budgetSchedule":"MONTHLY",
				 "utcOffsetMinutes":-420,"enabled":true}""", AiTenantConfigRequest.class);

		assertEquals(Optional.of("org-1"), request.unitId());
		assertEquals(Optional.of("chat"), request.flag());
		assertEquals(Optional.of(250_000L), request.maxSpendMicros());
		assertEquals(Optional.of(BudgetSchedule.MONTHLY), request.budgetSchedule());
		assertEquals(Optional.of(-420), request.utcOffsetMinutes());
		assertEquals(Optional.of(Boolean.TRUE), request.enabled());
	}

	@Test
	void emptyOptionalSerializesAsJsonNull() throws Exception {
		String json = MAPPER.writeValueAsString(
				new AiPreflightResponse(true, "trk-1", Optional.empty(), Optional.of(4)));

		assertTrue(json.contains("\"d\":null"), "empty Optional should serialize as null, got: " + json);
		assertTrue(json.contains("\"i\":4"), "present Optional should serialize unwrapped, got: " + json);
	}

	@Test
	void optionalSurvivesARoundTrip() throws Exception {
		AiPreflightResponse denied =
				new AiPreflightResponse(false, "trk-2", Optional.of(AiDenialCode.TENANT_BLOCKED), Optional.empty());

		AiPreflightResponse decoded =
				MAPPER.readValue(MAPPER.writeValueAsString(denied), AiPreflightResponse.class);

		assertEquals(denied, decoded);
		assertEquals(Optional.of(AiDenialCode.TENANT_BLOCKED), decoded.denialCode());
		assertEquals(Optional.empty(), decoded.maxIterations());
	}

	/**
	 * The {@code @JsonProperty} wire names survive the family change: Jackson 3 kept
	 * {@code com.fasterxml.jackson.annotation} as its annotation package, so the short keys
	 * ({@code "a"}, {@code "t"}, {@code "d"}, {@code "i"}) are not silently expanded back to
	 * the component names by one mapper and not the other.
	 */
	@Test
	void bothFamiliesProduceByteIdenticalJson() throws Exception {
		AiPreflightResponse response =
				new AiPreflightResponse(false, "trk-3", Optional.of(AiDenialCode.BUDGET_EXCEEDED), Optional.empty());

		assertEquals(JACKSON2.writeValueAsString(response), MAPPER.writeValueAsString(response),
				"a client must not be able to tell which Jackson family served it");
	}

	/**
	 * Phase 2 of the msgpack retirement flips the monolith's client over one service at a
	 * time, so for a window a Jackson 2 writer talks to a Jackson 3 reader and vice versa.
	 * Both directions have to round-trip or that window is a breakage.
	 */
	@Test
	void jackson2WritesAreReadableByJackson3() throws Exception {
		AiPreflightResponse original =
				new AiPreflightResponse(true, "trk-4", Optional.empty(), Optional.of(7));

		AiPreflightResponse decoded =
				MAPPER.readValue(JACKSON2.writeValueAsString(original), AiPreflightResponse.class);

		assertEquals(original, decoded);
	}

	@Test
	void jackson3WritesAreReadableByJackson2() throws Exception {
		AiPreflightResponse original =
				new AiPreflightResponse(true, "trk-5", Optional.empty(), Optional.of(7));

		AiPreflightResponse decoded =
				JACKSON2.readValue(MAPPER.writeValueAsString(original), AiPreflightResponse.class);

		assertEquals(original, decoded);
	}
}
