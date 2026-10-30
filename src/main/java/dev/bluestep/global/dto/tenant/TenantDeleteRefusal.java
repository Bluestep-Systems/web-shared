package dev.bluestep.global.dto.tenant;

import java.util.Set;

/**
 * Why a tenant could not be deleted (from 6.0.0): the body of the {@code 409} a refused
 * {@code DELETE /tenants/{classid}/{seqnum}} answers with. Nothing has been deleted when it is sent,
 * so the caller's tenant-side work (archiving the schema) has not started either.
 *
 * <p>The reasons are kinds, never rows, as for {@code ResellerDeleteRefusal}: which global user or
 * other tenant is in the way is a query an operator can run.</p>
 *
 * @param reasons every reason that applies, never empty
 */
public record TenantDeleteRefusal(Set<Reason> reasons) {

	/** Copies, so a refusal cannot change after it is built. */
	public TenantDeleteRefusal {
		reasons = Set.copyOf(reasons);
	}

	/** One thing standing in the way of a delete. */
	public enum Reason {
		/**
		 * A global user, a reseller or another tenant records this tenant as its creator or last
		 * modifier. Those references are keys the database holds to the tenant, so it cannot go first.
		 */
		NAMED_AS_ACTOR,
		/** Tenant access grants ({@code tenant_user_access}) still name the tenant. */
		HAS_GRANTS
	}
}
