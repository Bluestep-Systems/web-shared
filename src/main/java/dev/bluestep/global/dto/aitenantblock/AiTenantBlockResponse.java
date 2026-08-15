package dev.bluestep.global.dto.aitenantblock;

import java.time.OffsetDateTime;
import java.util.Optional;

/**
 * Read model for an ai_tenant_block row. An empty {@code organizationId} means the
 * block covers the whole tenant; {@code reason} is empty when none was recorded.
 */
public record AiTenantBlockResponse(
		Long id,
		String schemaName,
		Optional<String> organizationId,
		Optional<String> reason,
		OffsetDateTime blockedAt) {}
