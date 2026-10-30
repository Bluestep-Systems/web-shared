package dev.bluestep.global.dto.tenant;

import java.util.Optional;

import org.jspecify.annotations.Nullable;

import dev.bluestep.global.dto.tenantaccess.ResellerKey;

/**
 * One tenant, reduced to what a monolith pod keeps in memory for its hot paths: which tenants keep
 * change history, object statistics and alerts, which are global, which namespace holds each schema,
 * and each tenant's lock status (which gates whether its database connections are read-only).
 *
 * @param tenant              the key
 * @param reseller            the reseller it belongs to
 * @param namespace           the namespace whose database holds its schema
 * @param lockStatus          the monolith's {@code LockStatus} ordinal, verbatim; zero when absent
 * @param flags               the monolith's bit set, verbatim; zero when absent
 * @param enableChangeHistory as {@link TenantFeatures#enableChangeHistory()}
 * @param enableObjectStats   as {@link TenantFeatures#enableObjectStats()}
 * @param enableAlerts        as {@link TenantFeatures#enableAlerts()}
 * @param global              as {@link TenantFeatures#global()}
 */
public record TenantSnapshotEntry(
		TenantKey tenant,
		ResellerKey reseller,
		@Nullable String namespace,
		int lockStatus,
		long flags,
		boolean enableChangeHistory,
		boolean enableObjectStats,
		boolean enableAlerts,
		boolean global) {

	/** {@link #namespace}, as an {@code Optional}. */
	public Optional<String> optNamespace() {
		return Optional.ofNullable(namespace);
	}
}
