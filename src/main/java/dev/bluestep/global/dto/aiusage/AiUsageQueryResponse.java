package dev.bluestep.global.dto.aiusage;

import java.time.OffsetDateTime;
import java.util.Optional;

import dev.bluestep.global.dto.ai.BudgetSchedule;

/**
 * Result of an {@link AiUsageQueryRequest}: spend (micro-USD) consumed in the reported period
 * alongside the configured cost ceiling, so callers can render "$X of $Y used".
 *
 * <p>When {@code configured} is {@code false} no {@code ai_tenant_config} row
 * matched the scope — the soft-rollout grace — so {@code maxSpendMicros},
 * {@code maxIterations}, {@code budgetSchedule} and {@code windowEnd} are all
 * empty (unlimited) and {@code spendMicrosUsed} is the cumulative lifetime
 * total. For a {@link BudgetSchedule#LIFETIME} row {@code windowEnd} is likewise
 * empty (the window never closes). {@code windowStart} is always present — the
 * unconfigured case reports the lifetime epoch.</p>
 *
 * <p><b>{@code spendComplete} says whether the figure is exact or a floor.</b> Some turns in a window can be priced
 * without every charge they incurred being priceable — a caller too old to report a charge the current schema knows
 * how to price, or a metered key with no rate row. Those turns are recorded with a marker, and this flag is false
 * whenever the window contains one. Without it, "$X of $Y used" asserts a precision it does not have, and a spend
 * that omits a charge is indistinguishable from one where that charge was free.</p>
 *
 * <p><b>{@code explicitRange} says the period is the caller's, not the budget's.</b> When it is true,
 * {@code windowStart}/{@code windowEnd} echo the {@code from}/{@code to} that were asked for and
 * {@code spendMicrosUsed} covers exactly that period — so {@code maxSpendMicros}, which is a ceiling
 * over the <em>configured</em> window, is context rather than a denominator. Rendering "$X of $Y"
 * against an arbitrary range would compare a quarter's spend to a monthly cap; this flag is what lets
 * a caller avoid it. The ceiling is still returned, because "what is this scope's limit" is a fair
 * question to answer while reporting on any period.</p>
 *
 * @param spendMicrosUsed spend consumed in the reported period, micro-USD
 * @param maxSpendMicros  the configured ceiling, empty when unlimited
 * @param maxIterations   the configured tool-loop cap, empty when unlimited
 * @param budgetSchedule  how the window rolls, empty when unconfigured
 * @param windowStart     start of the reported period
 * @param windowEnd       end of the reported period, empty when it never closes
 * @param configured      whether an {@code ai_tenant_config} row matched the scope
 * @param spendComplete   whether every turn in the period priced in full; false makes {@code spendMicrosUsed} a floor
 * @param explicitRange   whether the period is a caller-supplied range rather than the budget window
 */
public record AiUsageQueryResponse(
		long spendMicrosUsed,
		Optional<Long> maxSpendMicros,
		Optional<Integer> maxIterations,
		Optional<BudgetSchedule> budgetSchedule,
		OffsetDateTime windowStart,
		Optional<OffsetDateTime> windowEnd,
		boolean configured,
		boolean spendComplete,
		boolean explicitRange
) {}
