package dev.bluestep.global.dto;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Optional;
import java.util.OptionalInt;
import java.util.OptionalLong;

import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.annotation.JsonInclude;

import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

import dev.bluestep.global.dto.ai.AiDenialCode;
import dev.bluestep.global.dto.ai.AiPreflightResponse;
import dev.bluestep.global.dto.ai.BudgetSchedule;
import dev.bluestep.global.dto.aitenantconfig.AiTenantConfigRequest;

/**
 * Pins the wire behaviour of an optional record component.
 *
 * <p>An optional component is a {@code @Nullable} value read through an {@code optX()} accessor
 * (6.0.0; before it the component itself was {@code Optional<T>}). Both spellings bind an absent key
 * and an explicit JSON null as absent, and both write an absent value as JSON null under the default
 * inclusion, so the move is invisible to clients. This class pins that against a bare Jackson 3 mapper
 * ({@code tools.jackson.*}), the family every consumer of this module runs.</p>
 */
class OptionalWireContractTest {

	/**
	 * Deliberately bare — no module registered. Mirrors what Spring Boot 4 auto-configures.
	 */
	private static final ObjectMapper MAPPER = JsonMapper.builder().build();

	@Test
	void optionalBindsWithNoModuleRegistered() throws Exception {
		AiTenantConfigRequest request = MAPPER.readValue("""
				{"tenantId":"acme","maxIterations":5}""", AiTenantConfigRequest.class);

		assertEquals(Optional.empty(), request.optUnitId(), "an absent key must bind absent");
		assertEquals(Optional.empty(), request.optFlag());
		assertEquals(OptionalLong.empty(), request.optMaxSpendMicros());
	}

	@Test
	void absentKeyBindsToEmpty() throws Exception {
		AiTenantConfigRequest request = MAPPER.readValue("""
				{"tenantId":"acme","maxIterations":5}""", AiTenantConfigRequest.class);

		assertEquals(Optional.empty(), request.optUnitId());
		assertEquals(Optional.empty(), request.optFlag());
		assertEquals(OptionalLong.empty(), request.optMaxSpendMicros());
		assertEquals(Optional.empty(), request.optBudgetSchedule());
		assertEquals(OptionalInt.empty(), request.optUtcOffsetMinutes());
		assertEquals(Optional.empty(), request.optEnabled());
	}

	@Test
	void explicitJsonNullBindsToEmpty() throws Exception {
		AiTenantConfigRequest request = MAPPER.readValue("""
				{"tenantId":"acme","maxIterations":5,"unitId":null,
				 "flag":null,"maxSpendMicros":null,"budgetSchedule":null,
				 "utcOffsetMinutes":null,"enabled":null}""", AiTenantConfigRequest.class);

		assertEquals(Optional.empty(), request.optUnitId());
		assertEquals(OptionalLong.empty(), request.optMaxSpendMicros());
		assertEquals(Optional.empty(), request.optBudgetSchedule());
	}

	@Test
	void presentValuesBind() throws Exception {
		AiTenantConfigRequest request = MAPPER.readValue("""
				{"tenantId":"acme","unitId":"org-1","flag":"chat",
				 "maxSpendMicros":250000,"maxIterations":5,"budgetSchedule":"MONTHLY",
				 "utcOffsetMinutes":-420,"enabled":true}""", AiTenantConfigRequest.class);

		assertEquals(Optional.of("org-1"), request.optUnitId());
		assertEquals(Optional.of("chat"), request.optFlag());
		assertEquals(OptionalLong.of(250_000L), request.optMaxSpendMicros());
		assertEquals(Optional.of(BudgetSchedule.MONTHLY), request.optBudgetSchedule());
		assertEquals(OptionalInt.of(-420), request.optUtcOffsetMinutes());
		assertEquals(Optional.of(Boolean.TRUE), request.optEnabled());
	}

	/**
	 * The wire shape is unchanged under the default inclusion: an absent value serializes as JSON
	 * null, exactly as the empty {@code Optional} component of 5.x did.
	 */
	@Test
	void emptyOptionalSerializesAsJsonNull() throws Exception {
		String json = MAPPER.writeValueAsString(new AiPreflightResponse(true, "trk-1", null, 4));

		assertTrue(json.contains("\"d\":null"), "an absent value should serialize as null, got: " + json);
		assertTrue(json.contains("\"i\":4"), "a present value should serialize unwrapped, got: " + json);
	}

	@Test
	void optionalSurvivesARoundTrip() throws Exception {
		AiPreflightResponse denied = new AiPreflightResponse(false, "trk-2", AiDenialCode.TENANT_BLOCKED, null);

		AiPreflightResponse decoded =
				MAPPER.readValue(MAPPER.writeValueAsString(denied), AiPreflightResponse.class);

		assertEquals(denied, decoded);
		assertEquals(Optional.of(AiDenialCode.TENANT_BLOCKED), decoded.optDenialCode());
		assertEquals(OptionalInt.empty(), decoded.optMaxIterations());
		assertNull(decoded.maxIterations());
	}

	/**
	 * The exact bytes, so the one-character {@code @JsonProperty} keys are pinned rather than
	 * inferred: Jackson 3 reads {@code com.fasterxml.jackson.annotation}, the one Jackson artifact
	 * this module publishes, and if that ever stopped holding the keys would silently expand back to
	 * the component names. Every value distinct from its neighbours, and the enum-valued component
	 * present, so a reordering or a renamed key cannot pass.
	 */
	@Test
	void shortKeysAreTheWire() throws Exception {
		AiPreflightResponse response = new AiPreflightResponse(false, "trk-3", AiDenialCode.BUDGET_EXCEEDED, null);

		assertEquals("{\"a\":false,\"t\":\"trk-3\",\"d\":\"BUDGET_EXCEEDED\",\"i\":null}",
				MAPPER.writeValueAsString(response));
	}

	/**
	 * Under {@code NON_NULL} and {@code NON_ABSENT} an absent value is omitted. With the 5.x
	 * {@code Optional} components {@code NON_NULL} kept an empty {@code Optional} and wrote it as
	 * {@code null}; a nullable component is plain null, so the two settings now agree. Nothing in
	 * web-global sets a non-default inclusion; this pins the behaviour so a future
	 * {@code spring.jackson.default-property-inclusion} is a caught decision.
	 */
	@Test
	void nonNullAndNonAbsentInclusionBothOmitAnAbsentValue() throws Exception {
		AiPreflightResponse response = new AiPreflightResponse(true, "trk-1", null, null);

		ObjectMapper nonNull = JsonMapper.builder()
				.changeDefaultPropertyInclusion(inclusion -> JsonInclude.Value.construct(
						JsonInclude.Include.NON_NULL, JsonInclude.Include.NON_NULL))
				.build();
		ObjectMapper nonAbsent = JsonMapper.builder()
				.changeDefaultPropertyInclusion(inclusion -> JsonInclude.Value.construct(
						JsonInclude.Include.NON_ABSENT, JsonInclude.Include.NON_ABSENT))
				.build();

		assertEquals("{\"a\":true,\"t\":\"trk-1\"}", nonNull.writeValueAsString(response));
		assertEquals("{\"a\":true,\"t\":\"trk-1\"}", nonAbsent.writeValueAsString(response));
	}
}
