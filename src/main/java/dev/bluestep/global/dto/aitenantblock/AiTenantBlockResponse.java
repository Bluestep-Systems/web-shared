package dev.bluestep.global.dto.aitenantblock;

import java.time.OffsetDateTime;
import java.util.Optional;

/**
 * Read model for an ai_tenant_block row. An empty {@code unitId} means the
 * block covers the whole tenant; {@code reason} is empty when none was recorded.
 */
public record AiTenantBlockResponse(
		Long id,
		String tenantId,
		Optional<String> unitId,
		Optional<String> reason,
		OffsetDateTime blockedAt) {}
