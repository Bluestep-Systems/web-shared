package dev.bluestep.global.dto.usage.threshold;

import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.OptionalLong;

import org.jspecify.annotations.Nullable;

import dev.bluestep.global.dto.usage.UsageCategory;
import dev.bluestep.global.dto.usage.UsageMetric;

/**
 * Creating or replacing one tenant's threshold on one metric: the write shape of
 * {@link UsageThreshold}, without the id, version and timestamps web-global assigns.
 *
 * <p>A threshold is identified by {@code (schemaName, metric, category, window)}: a tenant may limit
 * one metric over a day and again over a month, but never twice over the same window, because two
 * such rows would be one limit with its stages split. Stages that belong together go in one
 * {@code actions} list.</p>
 *
 * <p><b>The whole threshold, not a patch.</b> A replace sets every stage to exactly what is sent;
 * a stage left out is deleted.</p>
 *
 * <p><b>{@code expectedVersion} guards against a lost update.</b> Absent, the request creates the
 * threshold, and web-global refuses it if one with the same identity already exists. Present, the
 * request replaces the stored one, and web-global refuses it unless the stored {@link
 * UsageThreshold#version()} is exactly that. Either way a caller never overwrites a threshold it has
 * not seen.</p>
 *
 * <p>The constructor applies every rule {@link UsageThreshold} documents, so an incoherent request
 * fails binding rather than reaching the service.</p>
 *
 * @param schemaName      the canonical {@code U<seqnum>} of the tenant the threshold limits
 * @param metric          the metric limited
 * @param category        the category narrowing a per-category metric, or {@code null} for every
 *                        category together; always {@code null} on a metric with none
 * @param window          the window the metric is measured over; it must suit the metric
 * @param actions         the stages, at least one, in ascending level order
 * @param expectedVersion the stored version being replaced, or {@code null} to create
 */
public record UsageThresholdRequest(
		String schemaName,
		UsageMetric metric,
		@Nullable UsageCategory category,
		UsageThresholdWindow window,
		List<UsageThresholdAction> actions,
		@Nullable Long expectedVersion) {

	/**
	 * Refuses a missing component by name and an incoherent threshold, and copies the actions.
	 *
	 * @throws NullPointerException     when {@code schemaName}, {@code metric}, {@code window} or
	 *                                  {@code actions} is missing, or an action is null
	 * @throws IllegalArgumentException when a rule in {@link UsageThreshold} is broken, or
	 *                                  {@code expectedVersion} is negative
	 */
	public UsageThresholdRequest {
		Objects.requireNonNull(schemaName, "schemaName");
		Objects.requireNonNull(metric, "metric");
		Objects.requireNonNull(window, "window");
		actions = ThresholdRules.requireCoherent(metric, category, window, actions);
		if (expectedVersion != null && expectedVersion < 0) {
			throw new IllegalArgumentException("expectedVersion " + expectedVersion + " is negative");
		}
	}

	/**
	 * The category narrowing the metric.
	 *
	 * @return the category, or empty for every category together
	 */
	public Optional<UsageCategory> optCategory() {
		return Optional.ofNullable(category);
	}

	/**
	 * The stored version this request replaces.
	 *
	 * @return the version, or empty for a create
	 */
	public OptionalLong optExpectedVersion() {
		return expectedVersion == null ? OptionalLong.empty() : OptionalLong.of(expectedVersion);
	}
}
