package dev.bluestep.global.dto.endorsement;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * The platform endorsements web's code knows (from 6.16.0): one constant per constant of the monolith's
 * {@code myassn.security.Endorsement} (web {@code fcc0ad68f3}, 23 ids), which a web test pins to this list.
 *
 * <p><b>The constant name is the stored id</b> ({@link #id()}), matched exactly and case-sensitively, as the monolith
 * matches it: {@code engineer} is not {@link #ENGINEER}.</p>
 *
 * <p><b>Catalog order is the declaration order, and it is stable</b>: grouped as {@link EndorsementGroup} orders the
 * groups, then as the Console lists each group. A reader that shows a held set shows it in this order; a later release
 * adds an id at the end of its group and never reorders.</p>
 *
 * <p><b>Implications are hints.</b> {@link #impliedBy()} names the endorsements that also open this one's gate in the
 * monolith ({@code EndorsementGate}: {@code CUSTOM_TECH} grants to ENGINEER, UBERTECH or TECH). Each id is still
 * stored, and granted, on its own.</p>
 *
 * <p>Labels and groups: U-PLAN §2.2 as Brendan approved it (2026-11-19); a marker with no drafted label is labelled
 * with its id. Descriptions follow the monolith's javadoc where it has one.</p>
 */
public enum PlatformEndorsement {

	ENGINEER("Engineer", EndorsementGroup.STAFF_TIER,
			"Top staff tier, above Uber tech. Also opens the Uber tech, Tech, Endpoints and Manage global users gates."),
	UBERTECH("Uber tech", EndorsementGroup.STAFF_TIER,
			"Staff tier above Tech. Also opens the Tech and Manage global users gates.", "ENGINEER"),
	TECH("Tech", EndorsementGroup.STAFF_TIER,
			"Staff tier. Also opens the Manage global users gate.", "ENGINEER", "UBERTECH"),

	ENDORSE("Grant endorsements", EndorsementGroup.ADMINISTRATION,
			"Able to add endorsements in Super > Global Users, and to read the surfaces that show them. Also opens the"
					+ " Manage global users gate."),
	GLOBALUSERS("Manage global users", EndorsementGroup.ADMINISTRATION,
			"Super > Global Users.", "ENGINEER", "UBERTECH", "TECH", "ENDORSE"),
	CLIENT_ENDORSE("Grant tenant users' endorsements", EndorsementGroup.ADMINISTRATION,
			"Setting a tenant user's platform endorsements field."),

	AITOOLS("AI tools", EndorsementGroup.CAPABILITIES,
			"The MCP transport, AI chat and the surfaces that advertise them. Honoured on global identities only."),
	AI_PRICING("AI pricing admin", EndorsementGroup.CAPABILITIES,
			"AI provider pricing admin (ai_unit_rate). Deliberately not carried by Engineer or Uber tech."),
	ORG_LOGS("Read org/script logs via AI (PHI)", EndorsementGroup.CAPABILITIES,
			"Reading on-disk organization and script logs through the AI tools. Logs can carry PHI: only for people"
					+ " whose AI client is covered for PHI."),
	LOG_VIEWER("Log viewer", EndorsementGroup.CAPABILITIES,
			"Tailing unit logs in the log viewer without being a true super."),
	FEEDBACK_TRIAGE("Feedback triage", EndorsementGroup.CAPABILITIES,
			"Triaging a tenant's feedback: an item's status, type and external reference, and answering its reporter."
					+ " Honoured on global identities only."),
	KNOWLEDGE_BASE("Tenant knowledge bases", EndorsementGroup.CAPABILITIES,
			"Managing a tenant's knowledge bases and pushing to their repos."),
	GIT_GENERAL("Tenant git repos", EndorsementGroup.CAPABILITIES,
			"A tenant's general-purpose git repos (the /git/general proxy and git management), both directions."),
	GIT_FULL("Git full", EndorsementGroup.CAPABILITIES,
			"Mounting a tenant's git repos from outside that tenant. Widens which organization, never which repos."),
	ENDPOINT("Endpoints", EndorsementGroup.CAPABILITIES, "The Endpoints gate.", "ENGINEER"),
	TABLE_PARTITIONING("Table partitioning", EndorsementGroup.CAPABILITIES,
			"Changing a form's partition configuration and running partition migrations."),
	FORMHISTORY("Edit form history", EndorsementGroup.CAPABILITIES, "Editing a Relate form's change history."),
	ONE_SITE_ENGINEER("Create One sites", EndorsementGroup.CAPABILITIES,
			"Creating a BlueStep One site. Standalone: Engineer does not imply it."),
	ALLOW_SCRIPT_INJECTION("Allow script injection", EndorsementGroup.CAPABILITIES,
			"Allows bypassing XSS protection to inject raw HTML content."),

	NOTBLUESTEPEMPLOYEE("Not a BlueStep employee", EndorsementGroup.MARKERS,
			"Marker, no gate: the person is not a BlueStep employee (read by a system field)."),
	BLUESTEP_CORP("BLUESTEP_CORP", EndorsementGroup.MARKERS, "Marker, no gate."),
	AL_TECH("AL_TECH", EndorsementGroup.MARKERS, "Marker, no gate."),
	COMMUNITYCC("COMMUNITYCC", EndorsementGroup.MARKERS, "Marker, no gate.");

	private static final Map<String, PlatformEndorsement> BY_ID = Stream.of(values())
			.collect(Collectors.toUnmodifiableMap(PlatformEndorsement::id, Function.identity()));

	private final String label;
	private final EndorsementGroup group;
	private final String description;
	private final List<String> impliedByIds;

	PlatformEndorsement(String label, EndorsementGroup group, String description, String... impliedBy) {
		this.label = label;
		this.group = group;
		this.description = description;
		this.impliedByIds = List.of(impliedBy);
	}

	/** The id as the account's {@code attribs} stores it, and as the monolith matches it: the constant's name. */
	public String id() {
		return name();
	}

	/** What the Console calls it. */
	public String label() {
		return label;
	}

	/** Which group the Console shows it in. */
	public EndorsementGroup group() {
		return group;
	}

	/** One or two sentences on what it opens. */
	public String description() {
		return description;
	}

	/** The endorsements that also open this one's gate, in catalog order; a hint, never a grant. */
	public List<PlatformEndorsement> impliedBy() {
		return impliedByIds.stream().map(PlatformEndorsement::valueOf).sorted().toList();
	}

	/** The endorsement this stored id is, matched exactly; empty for a token no constant claims. */
	public static Optional<PlatformEndorsement> fromId(String id) {
		return Optional.ofNullable(BY_ID.get(id));
	}

	/** Every endorsement, in catalog order. */
	public static List<PlatformEndorsement> catalog() {
		return List.of(values());
	}
}
