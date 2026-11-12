package dev.bluestep.global.dto.tenant;

import java.util.List;

/**
 * Every tenant, as {@link TenantSnapshotEntry}s ordered by key: one read where a monolith pod used
 * to run four set queries and a traversal of every tenant to build its in-memory maps. A pod rebuilds
 * from it when it hears a tenant changed.
 *
 * @param tenants every tenant, ordered by seqnum
 */
public record TenantSnapshotResponse(List<TenantSnapshotEntry> tenants) {

	/**
	 * Jackson binds an omitted array to {@code null} whatever this package's null-marking says, and a
	 * caller that built the list keeps a handle on it, so the list is both defaulted and copied.
	 */
	public TenantSnapshotResponse {
		tenants = tenants == null ? List.of() : List.copyOf(tenants);
	}
}
