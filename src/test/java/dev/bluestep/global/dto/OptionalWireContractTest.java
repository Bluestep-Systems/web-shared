package dev.bluestep.global.dto;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Optional;

import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.annotation.JsonInclude;

import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

import dev.bluestep.global.dto.ai.AiDenialCode;
import dev.bluestep.global.dto.ai.AiPreflightResponse;
import dev.bluestep.global.dto.ai.BudgetSchedule;
import dev.bluestep.global.dto.aitenantconfig.AiTenantConfigRequest;

/**
 * Pins the wire behaviour that makes the "no null record components" rule workable.
 *
 * <p>The rule leans on one specific Jackson property: an {@code Optional<T>} component binds to
 * {@link Optional#empty()} for both an absent key and an explicit JSON null, so replacing a nullable
 * component with an {@code Optional} is invisible to clients.</p>
 *
 * <p>Jackson 3 ({@code tools.jackson.*}) only — the family every consumer of this module runs. Jackson
 * 3 absorbed the JDK8 datatypes into core, so that property holds with <em>no module registered</em>,
 * which {@link #optionalBindsWithNoModuleRegistered()} pins. That is why, since 5.0.0, this module no
 * longer publishes {@code jackson-datatype-jdk8}: it was the Jackson 2 answer to the same problem, and
 * the last Jackson 2 mapper these DTOs met (web-global's msgpack converter) is retired. A consumer that
 * still binds them with Jackson 2 has to register that module itself, or absent and null fields bind
 * literal {@code null} into components the type system says can never be null.</p>
 */
class OptionalWireContractTest {

	/**
	 * Deliberately bare — no module registered. Mirrors what Spring Boot 4 auto-configures,
	 * and proves the Optional support is core behaviour rather than something added on.
	 */
	private static final ObjectMapper MAPPER = JsonMapper.builder().build();

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

	/**
	 * The wire shape is unchanged by the migration under the default inclusion: an empty
	 * Optional serializes as JSON null, exactly as the nullable component it replaced did.
	 * See {@link #nonNullInclusionIsTheOneSettingThatDiverges()} for the one configuration
	 * where that stops being true.
	 */
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
	 * The exact bytes, so the one-character {@code @JsonProperty} keys are pinned rather than
	 * inferred: Jackson 3 reads {@code com.fasterxml.jackson.annotation}, the one Jackson artifact
	 * this module publishes, and if that ever stopped holding the keys would silently expand back to
	 * the component names. Every value distinct from its neighbours, and the enum-valued component
	 * present, so a reordering or a renamed key cannot pass.
	 */
	@Test
	void shortKeysAreTheWire() throws Exception {
		AiPreflightResponse response =
				new AiPreflightResponse(false, "trk-3", Optional.of(AiDenialCode.BUDGET_EXCEEDED), Optional.empty());

		assertEquals("{\"a\":false,\"t\":\"trk-3\",\"d\":\"BUDGET_EXCEEDED\",\"i\":null}",
				MAPPER.writeValueAsString(response));
	}

	/**
	 * The one inclusion setting where "invisible to clients" stops holding, pinned so the
	 * boundary is discoverable before someone crosses it.
	 *
	 * <p>{@code NON_NULL} is the obvious reach for trimming payloads, and it is the wrong
	 * one here: an empty {@code Optional} is not null, so it is <em>included</em> and
	 * written as {@code null}, where the nullable component it replaced would have been
	 * omitted. {@code NON_ABSENT} is the Optional-aware equivalent and preserves the old
	 * shape. Nothing in web-global sets a non-default inclusion today — this exists so that
	 * a future {@code spring.jackson.default-property-inclusion: non_null} is a caught
	 * decision rather than a silent wire change.</p>
	 */
	@Test
	void nonNullInclusionIsTheOneSettingThatDiverges() throws Exception {
		AiPreflightResponse response =
				new AiPreflightResponse(true, "trk-1", Optional.empty(), Optional.empty());

		ObjectMapper nonNull = JsonMapper.builder()
				.changeDefaultPropertyInclusion(inclusion -> JsonInclude.Value.construct(
						JsonInclude.Include.NON_NULL, JsonInclude.Include.NON_NULL))
				.build();
		ObjectMapper nonAbsent = JsonMapper.builder()
				.changeDefaultPropertyInclusion(inclusion -> JsonInclude.Value.construct(
						JsonInclude.Include.NON_ABSENT, JsonInclude.Include.NON_ABSENT))
				.build();

		assertEquals("{\"a\":true,\"t\":\"trk-1\",\"d\":null,\"i\":null}",
				nonNull.writeValueAsString(response),
				"NON_NULL keeps empty Optionals — an empty Optional is not null");
		assertEquals("{\"a\":true,\"t\":\"trk-1\"}", nonAbsent.writeValueAsString(response),
				"NON_ABSENT is the Optional-aware setting and reproduces the pre-migration shape");
	}
}
