package dev.bluestep.global.dto.knowledgebase;

import java.util.Set;

/**
 * Why a global knowledge base write could not be made (from 6.1.0): the body of its {@code 409}.
 *
 * <p>Each reason is a kind of obstacle, never a row.</p>
 *
 * @param reasons every reason that applies, never empty
 */
public record GlobalKnowledgeBaseWriteRefusal(Set<Reason> reasons) {

	/** Copies, so a refusal cannot change once built. */
	public GlobalKnowledgeBaseWriteRefusal {
		reasons = Set.copyOf(reasons);
	}

	/** One thing that stands in the way of the write. */
	public enum Reason {
		/** Another global knowledge base already has the name. */
		NAME_TAKEN,
		/** The tenant a connection names does not exist. */
		TENANT_ABSENT
	}
}
