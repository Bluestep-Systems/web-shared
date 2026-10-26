package dev.bluestep.global.dto.usage.threshold;

import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

import org.jspecify.annotations.Nullable;

import dev.bluestep.global.dto.usage.UsageCategory;
import dev.bluestep.global.dto.usage.UsageMetric;

/**
 * One tenant's stored limit on one metric: what web-global evaluates, and what a reader of the
 * tenant's thresholds is shown. Written through {@link UsageThresholdRequest}.
 *
 * <p>A tenant with no threshold on a metric is unlimited on it, and nothing about that metric ever
 * notifies or breaks.</p>
 *
 * <h2>Rules, enforced by the constructor</h2>
 *
 * <ul>
 *   <li><b>The category fits the metric.</b> On a metric reported per
 *       {@link UsageCategory} ({@link UsageMetric#perCategory()}), a
 *       category narrows the limit to that lane and an absent one limits every lane together. A
 *       metric with no category, such as a storage level, takes none.</li>
 *   <li><b>The window suits the metric</b> ({@link UsageThresholdWindow#suits}): a
 *       {@link UsageMetric.Aggregation#LEVEL} metric takes {@link UsageThresholdWindow#LATEST} and
 *       nothing else; a cumulative, average or peak metric takes an hourly, daily, weekly or monthly
 *       window and never the latest reading.</li>
 *   <li><b>Between one and {@value #MAX_ACTIONS} actions, in ascending limit order.</b> Each limit is
 *       positive and none is below the one before it. Two stages may share a limit, to notify and
 *       break at the same point; they trip together, in list order.</li>
 *   <li><b>At most one break, only on a cumulative or level metric, and nothing above it.</b> Once
 *       the circuit is broken a second break could add nothing, and a stage above the break could
 *       only be reached by usage that is being refused. A peak can only rise within its window and
 *       an average is frozen once no requests dilute it, so a break on either would lock the tenant
 *       out until the window rolls over; those metrics notify only.</li>
 * </ul>
 *
 * <h2>Identity, and how a replace is addressed</h2>
 *
 * <p>A threshold is identified by {@code (schemaName, metric, category, window)}: a tenant may limit
 * one metric over a day and again over a month, but never twice over the same window, because two
 * such rows would be one limit with its stages split. A replace is addressed by {@link #id()} in the
 * path ({@code PUT .../thresholds/{id}}), and the identity fields are immutable: a replace whose body
 * changes the schema, metric, category or window is refused (400, "delete and create"), so a
 * threshold never quietly becomes a different one under its trip history. Only the stages change.</p>
 *
 * <p>A stage is addressed by its index in {@code actions} of one {@link #version()}, as
 * {@link UsageTripState} and {@link UsageNotification} do; a replace closes the threshold's open
 * trips, so a reordered stage starts its trip state over.</p>
 *
 * @param id         web-global's id for the threshold
 * @param version    the threshold's current version; a replace names it as
 *                   {@link UsageThresholdRequest#expectedVersion()}
 * @param schemaName the canonical {@code U<seqnum>} of the tenant the threshold limits
 * @param metric     the metric limited
 * @param category   the category narrowing a per-category metric, or {@code null} for every
 *                   category together; always {@code null} on a metric with none
 * @param window     the window the metric is measured over
 * @param actions    the stages, in ascending limit order
 * @param createdAt  when the threshold was created
 * @param updatedAt  when it was last replaced, or {@code createdAt} if never; never before it
 */
public record UsageThreshold(
		long id,
		long version,
		String schemaName,
		UsageMetric metric,
		@Nullable UsageCategory category,
		UsageThresholdWindow window,
		List<UsageThresholdAction> actions,
		Instant createdAt,
		Instant updatedAt) {

	/** The most stages one threshold carries. */
	public static final int MAX_ACTIONS = 20;

	/**
	 * Refuses a missing component by name and an incoherent threshold, and copies the actions.
	 *
	 * @throws NullPointerException     when {@code schemaName}, {@code metric}, {@code window},
	 *                                  {@code actions}, {@code createdAt} or {@code updatedAt} is
	 *                                  missing, or an action is null
	 * @throws IllegalArgumentException when a rule above is broken, {@code version} is negative, or
	 *                                  {@code updatedAt} is before {@code createdAt}
	 */
	public UsageThreshold {
		Objects.requireNonNull(schemaName, "schemaName");
		Objects.requireNonNull(metric, "metric");
		Objects.requireNonNull(window, "window");
		Objects.requireNonNull(createdAt, "createdAt");
		Objects.requireNonNull(updatedAt, "updatedAt");
		actions = ThresholdRules.requireCoherent(metric, category, window, actions);
		if (version < 0) {
			throw new IllegalArgumentException("version " + version + " is negative");
		}
		ThresholdRules.requireNotBefore("updatedAt", updatedAt, "createdAt", createdAt);
	}

	/**
	 * The category narrowing the metric.
	 *
	 * @return the category, or empty for every category together
	 */
	public Optional<UsageCategory> optCategory() {
		return Optional.ofNullable(category);
	}
}
