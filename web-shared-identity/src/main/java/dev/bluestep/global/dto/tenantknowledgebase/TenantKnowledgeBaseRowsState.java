package dev.bluestep.global.dto.tenantknowledgebase;

import com.fasterxml.jackson.annotation.JsonCreator;

/** What web-global could say about the knowledge bases of the tenant it was asked about. */
public enum TenantKnowledgeBaseRowsState {

	/** The tenant's web deployment answered and found the tenant's schema. Its rows follow, possibly none. */
	ANSWERED,

	/** The tenant's web deployment answered that the tenant's schema is not there. No rows. */
	ABSENT,

	/** web-global has no namespace for the tenant, so nobody was asked. No rows. */
	UNMAPPED,

	/** The tenant's web deployment could not be asked or did not give a usable answer. No rows. */
	UNANSWERED,

	/** A state this reader does not know: what a state added after 6.6.0 binds as. Never sent. */
	UNKNOWN;

	/**
	 * Binds a wire value by exact constant name only, anything else as {@link #UNKNOWN}. Without this Jackson would
	 * also bind {@code 1} or {@code "1"} by ordinal.
	 *
	 * @param name the wire value
	 * @return the state
	 */
	@JsonCreator(mode = JsonCreator.Mode.DELEGATING)
	public static TenantKnowledgeBaseRowsState fromWire(String name) {
		for (TenantKnowledgeBaseRowsState state : values()) {
			if (state.name().equals(name)) {
				return state;
			}
		}
		return UNKNOWN;
	}
}
