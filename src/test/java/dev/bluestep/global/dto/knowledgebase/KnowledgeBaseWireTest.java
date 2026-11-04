package dev.bluestep.global.dto.knowledgebase;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Instant;
import java.util.Set;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import dev.bluestep.global.dto.tenant.TenantActor;
import dev.bluestep.global.dto.tenant.TenantKey;
import dev.bluestep.global.dto.tenantaccess.GlobalUserKey;
import dev.bluestep.global.dto.tenantaccess.TenantUserKey;

import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

/**
 * Pins the global knowledge base shapes' wire. web-global and every monolith build bind them from
 * different releases, so a renamed key would compile on both sides and still bind wrong across a
 * staggered deploy. The mapper is bare, as in the other wire tests.
 */
class KnowledgeBaseWireTest {

	private static final ObjectMapper JSON = JsonMapper.builder().build();

	private static final GlobalKnowledgeBaseId ID =
			new GlobalKnowledgeBaseId(UUID.fromString("0f8fad5b-d9cb-469f-a165-70867728950e"));

	private static final TenantKey TENANT = new TenantKey(TenantKey.CLASSID, 137_803L);

	private static final TenantActor GLOBAL_USER = new TenantActor.GlobalUser(new GlobalUserKey(222_222L, -180L));

	@Test
	void aKnowledgeBase_writesItsIdNameAndBranchAsBareStrings() throws Exception {
		GlobalKnowledgeBase sent = new GlobalKnowledgeBase(ID, new KnowledgeBaseName("handbook"), "Handbook",
				new LiveBranch("release/2026"), Instant.parse("2026-10-30T12:00:00Z"),
				Instant.parse("2026-10-30T13:00:00Z"), GLOBAL_USER,
				new TenantActor.TenantUser(TENANT, new TenantUserKey(222_222L, 42L)));

		String json = JSON.writeValueAsString(sent);

		assertEquals(sent, JSON.readValue(json, GlobalKnowledgeBase.class));
		assertTrue(json.contains("\"id\":\"0f8fad5b-d9cb-469f-a165-70867728950e\""), json);
		assertTrue(json.contains("\"name\":\"handbook\""), json);
		assertTrue(json.contains("\"title\":\"Handbook\""), json);
		assertTrue(json.contains("\"liveBranch\":\"release/2026\""), json);
		assertTrue(json.contains("\"creator\":{\"kind\":\"global-user\""), json);
		assertTrue(json.contains("\"modifier\":{\"kind\":\"tenant-user\""), json);
	}

	@Test
	void absentActors_bindAsAbsent() throws Exception {
		GlobalKnowledgeBase read = JSON.readValue("""
				{"id":"0f8fad5b-d9cb-469f-a165-70867728950e","name":"handbook","title":"Handbook",
				 "liveBranch":"main","created":"2026-10-30T12:00:00Z","modified":"2026-10-30T12:00:00Z",
				 "creator":null}""", GlobalKnowledgeBase.class);

		assertTrue(read.optCreator().isEmpty());
		assertTrue(read.optModifier().isEmpty());
	}

	@Test
	void aCreate_bindsWithOnlyTheName() throws Exception {
		GlobalKnowledgeBaseCreateRequest read = JSON.readValue("{\"name\":\"handbook\"}",
				GlobalKnowledgeBaseCreateRequest.class);

		assertEquals(new KnowledgeBaseName("handbook"), read.name());
		assertTrue(read.optTitle().isEmpty());
		assertTrue(read.optLiveBranch().isEmpty());
		assertTrue(read.optCreator().isEmpty());
	}

	@Test
	void anInvalidName_failsBinding() {
		assertThrows(JacksonException.class,
				() -> JSON.readValue("{\"name\":\"Hand_Book\"}", GlobalKnowledgeBaseCreateRequest.class));
	}

	@Test
	void anInvalidBranch_failsBinding() {
		assertThrows(JacksonException.class, () -> JSON.readValue(
				"{\"liveBranch\":\"a..b\"}", GlobalKnowledgeBasePatchRequest.class));
	}

	@Test
	void aPatch_roundTrips_andAnEmptyOneNamesNothing() throws Exception {
		GlobalKnowledgeBasePatchRequest sent = new GlobalKnowledgeBasePatchRequest(new KnowledgeBaseName("handbook"),
				"New title", new LiveBranch("main"), GLOBAL_USER);

		assertEquals(sent, JSON.readValue(JSON.writeValueAsString(sent), GlobalKnowledgeBasePatchRequest.class));

		GlobalKnowledgeBasePatchRequest empty = JSON.readValue("{}", GlobalKnowledgeBasePatchRequest.class);
		assertTrue(empty.optName().isEmpty());
		assertTrue(empty.optTitle().isEmpty());
		assertTrue(empty.optLiveBranch().isEmpty());
		assertTrue(empty.optActor().isEmpty());
	}

