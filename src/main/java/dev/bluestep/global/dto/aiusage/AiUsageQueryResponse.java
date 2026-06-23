package dev.bluestep.global.dto.aiusage;

import java.time.OffsetDateTime;

import dev.bluestep.global.dto.ai.BudgetSchedule;

/**
 * Result of an {@link AiUsageQueryRequest}: spend (micro-USD) consumed in the current
 * budget window alongside the configured cost ceiling, so callers can render "$X of $Y used".
 *
 * <p>When {@code configured} is {@code false} no {@code ai_tenant_config} row
 * matched the scope — the soft-rollout grace — so {@code maxSpendMicros},
 * {@code maxIterations}, {@code budgetSchedule} and {@code windowEnd} are all
 * {@code null} (unlimited) and {@code spendMicrosUsed} is the cumulative lifetime
 * total. For a {@link BudgetSchedule#LIFETIME} row {@code windowEnd} is likewise
 * {@code null} (the window never closes).</p>
 */
public record AiUsageQueryResponse(
		long spendMicrosUsed,
		Long maxSpendMicros,
		Integer maxIterations,
		BudgetSchedule budgetSchedule,
		OffsetDateTime windowStart,
		OffsetDateTime windowEnd,
		boolean configured
) {}
