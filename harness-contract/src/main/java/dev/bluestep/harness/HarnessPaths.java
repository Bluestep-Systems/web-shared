package dev.bluestep.harness;

/**
 * Where the harness's operations live on web's harness port, spelled once for web's controllers and web-global's
 * client. A holder of constants.
 */
public final class HarnessPaths {

	/** The handshake: {@code GET}, answered by web's negotiation filter. */
	public static final String HANDSHAKE = "/harness/contract";

	/** What every harness data path starts with; the next segment is the operation's. */
	public static final String STABLE_PREFIX = "/harness/";

	/** The segment of {@code GET /harness/tenant-knowledge-base-rows/{tenantId}} (1.0.0). */
	public static final String TENANT_KNOWLEDGE_BASE_ROWS = "tenant-knowledge-base-rows";

	/**
	 * A tenant id as the harness takes it: {@code U} and a positive number without leading zeros, as web names a
	 * tenant's schema. Anything else is a {@code 400}.
	 */
	public static final String TENANT_ID_PATTERN = "^U[1-9][0-9]{0,18}$";

	private HarnessPaths() {
	}
}
