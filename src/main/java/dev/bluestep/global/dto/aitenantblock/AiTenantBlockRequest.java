package dev.bluestep.global.dto.aitenantblock;

import java.util.Optional;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Create payload for an ai_tenant_block row. An empty {@code unitId}
 * means the entire tenant is blocked; a present value scopes the block to a
 * single organization within the tenant. {@code reason} is empty when no
 * explanation was recorded.
 */
public record AiTenantBlockRequest(
		@NotBlank String tenantId,
		Optional<String> unitId,
		Optional<@Size(max = 500) String> reason
) {}
