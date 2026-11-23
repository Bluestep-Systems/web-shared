package dev.bluestep.global.dto.usage.threshold;

import java.time.Instant;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

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
	 *                                  suit the metric's aggregation, there are no actions or more
	 *                                  than {@value UsageThreshold#MAX_ACTIONS}, a limit is below the one
	 *                                  before it, more than one action breaks, a break is on a metric
	 *                                  that is neither cumulative nor a level, or a stage's limit is
	 *                                  above the break's
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
		if (copy.size() > UsageThreshold.MAX_ACTIONS) {
			throw new IllegalArgumentException(copy.size() + " actions is more than "
					+ UsageThreshold.MAX_ACTIONS);
		}
		long previous = 0;
		@Nullable Long breakLimit = null;
		for (int i = 0; i < copy.size(); i++) {
			final UsageThresholdAction action = copy.get(i);
			if (action.limit() < previous) {
				throw new IllegalArgumentException("action " + i + " limit " + action.limit()
						+ " is below the limit before it, " + previous);
			}
			previous = action.limit();
			if (breakLimit != null && action.limit() > breakLimit) {
				throw new IllegalArgumentException("action " + i + " limit " + action.limit()
						+ " is above the break at " + breakLimit + "; once broken the usage stops");
			}
			if (action.kind() instanceof UsageThresholdAction.Break) {
				if (breakLimit != null) {
					throw new IllegalArgumentException("action " + i + " is a second break");
				}
				requireBreakable(metric);
				breakLimit = action.limit();
			}
		}
		return copy;
	}

	/**
	 * Refuses a break on a metric that refusing usage cannot hold still. A cumulative total stops
	 * growing once its usage is refused, and a level is lowered by the tenant; a peak can only rise
	 * within its window and an average is frozen once no requests dilute it, so breaking on either
	 * would lock the tenant out until the window rolls over.
	 *
	 * @param metric the metric a break is on
	 * @throws IllegalArgumentException when {@code metric} is an average or a peak
	 */
	private static void requireBreakable(final UsageMetric metric) {
		final UsageMetric.Aggregation aggregation = metric.aggregation();
		if (aggregation != UsageMetric.Aggregation.CUMULATIVE && aggregation != UsageMetric.Aggregation.LEVEL) {
			throw new IllegalArgumentException("metric " + metric + " is a " + aggregation
					+ " metric, which cannot fall while it is broken, so breaking on it only locks the tenant out");
		}
	}

	/**
	 * Refuses a window that does not match whether the metric is measured over one.
	 *
	 * @param metric the metric
	 * @param name   the window's component name
	 * @param present whether the window was given
	 * @throws IllegalArgumentException when the window is absent on a windowed metric or present on
	 *                                  a level
	 */
	static void requireWindowFits(final UsageMetric metric, final String name, final boolean present) {
		final boolean level = metric.aggregation() == UsageMetric.Aggregation.LEVEL;
		if (level && present) {
			throw new IllegalArgumentException("metric " + metric + " is a level and has no " + name);
		}
		if (!level && !present) {
			throw new IllegalArgumentException("metric " + metric + " is windowed, so " + name + " is required");
		}
	}

	/**
	 * Refuses an observed value that does not reach a positive limit.
	 *
	 * @param observed the value observed
	 * @param limit    the limit it tripped
	 * @throws IllegalArgumentException when {@code limit} is not positive or {@code observed} is below it
	 */
	static void requireReached(final long observed, final long limit) {
		if (limit <= 0) {
			throw new IllegalArgumentException("limit " + limit + " is not positive");
		}
		if (observed < limit) {
			throw new IllegalArgumentException("observed " + observed + " is below limit " + limit);
		}
	}

	/**
	 * Refuses a negative number by name.
	 *
	 * @param name  the component name
	 * @param value its value
	 * @throws IllegalArgumentException when {@code value} is negative
	 */
	static void requireNotNegative(final String name, final long value) {
		if (value < 0) {
			throw new IllegalArgumentException(name + " " + value + " is negative");
		}
	}

	/**
	 * Refuses a list naming the same element twice, and returns an unmodifiable copy in order.
	 *
	 * @param <T>  the element type
	 * @param name the component name
	 * @param list the list, as given
	 * @return the list, copied
	 * @throws NullPointerException     when an element is null
	 * @throws IllegalArgumentException when an element appears twice
	 */
	static <T> List<T> requireDistinct(final String name, final List<T> list) {
		final List<T> copy = List.copyOf(list);
		final Set<T> seen = new HashSet<>();
		for (final T element : copy) {
			if (!seen.add(element)) {
				throw new IllegalArgumentException(name + " names " + element + " twice");
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
