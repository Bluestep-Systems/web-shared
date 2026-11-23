package dev.bluestep.global.dto.endorsement;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Arrays;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** The 6.16.0 endorsement catalog. */
@DisplayName("6.16.0 platform endorsement catalog")
class PlatformEndorsementTest {

	/**
	 * web's {@code myassn.security.Endorsement} at fcc0ad68f3, in its own (alphabetical) order. A web test pins the
	 * monolith's enum to this catalog; this pins the catalog to what was read there.
	 */
	static final List<String> WEB_ENUM = List.of("AITOOLS", "AI_PRICING", "AL_TECH", "ALLOW_SCRIPT_INJECTION",
			"BLUESTEP_CORP", "CLIENT_ENDORSE", "COMMUNITYCC", "ENGINEER", "ENDPOINT", "ENDORSE", "FEEDBACK_TRIAGE",
			"FORMHISTORY", "GIT_FULL", "GIT_GENERAL", "GLOBALUSERS", "KNOWLEDGE_BASE", "LOG_VIEWER",
			"NOTBLUESTEPEMPLOYEE", "ONE_SITE_ENGINEER", "ORG_LOGS", "TABLE_PARTITIONING", "TECH", "UBERTECH");

	@Test
	@DisplayName("one constant per monolith constant, in a stable catalog order grouped as the groups are ordered")
	void catalog() {
		assertEquals(WEB_ENUM.stream().sorted().toList(),
				PlatformEndorsement.catalog().stream().map(PlatformEndorsement::id).sorted().toList());
		assertEquals(List.of("ENGINEER", "UBERTECH", "TECH", "ENDORSE", "GLOBALUSERS", "CLIENT_ENDORSE", "AITOOLS",
				"AI_PRICING", "ORG_LOGS", "LOG_VIEWER", "FEEDBACK_TRIAGE", "KNOWLEDGE_BASE", "GIT_GENERAL", "GIT_FULL",
				"ENDPOINT", "TABLE_PARTITIONING", "FORMHISTORY", "ONE_SITE_ENGINEER", "ALLOW_SCRIPT_INJECTION",
				"NOTBLUESTEPEMPLOYEE", "BLUESTEP_CORP", "AL_TECH", "COMMUNITYCC"),
				PlatformEndorsement.catalog().stream().map(PlatformEndorsement::id).toList());
		List<EndorsementGroup> groups = PlatformEndorsement.catalog().stream().map(PlatformEndorsement::group).toList();
		for (int i = 1; i < groups.size(); i++) {
			assertTrue(groups.get(i - 1).compareTo(groups.get(i)) <= 0, "grouped at " + i);
		}
		assertEquals(Arrays.asList(EndorsementGroup.values()), groups.stream().distinct().toList());
	}

	@Test
	@DisplayName("ids match exactly and case-sensitively, as the monolith matches them")
	void fromId() {
		assertEquals(Optional.of(PlatformEndorsement.ENGINEER), PlatformEndorsement.fromId("ENGINEER"));
		assertTrue(PlatformEndorsement.fromId("engineer").isEmpty());
		assertTrue(PlatformEndorsement.fromId(" ENGINEER").isEmpty());
		assertTrue(PlatformEndorsement.fromId("").isEmpty());
		for (PlatformEndorsement endorsement : PlatformEndorsement.values()) {
			assertEquals(endorsement.name(), endorsement.id());
			assertFalse(endorsement.label().isBlank());
			assertFalse(endorsement.description().isBlank());
		}
	}

	@Test
	@DisplayName("implications are the monolith's gates, in catalog order, and never self-referential")
	void impliedBy() {
		assertEquals(List.of(), PlatformEndorsement.ENGINEER.impliedBy());
		assertEquals(List.of(PlatformEndorsement.ENGINEER), PlatformEndorsement.UBERTECH.impliedBy());
		assertEquals(List.of(PlatformEndorsement.ENGINEER, PlatformEndorsement.UBERTECH),
				PlatformEndorsement.TECH.impliedBy());
		assertEquals(List.of(PlatformEndorsement.ENGINEER), PlatformEndorsement.ENDPOINT.impliedBy());
		assertEquals(List.of(PlatformEndorsement.ENGINEER, PlatformEndorsement.UBERTECH, PlatformEndorsement.TECH,
				PlatformEndorsement.ENDORSE), PlatformEndorsement.GLOBALUSERS.impliedBy());
		for (PlatformEndorsement endorsement : PlatformEndorsement.values()) {
			assertFalse(endorsement.impliedBy().contains(endorsement));
		}
		assertEquals(List.of(), PlatformEndorsement.AI_PRICING.impliedBy(), "deliberately not carried by staff");
		assertEquals(List.of(), PlatformEndorsement.ONE_SITE_ENGINEER.impliedBy());
	}
}
