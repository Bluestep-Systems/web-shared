package dev.bluestep.global.dto;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Optional;

import org.junit.jupiter.api.Test;

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
 * replacing a nullable component with an {@code Optional} is invisible to clients. That
 * property comes from {@code jackson-datatype-jdk8} being registered on the mapper — it
 * is <em>not</em> Jackson core behaviour. {@link #withoutTheModuleNullsLeakIntoRecords()}
 * pins the failure mode we get if that module ever falls off the classpath, which is the
 * reason this module declares it as an {@code api} dependency.</p>
 */
class OptionalWireContractTest {

	/** Mirrors the mapper Spring Boot auto-configures (Jdk8Module is registered from the classpath). */
	private static final ObjectMapper MAPPER = JsonMapper.builder().addModule(new Jdk8Module()).build();

	@Test
	void absentKeyBindsToEmpty() throws Exception {
		AiTenantConfigRequest request = MAPPER.readValue("""
				{"schemaName":"acme","maxIterations":5}""", AiTenantConfigRequest.class);

		assertEquals(Optional.empty(), request.organizationId());
		assertEquals(Optional.empty(), request.flag());
		assertEquals(Optional.empty(), request.maxSpendMicros());
		assertEquals(Optional.empty(), request.budgetSchedule());
		assertEquals(Optional.empty(), request.utcOffsetMinutes());
		assertEquals(Optional.empty(), request.enabled());
	}

	@Test
	void explicitJsonNullBindsToEmpty() throws Exception {
		AiTenantConfigRequest request = MAPPER.readValue("""
				{"schemaName":"acme","maxIterations":5,"organizationId":null,
				 "flag":null,"maxSpendMicros":null,"budgetSchedule":null,
				 "utcOffsetMinutes":null,"enabled":null}""", AiTenantConfigRequest.class);

		assertEquals(Optional.empty(), request.organizationId());
		assertEquals(Optional.empty(), request.maxSpendMicros());
		assertEquals(Optional.empty(), request.budgetSchedule());
	}

	@Test
	void presentValuesBind() throws Exception {
		AiTenantConfigRequest request = MAPPER.readValue("""
				{"schemaName":"acme","organizationId":"org-1","flag":"chat",
				 "maxSpendMicros":250000,"maxIterations":5,"budgetSchedule":"MONTHLY",
				 "utcOffsetMinutes":-420,"enabled":true}""", AiTenantConfigRequest.class);

		assertEquals(Optional.of("org-1"), request.organizationId());
		assertEquals(Optional.of("chat"), request.flag());
		assertEquals(Optional.of(250_000L), request.maxSpendMicros());
		assertEquals(Optional.of(BudgetSchedule.MONTHLY), request.budgetSchedule());
		assertEquals(Optional.of(-420), request.utcOffsetMinutes());
		assertEquals(Optional.of(Boolean.TRUE), request.enabled());
	}

	/**
	 * The wire shape is unchanged by the migration: an empty Optional serializes as JSON
	 * null, exactly as the nullable component it replaced did. Clients see no difference.
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
	 * Documents why {@code jackson-datatype-jdk8} is an {@code api} dependency rather than
	 * a consumer's problem. Without it a mapper does not fail fast: absent and null fields
	 * bind literal {@code null} into components the type system says can never be null, and
	 * the error only surfaces later, when a value is actually present.
	 */
	@Test
	void withoutTheModuleNullsLeakIntoRecords() throws Exception {
		ObjectMapper bare = new ObjectMapper();

		AiTenantConfigRequest silentlyNull = bare.readValue("""
				{"schemaName":"acme","maxIterations":5}""", AiTenantConfigRequest.class);
		assertEquals(null, silentlyNull.organizationId(),
				"without Jdk8Module an absent field binds null, not Optional.empty");

		assertThrows(InvalidDefinitionException.class, () -> bare.readValue("""
				{"schemaName":"acme","maxIterations":5,"organizationId":"org-1"}""",
				AiTenantConfigRequest.class),
				"without Jdk8Module a *present* value is the first thing that fails");
	}
}
