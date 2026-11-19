package dev.bluestep.global.dto.endorsement;

/** How the Console groups the platform endorsements (from 6.16.0), in the order it shows the groups. */
public enum EndorsementGroup {

	/** The staff tiers, each implying the gates of the tiers below it. */
	STAFF_TIER,

	/** Granting endorsements and managing global users. */
	ADMINISTRATION,

	/** One capability each. */
	CAPABILITIES,

	/** Markers the monolith reads as facts about the person, or not at all; no gate. */
	MARKERS
}
