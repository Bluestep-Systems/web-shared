package dev.bluestep.global.dto.aitenantconfig;

import dev.bluestep.global.dto.ai.BudgetSchedule;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

/**
 * Create/update payload for an ai_tenant_config row. {@code organizationId} and
 * {@code flag} may be null — a null acts as a wildcard against that dimension
 * (e.g. NULL flag = applies to all flags for the tenant).
 *
 * <p>{@code budgetSchedule} and {@code utcOffsetMinutes} are optional on the wire;
 * absent values default to {@link BudgetSchedule#LIFETIME} and {@code 0} in the
 * service layer. {@code utcOffsetMinutes} is bounded by {@link java.time.ZoneOffset}'s
 * valid range ({@code -1080 .. 1080}).</p>
 */
public record AiTenantConfigRequest(
		@NotBlank String schemaName,
		String organizationId,
		String flag,
		@NotNull @Positive Integer maxTokenBudget,
		@NotNull @Positive Integer maxIterations,
		BudgetSchedule budgetSchedule,
		@Min(-1080) @Max(1080) Integer utcOffsetMinutes,
		Boolean enabled
) {}
