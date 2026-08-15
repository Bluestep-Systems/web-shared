package dev.bluestep.global.dto.aitenantconfig;

import java.time.OffsetDateTime;
import java.util.Optional;

import dev.bluestep.global.dto.ai.BudgetSchedule;

/**
 * Read model for an ai_tenant_config row. Empty {@code organizationId}/{@code flag}
 * are wildcards against that dimension; an empty {@code maxSpendMicros} means the
 * scope is uncapped (metered but not gated on spend).
 */
public record AiTenantConfigResponse(
		Long id,
		String schemaName,
		Optional<String> organizationId,
		Optional<String> flag,
		Optional<Long> maxSpendMicros,
		Integer maxIterations,
		BudgetSchedule budgetSchedule,
		int utcOffsetMinutes,
		boolean enabled,
		OffsetDateTime createdAt,
		OffsetDateTime updatedAt
) {}
