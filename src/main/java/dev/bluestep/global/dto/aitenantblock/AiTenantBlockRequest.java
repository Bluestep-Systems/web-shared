package dev.bluestep.global.dto.aitenantblock;

import java.util.Optional;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Create payload for an ai_tenant_block row. An empty {@code organizationId}
 * means the entire tenant is blocked; a present value scopes the block to a
 * single organization within the tenant. {@code reason} is empty when no
 * explanation was recorded.
 */
public record AiTenantBlockRequest(
		@NotBlank String schemaName,
		Optional<String> organizationId,
		Optional<@Size(max = 500) String> reason
) {}
