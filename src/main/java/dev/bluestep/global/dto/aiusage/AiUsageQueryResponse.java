package dev.bluestep.global.dto.aiusage;

import java.time.OffsetDateTime;
import java.util.Optional;

import dev.bluestep.global.dto.ai.BudgetSchedule;

/**
 * Result of an {@link AiUsageQueryRequest}: spend (micro-USD) consumed in the current
 * budget window alongside the configured cost ceiling, so callers can render "$X of $Y used".
 *
 * <p>When {@code configured} is {@code false} no {@code ai_tenant_config} row
 * matched the scope — the soft-rollout grace — so {@code maxSpendMicros},
 * {@code maxIterations}, {@code budgetSchedule} and {@code windowEnd} are all
 * empty (unlimited) and {@code spendMicrosUsed} is the cumulative lifetime
 * total. For a {@link BudgetSchedule#LIFETIME} row {@code windowEnd} is likewise
 * empty (the window never closes). {@code windowStart} is always present — the
 * unconfigured case reports the lifetime epoch.</p>
 */
public record AiUsageQueryResponse(
		long spendMicrosUsed,
		Optional<Long> maxSpendMicros,
		Optional<Integer> maxIterations,
		Optional<BudgetSchedule> budgetSchedule,
		OffsetDateTime windowStart,
		Optional<OffsetDateTime> windowEnd,
		boolean configured
) {}
