package dev.bluestep.global.dto.aitenantconfig;

import java.time.OffsetDateTime;
import java.util.Optional;

import dev.bluestep.global.dto.ai.BudgetSchedule;

/**
 * Read model for an ai_tenant_config row. Empty {@code unitId}/{@code userId}/{@code flag}
 * are wildcards against that dimension; an empty {@code maxSpendMicros} means the
 * scope is uncapped (metered but not gated on spend).
 *
 * <p>See {@link AiTenantConfigRequest} for why the optional dimensions resolve unit-before-
 * user-before-flag.</p>
 */
public record AiTenantConfigResponse(
		Long id,
		String tenantId,
		Optional<String> unitId,
		Optional<String> userId,
		Optional<String> flag,
		Optional<Long> maxSpendMicros,
		Integer maxIterations,
		BudgetSchedule budgetSchedule,
		int utcOffsetMinutes,
		boolean enabled,
		OffsetDateTime createdAt,
		OffsetDateTime updatedAt
) {}
