package dev.bluestep.global.dto.usage.threshold;

import java.time.Instant;
import java.util.Objects;
import java.util.Optional;

import org.jspecify.annotations.Nullable;

import dev.bluestep.global.dto.usage.UsageCategory;
import dev.bluestep.global.dto.usage.UsageMetric;

/**
 * One broken circuit: a tenant whose {@link UsageThresholdAction.Break} stage on a metric has tripped
 * and not cleared. An entry of {@link UsageBreakerList}.
 *
 * <p>The pod refuses the usage the metric measures, for the tenant, in the category named or in
 * every category when none is. A tenant may appear more than once for one metric, from thresholds
 * over different windows; each entry stands on its own, and the usage is refused while any does.</p>
 *
 * <p>{@code clearsAt} is the end of the window the breaker tripped in, when the window rolls over
 * and the breaker clears; a pod refusing usage reports it, for instance as {@code Retry-After}.
 * A breaker on a {@link UsageMetric.Aggregation#LEVEL} metric has no window and so no such time;
 * it stands until the reading falls back below the limit or a writer resets it. A consumer treats an entry whose
 * {@code clearsAt} has passed as cleared, without waiting for the next poll.</p>
 *
 * @param schemaName the canonical {@code U<seqnum>} of the tenant whose circuit is broken
 * @param metric     the metric whose threshold broke it
 * @param category   the category broken, or {@code null} for every category; always {@code null} on a
 *                   metric with none
 * @param clearsAt   the end of the window the breaker tripped in; {@code null} exactly when
 *                   {@code metric} is a level
 */
public record UsageBreaker(
		String schemaName,
		UsageMetric metric,
		@Nullable UsageCategory category,
		@Nullable Instant clearsAt) {

	/**
	 * Refuses a missing component by name, a category that does not fit the metric, and a
	 * {@code clearsAt} that does not match whether the metric has a window.
	 *
	 * @throws NullPointerException     when {@code schemaName} or {@code metric} is missing
	 * @throws IllegalArgumentException when {@code category} is named on a metric with none, or
	 *                                  {@code clearsAt} is absent on a windowed metric or present
	 *                                  on a level
	 */
	public UsageBreaker {
		Objects.requireNonNull(schemaName, "schemaName");
		Objects.requireNonNull(metric, "metric");
		ThresholdRules.requireCategoryFits(metric, category);
		ThresholdRules.requireWindowFits(metric, "clearsAt", clearsAt != null);
	}

	/**
	 * The category broken.
	 *
	 * @return the category, or empty for every category
	 */
	public Optional<UsageCategory> optCategory() {
		return Optional.ofNullable(category);
	}

	/**
	 * When the breaker's window ends and it clears.
	 *
	 * @return the time, or empty for a level breaker, which has no window
	 */
	public Optional<Instant> optClearsAt() {
		return Optional.ofNullable(clearsAt);
	}
}
