package dev.bluestep.global.dto.aiusage;

import java.time.OffsetDateTime;

import dev.bluestep.global.dto.ai.BudgetSchedule;

/**
 * Result of an {@link AiUsageQueryRequest}: tokens consumed in the current budget
 * window alongside the configured cap, so callers can render "X of Y used".
 *
 * <p>When {@code configured} is {@code false} no {@code ai_tenant_config} row
 * matched the scope — the soft-rollout grace — so {@code maxTokenBudget},
 * {@code maxIterations}, {@code budgetSchedule} and {@code windowEnd} are all
 * {@code null} (unlimited) and {@code tokensUsed} is the cumulative lifetime
 * total. For a {@link BudgetSchedule#LIFETIME} row {@code windowEnd} is likewise
 * {@code null} (the window never closes).</p>
 */
public record AiUsageQueryResponse(
		long tokensUsed,
		Integer maxTokenBudget,
		Integer maxIterations,
		BudgetSchedule budgetSchedule,
		OffsetDateTime windowStart,
		OffsetDateTime windowEnd,
		boolean configured
) {}
