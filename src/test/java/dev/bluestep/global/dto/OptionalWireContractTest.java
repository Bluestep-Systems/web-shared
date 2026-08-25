package dev.bluestep.global.dto;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Optional;

import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.exc.InvalidDefinitionException;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.fasterxml.jackson.datatype.jdk8.Jdk8Module;

import dev.bluestep.global.dto.ai.AiDenialCode;
import dev.bluestep.global.dto.ai.AiPreflightResponse;
import dev.bluestep.global.dto.ai.BudgetSchedule;
import dev.bluestep.global.dto.aitenantconfig.AiTenantConfigRequest;

/**
 * Pins the wire behaviour that makes the "no null record components" rule workable.
 *
 * <p>The rule leans on one specific Jackson property: an {@code Optional<T>} component
 * binds to {@link Optional#empty()} for both an absent key and an explicit JSON null, so
 * replacing a nullable component with an {@code Optional} is invisible to clients.</p>
 *
 * <p>This class covers <b>Jackson 2</b> ({@code com.fasterxml.jackson.*}), where that
 * property is <em>not</em> core behaviour — it comes from {@code jackson-datatype-jdk8}
 * being registered on the mapper. {@link #withoutTheModuleNullsLeakIntoRecords()} pins the
 * failure mode we get if that module ever falls off the classpath, which is the reason this
 * module declares it as an {@code api} dependency. It is still load-bearing: web-global's
 * msgpack converter hand-builds a Jackson 2 mapper, and the web monolith is entirely
 * Jackson 2 until it migrates off web-shared 1.2.2.</p>
 *
 * <p>Jackson 3 ({@code tools.jackson.*}) folded the JDK8 datatypes into core and needs no
 * module at all — see {@link OptionalWireContractJackson3Test}, which also pins that the two
 * families emit an identical wire. Keep the two classes in step: a change to the shape of
 * these DTOs has to hold on both.</p>
 */
class OptionalWireContractTest {

	/**
	 * Mirrors a correctly-configured Jackson 2 mapper. Note this has to be built by hand:
	 * Boot 4 auto-configures Jackson 3 only, so a Jackson 2 mapper in a Boot 4 service is
	 * always hand-rolled and always the caller's responsibility to register the module on.
	 */
	private static final ObjectMapper MAPPER = JsonMapper.builder().addModule(new Jdk8Module()).build();

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
	 * The write side of the gap, and the one that actually bites on migration.
	 *
	 * <p>This is not hypothetical. The web monolith's {@code AiUsageGateClient} builds its
	 * msgpack mapper as {@code new ObjectMapper(new MessagePackFactory())
	 * .registerModule(new JavaTimeModule())} — {@code Jdk8Module} is absent, which is
	 * correct for 1.2.2 and fatal for 2.0.0. Because {@code preflight} is fail-closed
	 * ("callers must treat this as a hard block"), the throw pinned here would block every
	 * AI turn rather than degrade quietly.</p>
	 *
	 * <p>It fails loudly rather than emitting {@code {"present":true}} for an Optional,
	 * which is the one mercy: a consumer that forgets the module finds out immediately
	 * instead of writing a corrupt wire. Any consumer of these DTOs on Jackson 2 must
	 * register {@code Jdk8Module} on every mapper it hands them to, including
	 * hand-built ones that Spring Boot never sees.</p>
	 */
	@Test
	void withoutTheModuleSerializationFailsLoudly() {
		ObjectMapper bare = new ObjectMapper();

		InvalidDefinitionException thrown = assertThrows(InvalidDefinitionException.class,
				() -> bare.writeValueAsString(new AiPreflightResponse(
						true, "trk-1", Optional.empty(), Optional.of(4))),
				"a mapper without Jdk8Module must refuse to write these DTOs, not invent a shape for them");

		assertTrue(thrown.getMessage().contains("jackson-datatype-jdk8"),
				"the failure should name the missing module, got: " + thrown.getMessage());
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

		ObjectMapper nonNull = JsonMapper.builder().addModule(new Jdk8Module())
				.serializationInclusion(JsonInclude.Include.NON_NULL).build();
		ObjectMapper nonAbsent = JsonMapper.builder().addModule(new Jdk8Module())
				.serializationInclusion(JsonInclude.Include.NON_ABSENT).build();

		assertEquals("{\"a\":true,\"t\":\"trk-1\",\"d\":null,\"i\":null}",
				nonNull.writeValueAsString(response),
				"NON_NULL keeps empty Optionals — an empty Optional is not null");
		assertEquals("{\"a\":true,\"t\":\"trk-1\"}", nonAbsent.writeValueAsString(response),
				"NON_ABSENT is the Optional-aware setting and reproduces the pre-migration shape");
	}

	/**
	 * The read side, and why {@code jackson-datatype-jdk8} is an {@code api} dependency
	 * rather than a consumer's problem. Reading does not fail fast the way writing does:
	 * absent and null fields bind literal {@code null} into components the type system says
	 * can never be null, and the error only surfaces later, when a value is actually
	 * present. There are no compact constructors to catch it in between.
	 */
	@Test
	void withoutTheModuleNullsLeakIntoRecords() throws Exception {
		ObjectMapper bare = new ObjectMapper();

		AiTenantConfigRequest silentlyNull = bare.readValue("""
				{"tenantId":"acme","maxIterations":5}""", AiTenantConfigRequest.class);
		assertEquals(null, silentlyNull.unitId(),
				"without Jdk8Module an absent field binds null, not Optional.empty");

		assertThrows(InvalidDefinitionException.class, () -> bare.readValue("""
				{"tenantId":"acme","maxIterations":5,"unitId":"org-1"}""",
				AiTenantConfigRequest.class),
				"without Jdk8Module a *present* value is the first thing that fails");
	}
}
