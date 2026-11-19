package dev.bluestep.global.dto.knowledgebase;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import tools.jackson.core.JacksonException;
import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.MapperFeature;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

/**
 * Pins {@link Access}'s wire (6.11.0): read by hand, so no mapper setting changes it; only exactly
 * {@code {"kind":"public"}} reads as public; a kind this release does not know is {@link Access.Unknown}, written back
 * as read and refused by every request; a payload that could mean two things fails to bind.
 */
class KnowledgeBaseAccessWireTest {

	private static final ObjectMapper JSON = JsonMapper.builder().build();
	/** Every setting that could loosen a reading, at once. */
	private static final ObjectMapper LOOSE = JsonMapper.builder()
			.enable(MapperFeature.ACCEPT_CASE_INSENSITIVE_VALUES)
			.enable(MapperFeature.ACCEPT_CASE_INSENSITIVE_PROPERTIES)
			.enable(MapperFeature.ACCEPT_CASE_INSENSITIVE_ENUMS)
			.enable(DeserializationFeature.ACCEPT_SINGLE_VALUE_AS_ARRAY)
			.enable(DeserializationFeature.UNWRAP_SINGLE_VALUE_ARRAYS)
			.disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
			.build();
	/** As strict a reader as there is: an unknown member anywhere fails. */
	private static final ObjectMapper STRICT = JsonMapper.builder()
			.enable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
			.enable(DeserializationFeature.FAIL_ON_NULL_FOR_PRIMITIVES)
			.build();
	private static final List<ObjectMapper> MAPPERS = List.of(JSON, LOOSE, STRICT);

	private static final GlobalKnowledgeBaseId ID =
			new GlobalKnowledgeBaseId(UUID.fromString("0f8fad5b-d9cb-469f-a165-70867728950e"));
	private static final String KB = """
			{"id":"0f8fad5b-d9cb-469f-a165-70867728950e","name":"docs","title":"Docs","liveBranch":"main",
			 "created":"2026-11-18T00:00:00Z","modified":"2026-11-18T00:00:00Z","creator":null,"modifier":null%s}""";

	static GlobalKnowledgeBase kb(Access access) {
		Instant at = Instant.parse("2026-11-18T00:00:00Z");
		return new GlobalKnowledgeBase(ID, new KnowledgeBaseName("docs"), "Docs", LiveBranch.DEFAULT, at, at, null,
				null, access);
	}

	static Access read(ObjectMapper mapper, String access) throws Exception {
		return mapper.readValue(KB.formatted(",\"access\":" + access), GlobalKnowledgeBase.class).access();
	}

	@Test
	void eachKindIsItsTag() throws Exception {
		for (ObjectMapper mapper : MAPPERS) {
			assertEquals("{\"kind\":\"public\"}", mapper.writeValueAsString(Access.PUBLIC));
			assertEquals("{\"kind\":\"private\"}", mapper.writeValueAsString(Access.PRIVATE));
			assertTrue(mapper.writeValueAsString(kb(Access.PUBLIC)).endsWith(",\"access\":{\"kind\":\"public\"}}"));
			for (Access access : List.of(Access.PUBLIC, Access.PRIVATE)) {
				assertEquals(access, mapper.readValue(mapper.writeValueAsString(access), Access.class));
				assertEquals(kb(access), mapper.readValue(mapper.writeValueAsString(kb(access)),
						GlobalKnowledgeBase.class));
			}
		}
		assertTrue(Access.PUBLIC.isPublic());
		assertFalse(Access.PRIVATE.isPublic());
	}

	@Test
	void anOlderPeersKnowledgeBaseIsPrivate() throws Exception {
		for (String suffix : List.of("", ",\"access\":null")) {
			GlobalKnowledgeBase read = JSON.readValue(KB.formatted(suffix), GlobalKnowledgeBase.class);
			assertEquals(Access.PRIVATE, read.access());
		}
		assertEquals(Access.PRIVATE, new GlobalKnowledgeBase(ID, new KnowledgeBaseName("docs"), "Docs",
				LiveBranch.DEFAULT, Instant.EPOCH, Instant.EPOCH, null, null).access());
	}

	/**
	 * Codex round 1, finding 3: a mapper that accepts values case-insensitively still reads only the exact tags; every
	 * near miss, a known kind with extra members included, is Unknown, never public.
	 */
	@Test
	void onlyTheExactTagsAreKnownWhateverTheMapper() throws Exception {
		for (ObjectMapper mapper : MAPPERS) {
			assertEquals(Access.PUBLIC, read(mapper, "{\"kind\":\"public\"}"));
			assertEquals(Access.PRIVATE, read(mapper, "{\"kind\":\"private\"}"));
			for (String near : List.of("{\"kind\":\"PUBLIC\"}", "{\"kind\":\"Public\"}", "{\"kind\":\"public \"}",
					"{\"kind\":\"\"}", "{\"kind\":\"public\",\"extra\":1}", "{\"kind\":\"private\",\"permissions\":[]}")) {
				Access access = read(mapper, near);
				assertInstanceOf(Access.Unknown.class, access, near);
				assertFalse(access.isPublic(), near);
			}
			// A differently cased member name is not the kind: no kind at all.
			assertThrows(JacksonException.class, () -> read(mapper, "{\"Kind\":\"public\"}"));
		}
	}

