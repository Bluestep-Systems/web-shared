package dev.bluestep.global.dto.usage.threshold;

import java.time.Instant;
import java.util.List;
import java.util.Objects;

import org.jspecify.annotations.Nullable;

import dev.bluestep.global.dto.usage.UsageCategory;
import dev.bluestep.global.dto.usage.UsageMetric;

/**
 * The coherence rules more than one record in this package enforces, kept in one place so the write
 * shape, the stored shape and the shapes derived from them cannot drift apart.
 */
final class ThresholdRules {

	private ThresholdRules() {}

	/**
	 * Refuses a category on a metric that has none. An absent category is always coherent: on a
	 * per-category metric it means every category together, and on any other it is the only choice.
	 *
	 * @param metric   the metric
	 * @param category the category named alongside it, or {@code null} for none
	 * @throws IllegalArgumentException when {@code category} is present and {@code metric} is not
	 *                                  reported per category
	 */
	static void requireCategoryFits(final UsageMetric metric, final @Nullable UsageCategory category) {
		if (category != null && !metric.perCategory()) {
			throw new IllegalArgumentException("metric " + metric + " has no category, but " + category
					+ " was named");
		}
	}

	/**
	 * Refuses a threshold whose parts contradict each other, and returns an unmodifiable copy of its
	 * actions.
	 *
	 * @param metric   the metric limited
	 * @param category the category narrowing it, or {@code null} for every category
	 * @param window   the window measured over
	 * @param actions  the stages, as given
	 * @return the stages, copied
	 * @throws NullPointerException     when {@code actions} is missing or holds a null
	 * @throws IllegalArgumentException when the category does not fit the metric, the window does not
	 *                                  suit the metric's aggregation, there are no actions, a level is
	 *                                  below the one before it, or more than one action breaks
	 */
	static List<UsageThresholdAction> requireCoherent(final UsageMetric metric,
			final @Nullable UsageCategory category, final UsageThresholdWindow window,
			final List<UsageThresholdAction> actions) {
		requireCategoryFits(metric, category);
		if (!window.suits(metric.aggregation())) {
			throw new IllegalArgumentException("window " + window + " does not suit " + metric + ", a "
					+ metric.aggregation() + " metric");
		}
		Objects.requireNonNull(actions, "actions");
		final List<UsageThresholdAction> copy = List.copyOf(actions);
		if (copy.isEmpty()) {
			throw new IllegalArgumentException("a threshold needs at least one action");
		}
		long previous = 0;
		int breaks = 0;
		for (int i = 0; i < copy.size(); i++) {
			final UsageThresholdAction action = copy.get(i);
			if (action.level() < previous) {
				throw new IllegalArgumentException("action " + i + " level " + action.level()
						+ " is below the level before it, " + previous);
			}
			previous = action.level();
			if (action.kind() instanceof UsageThresholdAction.Break && ++breaks > 1) {
				throw new IllegalArgumentException("action " + i + " is a second break");
			}
		}
		return copy;
	}

	/**
	 * Refuses a moment that comes before another it may not precede.
	 *
	 * @param name    the later moment's component name
	 * @param later   the moment that may not come first, or {@code null} when absent
	 * @param earlier the component name of the moment it may not precede
	 * @param floor   that moment
	 * @throws IllegalArgumentException when {@code later} is present and before {@code floor}
	 */
	static void requireNotBefore(final String name, final @Nullable Instant later, final String earlier,
			final Instant floor) {
		if (later != null && later.isBefore(floor)) {
			throw new IllegalArgumentException(name + " " + later + " is before " + earlier + " " + floor);
		}
	}
}
