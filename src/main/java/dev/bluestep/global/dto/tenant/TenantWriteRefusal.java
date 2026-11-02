package dev.bluestep.global.dto.tenant;

import java.util.Set;

/**
 * Why a tenant create or update could not be made (from 6.0.0): the body of its {@code 409}.
 *
 * <p>The reasons are kinds, never rows: a refusal reaches a person, and which other tenant holds a
 * value is a query an operator can run.</p>
 *
 * @param reasons every reason that applies, never empty
 */
public record TenantWriteRefusal(Set<Reason> reasons) {

	/** Copies, so a refusal cannot change after it is built. */
	public TenantWriteRefusal {
		reasons = Set.copyOf(reasons);
	}

	/** One thing standing in the way of the write. */
	public enum Reason {
		/** The key named on a create is already a tenant's: a retry of a create that went through. */
		KEY_TAKEN,
		/** The {@code associationId} is already another tenant's. */
		TENANT_ID_TAKEN
	}
}