	/** Codex round 1, finding 1: a newer peer's kind is held, never public, and written back exactly as read. */
	@Test
	void anUnknownKindIsHeldAndWrittenBackAsRead() throws Exception {
		String members = "{\"kind\":\"members\",\"permissions\":[{\"role\":\"reader\",\"users\":[1,2]}],"
				+ "\"note\":null,\"open\":false,\"since\":\"2027-01-01\"}";
		for (ObjectMapper mapper : MAPPERS) {
			Access access = read(mapper, members);
			Access.Unknown unknown = assertInstanceOf(Access.Unknown.class, access);
			assertEquals("members", unknown.kind());
			assertFalse(unknown.isPublic());
			assertEquals(members, mapper.writeValueAsString(access));
			String record = mapper.writeValueAsString(kb(access));
			assertTrue(record.endsWith(",\"access\":" + members + "}"), record);
			assertEquals(kb(access), mapper.readValue(record, GlobalKnowledgeBase.class));
		}
	}

	/** Codex round 1, finding 2: a second kind is an error, under every mapper, whichever order it comes in. */
	@Test
	void aSecondKindFailsToBind() {
		for (ObjectMapper mapper : MAPPERS) {
			for (String twice : List.of("{\"kind\":\"public\",\"kind\":\"members\"}",
					"{\"kind\":\"members\",\"kind\":\"public\"}", "{\"kind\":\"public\",\"kind\":\"public\"}",
					"{\"kind\":\"private\",\"x\":1,\"kind\":\"public\"}")) {
				assertThrows(JacksonException.class, () -> read(mapper, twice), twice);
			}
		}
	}

	@Test
	void aMalformedAccessFailsToBind() {
		for (ObjectMapper mapper : MAPPERS) {
			for (String bad : List.of("{}", "{\"permissions\":[]}", "{\"kind\":null}", "{\"kind\":1}",
					"{\"kind\":[\"public\"]}", "{\"kind\":{\"public\":true}}", "\"public\"", "true", "1")) {
				assertThrows(JacksonException.class, () -> read(mapper, bad), bad);
			}
		}
	}

	@Test
	void theListRowCarriesIt() throws Exception {
		GlobalKnowledgeBaseListEntry row = GlobalKnowledgeBaseListEntry.of(kb(Access.PUBLIC), 3, List.of());
		assertEquals(Access.PUBLIC, row.access());
		assertEquals(Access.PUBLIC, row.knowledgeBase().access());
		String json = JSON.writeValueAsString(row);
		assertTrue(json.contains("\"access\":{\"kind\":\"public\"}"), json);
		assertEquals(row, JSON.readValue(json, GlobalKnowledgeBaseListEntry.class));
		GlobalKnowledgeBaseListEntry older = JSON.readValue(json.replace(",\"access\":{\"kind\":\"public\"}", ""),
				GlobalKnowledgeBaseListEntry.class);
		assertEquals(Access.PRIVATE, older.access());
		// A 6.11.0 row still reads as a plain knowledge base.
		assertEquals(Access.PUBLIC, JSON.readValue(json, GlobalKnowledgeBase.class).access());
		String members = json.replace("{\"kind\":\"public\"}", "{\"kind\":\"members\",\"permissions\":[]}");
		GlobalKnowledgeBaseListEntry newer = JSON.readValue(members, GlobalKnowledgeBaseListEntry.class);
		assertInstanceOf(Access.Unknown.class, newer.access());
		assertEquals(members, JSON.writeValueAsString(newer));
	}

	@Test
	void requestsLeaveItAbsentUnlessGiven() throws Exception {
		assertTrue(JSON.readValue("{\"name\":\"docs\"}", GlobalKnowledgeBaseCreateRequest.class).optAccess().isEmpty());
		assertEquals(Access.PUBLIC, JSON.readValue("{\"name\":\"docs\",\"access\":{\"kind\":\"public\"}}",
				GlobalKnowledgeBaseCreateRequest.class).access());
		assertTrue(JSON.readValue("{\"title\":\"Docs\"}", GlobalKnowledgeBasePatchRequest.class).optAccess().isEmpty());
		assertEquals(Access.PRIVATE, JSON.readValue("{\"access\":{\"kind\":\"private\"}}",
				GlobalKnowledgeBasePatchRequest.class).access());
		assertTrue(new GlobalKnowledgeBasePatchRequest(null, "Docs", null, null).optAccess().isEmpty());
		assertTrue(new GlobalKnowledgeBaseCreateRequest(new KnowledgeBaseName("docs"), null, null, null).optAccess()
				.isEmpty());
	}

	/** An Unknown is never written: the requests refuse it, bound or built. */
	@Test
	void requestsRefuseAnUnknownKind() {
		for (ObjectMapper mapper : MAPPERS) {
			assertThrows(JacksonException.class, () -> mapper.readValue(
					"{\"name\":\"docs\",\"access\":{\"kind\":\"members\"}}", GlobalKnowledgeBaseCreateRequest.class));
			assertThrows(JacksonException.class, () -> mapper.readValue("{\"access\":{\"kind\":\"PUBLIC\"}}",
					GlobalKnowledgeBasePatchRequest.class));
		}
		Map<String, Object> raw = new LinkedHashMap<>();
		raw.put("kind", "members");
		Access unknown = new Access.Unknown("members", raw);
		assertThrows(IllegalArgumentException.class, () -> new GlobalKnowledgeBaseCreateRequest(
				new KnowledgeBaseName("docs"), null, null, null, unknown));
		assertThrows(IllegalArgumentException.class, () -> new GlobalKnowledgeBasePatchRequest(null, null, null, null,
				unknown));
		assertThrows(IllegalArgumentException.class, () -> new Access.Unknown("other", raw));
	}
}
