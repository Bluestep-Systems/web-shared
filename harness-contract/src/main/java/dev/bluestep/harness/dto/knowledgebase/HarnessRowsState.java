package dev.bluestep.harness.dto.knowledgebase;

import com.fasterxml.jackson.annotation.JsonCreator;

/** What web found for the tenant it was asked about. */
public enum HarnessRowsState {

	/**
	 * The tenant's schema is in this namespace's database. Its rows follow, possibly none: a schema whose
	 * {@code knowledge_base} table the updater has not created yet answers {@code FOUND} with no rows, since no
	 * knowledge base can exist there.
	 */
	FOUND,

	/** No such schema here: the wrong namespace, or a tenant mid-move. No rows. */
	ABSENT,

	/**
	 * A state this reader's contract does not know: what a state added after 1.0.0 binds as. Never sent by web; a
	 * reader treats it as "web answered, but not in a way I can use".
	 */
	UNKNOWN;

	/**
	 * Binds a wire value by exact constant name only, anything else as {@link #UNKNOWN}. Without this Jackson would
	 * also bind {@code 1} or {@code "1"} by ordinal, as the contract line's self-token enums found.
	 *
	 * @param name the wire value
	 * @return the state
	 */
	@JsonCreator(mode = JsonCreator.Mode.DELEGATING)
	public static HarnessRowsState fromWire(String name) {
		for (HarnessRowsState state : values()) {
			if (state.name().equals(name)) {
				return state;
			}
		}
		return UNKNOWN;
	}
}
