package dev.bluestep.global.dto.knowledgebase;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import dev.bluestep.global.dto.tenant.TenantActor;
import dev.bluestep.global.dto.tenant.TenantKey;
import dev.bluestep.global.dto.tenantaccess.GlobalUserKey;

import tools.jackson.core.JacksonException;
import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

/** 6.9.0: the global knowledge-base list's row, its version and connections beside the knowledge base's members. */
class KnowledgeBaseListEntryWireTest {

	private static final ObjectMapper JSON = JsonMapper.builder().build();

	private static final GlobalKnowledgeBaseId ID =
			new GlobalKnowledgeBaseId(UUID.fromString("0f8fad5b-d9cb-469f-a165-70867728950e"));

	private static final TenantActor GLOBAL_USER = new TenantActor.GlobalUser(new GlobalUserKey(222_222L, -180L));

	private static final GlobalKnowledgeBase KB = new GlobalKnowledgeBase(ID, new KnowledgeBaseName("handbook"),
			"Handbook", new LiveBranch("main"), Instant.parse("2026-10-30T12:00:00Z"),
			Instant.parse("2026-10-30T13:00:00Z"), GLOBAL_USER, null);

	private static final KnowledgeBaseTenantConnection CONNECTION = new KnowledgeBaseTenantConnection(ID,
			new TenantKey(TenantKey.CLASSID, 137_803L), Instant.parse("2026-10-31T09:00:00Z"), GLOBAL_USER);

	@Test
	void aRow_carriesTheKnowledgeBaseItsVersionAndItsConnections() throws Exception {
		GlobalKnowledgeBaseListEntry sent = GlobalKnowledgeBaseListEntry.of(KB, 7, List.of(CONNECTION));

		String json = JSON.writeValueAsString(sent);

		assertEquals(sent, JSON.readValue(json, GlobalKnowledgeBaseListEntry.class));
		assertEquals(KB, sent.knowledgeBase());
		assertTrue(json.contains("\"version\":7"), json);
		assertTrue(json.contains("\"connections\":[{\"knowledgeBase\":\"0f8fad5b-d9cb-469f-a165-70867728950e\""), json);
		assertTrue(json.contains("\"tenant\":{\"classid\":111111,\"seqnum\":137803}"), json);
		assertTrue(sent.optModifier().isEmpty());
	}

	@Test
	void aRow_bindsAsTheKnowledgeBaseForAReaderBefore690() throws Exception {
		String json = JSON.writeValueAsString(GlobalKnowledgeBaseListEntry.of(KB, 7, List.of(CONNECTION)));
		ObjectMapper lenient = JsonMapper.builder().disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES).build();

		assertEquals(KB, lenient.readValue(json, GlobalKnowledgeBase.class));
		assertEquals(JSON.writeValueAsString(KB).replaceFirst("}$", ""), json.substring(0, json.indexOf(",\"version\"")));
	}

	@Test
	void aRowWithoutConnectionsOrWithAnotherKnowledgeBasesConnection_failsBinding() {
		assertThrows(JacksonException.class, () -> JSON.readValue("""
				{"id":"0f8fad5b-d9cb-469f-a165-70867728950e","name":"handbook","title":"Handbook",
				 "liveBranch":"main","created":"2026-10-30T12:00:00Z","modified":"2026-10-30T12:00:00Z",
				 "version":1}""", GlobalKnowledgeBaseListEntry.class));
		assertThrows(IllegalArgumentException.class, () -> GlobalKnowledgeBaseListEntry.of(KB, 1, List.of(
				new KnowledgeBaseTenantConnection(new GlobalKnowledgeBaseId(UUID.randomUUID()),
						new TenantKey(TenantKey.CLASSID, 1L), Instant.EPOCH, null))));
		assertThrows(IllegalArgumentException.class, () -> GlobalKnowledgeBaseListEntry.of(KB, -1, List.of()));
	}

	@Test
	void aPaddedTitle_failsAsTheKnowledgeBaseWould() {
		assertThrows(JacksonException.class, () -> JSON.readValue("""
				{"id":"0f8fad5b-d9cb-469f-a165-70867728950e","name":"handbook","title":" Handbook",
				 "liveBranch":"main","created":"2026-10-30T12:00:00Z","modified":"2026-10-30T12:00:00Z",
				 "version":1,"connections":[]}""", GlobalKnowledgeBaseListEntry.class));
	}

	private static String row(String version) {
		return """
				{"id":"0f8fad5b-d9cb-469f-a165-70867728950e","name":"handbook","title":"Handbook",
				 "liveBranch":"main","created":"2026-10-30T12:00:00Z","modified":"2026-10-30T12:00:00Z",
				 "version":%s,"connections":[]}""".formatted(version);
	}

	/** R690 Codex: a fraction is not truncated into a valid-looking version, and only an integral number binds. */
	@Test
	void aVersionIsAnIntegralJsonNumberOnly() throws Exception {
		assertEquals(7L, JSON.readValue(row("7"), GlobalKnowledgeBaseListEntry.class).version());
		assertEquals(0L, JSON.readValue(row("0"), GlobalKnowledgeBaseListEntry.class).version());
		assertEquals(Long.MAX_VALUE, JSON.readValue(row(Long.toString(Long.MAX_VALUE)),
				GlobalKnowledgeBaseListEntry.class).version());
		for (String bad : new String[] {"7.9", "-0.5", "7.0", "1e2", "\"7\"", "true", "null", "-1",
				"9223372036854775808", "{}", "[7]"}) {
			assertThrows(JacksonException.class, () -> JSON.readValue(row(bad), GlobalKnowledgeBaseListEntry.class),
					bad);
		}
	}
}
