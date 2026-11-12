package dev.bluestep.global.dto.usage.threshold;

import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.OptionalLong;

import org.jspecify.annotations.Nullable;

import dev.bluestep.global.dto.constraints.CodePointSize;
import dev.bluestep.global.dto.usage.StorageSample;
import dev.bluestep.global.dto.usage.UsageCategory;
import dev.bluestep.global.dto.usage.UsageMetric;

import jakarta.validation.constraints.NotBlank;

/**
 * Creating or replacing one tenant's threshold on one metric: the write shape of
 * {@link UsageThreshold}, without the id, version and timestamps web-global assigns.
 *
 * <p>Identity is {@code (schemaName, metric, category, window)}, as {@link UsageThreshold} sets out:
 * a tenant may limit one metric over a day and again over a month, but never twice over the same
 * window. Stages that belong together go in one {@code actions} list.</p>
 *
 * <p><b>The whole threshold, not a patch.</b> A replace sets every stage to exactly what is sent;
 * a stage left out is deleted.</p>
 *
 * <p><b>Create and replace are addressed differently.</b> With {@code expectedVersion} absent, the
 * request creates a threshold ({@code POST .../thresholds}), and web-global refuses it (409) if one
 * with the same identity already exists. With it present, the request replaces the threshold whose
 * {@link UsageThreshold#id()} the path names ({@code PUT .../thresholds/{id}}), and web-global
 * refuses it (409) unless the stored {@link UsageThreshold#version()} is exactly that, so a caller
 * never overwrites a threshold it has not seen. The identity fields are immutable: a replace whose
 * schema, metric, category or window differs from the stored threshold's is refused (400, "delete
 * and create").</p>
 *
 * <p>The constructor applies every rule {@link UsageThreshold} documents, so an incoherent request
 * fails binding rather than reaching the service.</p>
 *
 * @param schemaName      the canonical {@code U<seqnum>} of the tenant the threshold limits; at most
 *                        {@value StorageSample#MAX_SCHEMA_NAME_LENGTH} code points
 * @param metric          the metric limited
 * @param category        the category narrowing a per-category metric, or {@code null} for every
 *                        category together; always {@code null} on a metric with none
 * @param window          the window the metric is measured over; it must suit the metric
 * @param actions         the stages, between one and {@value UsageThreshold#MAX_ACTIONS}, in
 *                        ascending limit order
 * @param expectedVersion the stored version being replaced, or {@code null} to create
 */
public record UsageThresholdRequest(
		@NotBlank @CodePointSize(max = StorageSample.MAX_SCHEMA_NAME_LENGTH) String schemaName,
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
