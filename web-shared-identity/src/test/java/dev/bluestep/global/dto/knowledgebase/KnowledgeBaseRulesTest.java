package dev.bluestep.global.dto.knowledgebase;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.UUID;
import java.util.regex.Pattern;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/**
 * The name, branch and title rules. The cases are the monolith's own ({@code KnowledgeBaseTypesTest} in
 * {@code myassn.knowledgebase}), so the two sides agree on every one.
 */
@DisplayName("global knowledge base rules")
class KnowledgeBaseRulesTest {

	@ParameterizedTest
	@ValueSource(strings = { "a", "handbook", "team-2-notes", "0" })
	void validNames_build(String name) {
		assertTrue(KnowledgeBaseName.isValid(name));
		assertEquals(name, new KnowledgeBaseName(name).value());
		assertTrue(Pattern.matches(KnowledgeBaseName.PATTERN, name), "the table's CHECK pattern agrees");
	}

	@ParameterizedTest
	@ValueSource(strings = { "", "Handbook", "-a", "a-", "a.b", "a_b", "a b", "kb/x", "..", "530021___46479" })
	void invalidNames_areRefused(String name) {
		assertFalse(KnowledgeBaseName.isValid(name));
		assertTrue(KnowledgeBaseName.parse(name).isEmpty());
		assertThrows(IllegalArgumentException.class, () -> new KnowledgeBaseName(name));
		assertFalse(Pattern.matches(KnowledgeBaseName.PATTERN, name), "the table's CHECK pattern agrees");
	}

	@Test
	void aName_isAtMost64Characters() {
		// Literal on purpose: 64 is the contract (the monolith's rule, changeset 032's varchar(64)), not
		// whatever the constant says.
		assertEquals(64, KnowledgeBaseName.MAX_LENGTH);
		assertTrue(KnowledgeBaseName.isValid("a".repeat(64)));
		assertFalse(KnowledgeBaseName.isValid("a".repeat(65)));
		assertTrue(Pattern.matches(KnowledgeBaseName.PATTERN, "a".repeat(64)));
		assertFalse(Pattern.matches(KnowledgeBaseName.PATTERN, "a".repeat(65)));
	}

	@Test
	void theRepo_isKbDashNameInTheKnowledgeBaseOrg() {
		KnowledgeBaseName name = new KnowledgeBaseName("handbook");

		assertEquals("kb-handbook", name.repoName());
		assertEquals("knowledge-base/kb-handbook", name.globalRepoPath());
	}

	@ParameterizedTest
	@ValueSource(strings = { "master", "main", "release/2026", "v1.2", "a_b", "docs/v1.2/notes", "HEADS", "lock",
			"a.locked" })
	void validBranches_build(String branch) {
		assertTrue(LiveBranch.isValid(branch));
		assertEquals(branch, new LiveBranch(branch).name());
		assertTrue(Pattern.matches(LiveBranch.CHARACTERS_PATTERN, branch), "the table's CHECK pattern agrees");
	}

	@ParameterizedTest
	@ValueSource(strings = { "", "-x", "a..b", "a//b", "a/", "a.", "a.lock", "a b", "a~1", "a:b", "/a", ".a",
			"docs/.private", "docs.lock/main", "docs/./main", "docs/../main", "HEAD" })
	void invalidBranches_areRefused(String branch) {
		assertFalse(LiveBranch.isValid(branch));
		assertTrue(LiveBranch.parse(branch).isEmpty());
		assertThrows(IllegalArgumentException.class, () -> new LiveBranch(branch));
	}

	@Test
	void aBranch_isAtMost200Characters() {
		// Literal on purpose: 200 is the contract (the monolith's rule, changeset 032's varchar(200)).
		assertEquals(200, LiveBranch.MAX_LENGTH);
		assertTrue(LiveBranch.isValid("a".repeat(200)));
		assertFalse(LiveBranch.isValid("a".repeat(201)));
		assertTrue(Pattern.matches(LiveBranch.CHARACTERS_PATTERN, "a".repeat(200)));
		assertFalse(Pattern.matches(LiveBranch.CHARACTERS_PATTERN, "a".repeat(201)));
	}

	@Test
	void theDefaultBranch_isMain() {
		assertEquals("main", LiveBranch.DEFAULT.name());
	}

	@Test
	void aTitle_isTrimmedNonBlankBoundedAndFreeOfControlCharacters() {
		assertEquals("Handbook", GlobalKnowledgeBase.title("  Handbook ").orElseThrow());
		assertTrue(GlobalKnowledgeBase.title(null).isEmpty());
		assertTrue(GlobalKnowledgeBase.title("   ").isEmpty());
		assertTrue(GlobalKnowledgeBase.title("a\nb").isEmpty());
		assertTrue(GlobalKnowledgeBase.title("a\u0085b").isEmpty());
		assertEquals("Handbook", GlobalKnowledgeBase.title("\tHandbook\n").orElseThrow());
		// Literal on purpose: 200 is the contract (the monolith's rule, changeset 032's varchar(200)).
		assertEquals(200, GlobalKnowledgeBase.MAX_TITLE_LENGTH);
		assertTrue(GlobalKnowledgeBase.title("x".repeat(200)).isPresent());
		assertTrue(GlobalKnowledgeBase.title("x".repeat(201)).isEmpty());
		assertEquals("x".repeat(200), GlobalKnowledgeBase.title(" " + "x".repeat(200) + " ").orElseThrow());
	}

	@Test
	void anId_roundTripsThroughItsText_andGarbageDoesNotParse() {
		GlobalKnowledgeBaseId id = new GlobalKnowledgeBaseId(UUID.randomUUID());

		assertEquals(id, GlobalKnowledgeBaseId.parse(id.toString()).orElseThrow());
		assertTrue(GlobalKnowledgeBaseId.parse("not-a-uuid").isEmpty());
	}

	@Test
	void anId_parsesOnlyTheCanonicalLowercaseForm() {
		String canonical = "0f8fad5b-d9cb-469f-a165-70867728950e";

		assertEquals(UUID.fromString(canonical), GlobalKnowledgeBaseId.parse(canonical).orElseThrow().value());
		assertTrue(GlobalKnowledgeBaseId.parse("1-1-1-1-1").isEmpty());
		assertTrue(GlobalKnowledgeBaseId.parse("0f8fad5bd9cb469fa16570867728950e").isEmpty());
		assertTrue(GlobalKnowledgeBaseId.parse(canonical.substring(1)).isEmpty());
		assertTrue(GlobalKnowledgeBaseId.parse(canonical + "0").isEmpty());
		assertTrue(GlobalKnowledgeBaseId.parse(" " + canonical).isEmpty());
		assertTrue(GlobalKnowledgeBaseId.parse(canonical + " ").isEmpty());
		assertTrue(GlobalKnowledgeBaseId.parse(canonical.toUpperCase(java.util.Locale.ROOT)).isEmpty());
		assertTrue(GlobalKnowledgeBaseId.parse("").isEmpty());
		assertTrue(GlobalKnowledgeBaseId.parse(null).isEmpty());
	}
}
