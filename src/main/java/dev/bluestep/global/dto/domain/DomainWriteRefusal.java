package dev.bluestep.global.dto.domain;

import java.util.Set;

/**
 * Why a domain create or update could not be made (from 6.0.0): the body of its {@code 409}.
 *
 * <p>The reasons are kinds, never rows: which tenant holds a host, or which reservation a prefix hits,
 * is a query an operator can run.</p>
 *
 * @param reasons every reason that applies, never empty
 */
public record DomainWriteRefusal(Set<Reason> reasons) {

	/** Copies, so a refusal cannot change after it is built. */
	public DomainWriteRefusal {
		reasons = Set.copyOf(reasons);
	}

	/** One thing standing in the way of the write. */
	public enum Reason {
		/** Another domain already has the host, compared case-insensitively. */
		HOST_TAKEN,
		/** The host's prefix is a reserved domain. */
		RESERVED,
		/** The tenant named on a create does not exist. */
		TENANT_ABSENT
	}
}
