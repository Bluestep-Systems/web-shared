package dev.bluestep.global.dto.tenantaccess;

import java.util.List;

/**
 * Every grant standing against one tenant, expired ones included.
 *
 * @param tenantId the tenant
 * @param grants   its grants, by user key. Expired entries are present with {@code live: false} —
 *                 this is the administrative read, and a grant hidden because its expiry passed is
 *                 a grant nobody can find to extend or remove
 */
public record TenantGrantsResponse(String tenantId, List<TenantAccessGrantResponse> grants) {

	public TenantGrantsResponse {
		grants = List.copyOf(grants);
	}
}
