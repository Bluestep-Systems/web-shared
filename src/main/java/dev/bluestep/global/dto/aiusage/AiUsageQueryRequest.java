package dev.bluestep.global.dto.aiusage;

import jakarta.validation.constraints.NotBlank;

/**
 * Read-only usage report query. Resolves the most-specific {@code ai_tenant_config}
 * row for the (schemaName, organizationId, flag) scope, derives the current budget
 * window from that row's schedule, and sums consumed tokens within it.
 *
 * <p>{@code organizationId} and {@code flag} are optional; a null narrows nothing
 * (tenant-wide). The matched config row's own wildcards govern which usage rows
 * are summed, exactly as in authorization.</p>
 */
public record AiUsageQueryRequest(
		@NotBlank String schemaName,
		String organizationId,
		String flag
) {}
