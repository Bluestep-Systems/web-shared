package dev.bluestep.global.dto.aitenantconfig;

import java.time.OffsetDateTime;

import dev.bluestep.global.dto.ai.BudgetSchedule;

public record AiTenantConfigResponse(
		Long id,
		String schemaName,
		String organizationId,
		String flag,
		Integer maxTokenBudget,
		Integer maxIterations,
		BudgetSchedule budgetSchedule,
		int utcOffsetMinutes,
		boolean enabled,
		OffsetDateTime createdAt,
		OffsetDateTime updatedAt
) {}
