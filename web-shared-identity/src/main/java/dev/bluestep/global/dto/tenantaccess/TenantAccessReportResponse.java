package dev.bluestep.global.dto.tenantaccess;

import java.time.OffsetDateTime;

/**
 * Acknowledgement of a {@link TenantAccessReportRequest}: what web-global now holds for the tenant.
 *
 * <p>{@code recorded} is echoed rather than assumed equal to what was sent, because duplicate keys in
 * a report collapse to one row — a monolith seeing a smaller number back than it sent is looking at a
 * bug in how it assembled the list, and that is worth being able to notice.</p>
 *
 * <p>{@code reportedAt} is web-global's clock, not the caller's. Freshness is judged against it, so
 * returning it lets a tenant see the timestamp its next staleness deadline is actually measured
 * from instead of inferring one from its own clock.</p>
 *
 * @param tenantId   the tenant whose snapshot was replaced
 * @param recorded   how many distinct users the stored snapshot now holds
 * @param reportedAt when web-global recorded it
 */
public record TenantAccessReportResponse(String tenantId, int recorded, OffsetDateTime reportedAt) {}
