package dev.bluestep.global.dto.tenantaccess;

import java.util.List;

/**
 * The tenants one global user can reach right now.
 *
 * <p>Live grants only, because this answers the catalog's question rather than an auditor's — the
 * per-tenant read is where an expired grant is still visible.</p>
 *
 * @param user      the global user asked about
 * @param tenantIds the tenants they reach, sorted, so two calls compare equal
 */
public record UserTenantsResponse(GlobalUserKey user, List<String> tenantIds) {

	public UserTenantsResponse {
		tenantIds = List.copyOf(tenantIds);
	}
}