	@Test
	void aConnection_writesItsKnowledgeBaseAndTenant() throws Exception {
		KnowledgeBaseTenantConnection sent = new KnowledgeBaseTenantConnection(ID, TENANT,
				Instant.parse("2026-10-30T12:00:00Z"), null);

		String json = JSON.writeValueAsString(sent);

		assertEquals(sent, JSON.readValue(json, KnowledgeBaseTenantConnection.class));
		assertTrue(json.contains("\"knowledgeBase\":\"0f8fad5b-d9cb-469f-a165-70867728950e\""), json);
		assertTrue(json.contains("\"tenant\":{\"classid\":111111,\"seqnum\":137803}"), json);
	}

	@Test
	void aConnect_bindsItsActor() throws Exception {
		KnowledgeBaseConnectRequest read = JSON.readValue(
				"{\"actor\":{\"kind\":\"global-user\",\"user\":{\"classid\":222222,\"seqnum\":-180}}}",
				KnowledgeBaseConnectRequest.class);

		assertEquals(GLOBAL_USER, read.optActor().orElseThrow());
	}

	@Test
	void aRefusal_writesItsReasonsByName() throws Exception {
		String json = JSON.writeValueAsString(
				new GlobalKnowledgeBaseWriteRefusal(Set.of(GlobalKnowledgeBaseWriteRefusal.Reason.NAME_TAKEN)));

		assertEquals("{\"reasons\":[\"NAME_TAKEN\"]}", json);
	}

	@Test
	void anId_bindsOnlyFromTheCanonicalForm() throws Exception {
		assertEquals(ID, JSON.readValue("\"0f8fad5b-d9cb-469f-a165-70867728950e\"", GlobalKnowledgeBaseId.class));
		assertThrows(JacksonException.class, () -> JSON.readValue("\"1-1-1-1-1\"", GlobalKnowledgeBaseId.class));
		assertThrows(JacksonException.class,
				() -> JSON.readValue("\"0F8FAD5B-D9CB-469F-A165-70867728950E\"", GlobalKnowledgeBaseId.class));
		assertThrows(JacksonException.class,
				() -> JSON.readValue("\"0f8fad5bd9cb469fa16570867728950e\"", GlobalKnowledgeBaseId.class));
	}

	private static GlobalKnowledgeBase withTitle(String title) {
		return new GlobalKnowledgeBase(ID, new KnowledgeBaseName("handbook"), title, new LiveBranch("main"),
				Instant.parse("2026-10-30T12:00:00Z"), Instant.parse("2026-10-30T12:00:00Z"), null, null);
	}

	@Test
	void aTitle_thatItsOwnRuleWouldChange_isRefusedAtConstruction() {
		assertThrows(IllegalArgumentException.class, () -> withTitle(" Handbook"));
		assertThrows(IllegalArgumentException.class, () -> withTitle("Handbook\t"));
		assertThrows(IllegalArgumentException.class, () -> withTitle(""));
		assertThrows(IllegalArgumentException.class, () -> withTitle("   "));
		assertThrows(IllegalArgumentException.class, () -> withTitle("x".repeat(201)));
		assertThrows(IllegalArgumentException.class, () -> withTitle("a\nb"));
		assertThrows(IllegalArgumentException.class, () -> withTitle("a\u0085b"));
		assertThrows(NullPointerException.class, () -> withTitle(null));
		assertEquals("x".repeat(200), withTitle("x".repeat(200)).title());
	}

	@Test
	void aBadTitle_failsBinding() {
		String template = """
				{"id":"0f8fad5b-d9cb-469f-a165-70867728950e","name":"handbook","title":"%s",
				 "liveBranch":"main","created":"2026-10-30T12:00:00Z","modified":"2026-10-30T12:00:00Z"}""";

		assertThrows(JacksonException.class,
				() -> JSON.readValue(template.formatted(" padded"), GlobalKnowledgeBase.class));
		assertThrows(JacksonException.class,
				() -> JSON.readValue(template.formatted("x".repeat(201)), GlobalKnowledgeBase.class));
		assertThrows(JacksonException.class,
				() -> JSON.readValue(template.formatted("a\\tb"), GlobalKnowledgeBase.class));
		assertEquals("x".repeat(200),
				JSON.readValue(template.formatted("x".repeat(200)), GlobalKnowledgeBase.class).title());
	}
}
