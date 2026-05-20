package dev.bluestep.global.dto.aitenantconfig;

import java.time.OffsetDateTime;

public record AiTenantConfigResponse(
		Long id,
		String schemaName,
		String organizationId,
		String flag,
		Integer maxTokenBudget,
		Integer maxIterations,
		boolean enabled,
		OffsetDateTime createdAt,
		OffsetDateTime updatedAt
) {}
