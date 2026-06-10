package dev.bluestep.global.dto.aitenantblock;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Create payload for an ai_tenant_block row. A NULL {@code organizationId}
 * means the entire tenant is blocked; a non-null value scopes the block to a
 * single organization within the tenant.
 */
public record AiTenantBlockRequest(
		@NotBlank String schemaName,
		String organizationId,
		@Size(max = 500) String reason
) {}
