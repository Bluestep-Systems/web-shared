package dev.bluestep.global.dto.usage.threshold;

import java.util.Objects;
import java.util.Optional;

import org.jspecify.annotations.Nullable;

import dev.bluestep.global.dto.usage.UsageCategory;
import dev.bluestep.global.dto.usage.UsageMetric;

/**
 * One notice web-global has queued for web's dispatcher to send: a {@link UsageThresholdAction.Notify}
 * stage tripped, on one channel that names recipients, for one recipient.
 *
 * <p>web-global queues one notification per (channel, recipient), and acknowledgements and retries
 * are per notification, so per recipient: an email that failed for one user is retried without
 * mailing the others again or sending anyone's in-app alert twice. {@link NotifyChannel#OPS} is never
 * queued here; web-global alerts its operators itself. The dispatcher sends, then names the
 * notification in a {@link UsageNotificationAckRequest}; until then it stays pending and is offered
 * again.</p>
 *
 * <p>Everything the notice says is carried, so the dispatcher never reads the threshold back: the
 * metric, the category, the level and what was observed, and the window it was observed in.</p>
 *
 * @param id          web-global's id for the notification, which the acknowledgement names
 * @param schemaName  the canonical {@code U<seqnum>} of the tenant the threshold limits
 * @param thresholdId the {@link UsageThreshold#id()} whose stage tripped
 * @param actionIndex the stage: its index in {@link UsageThreshold#actions()}
 * @param channel     how to send the notice; always one that {@linkplain NotifyChannel#namesRecipients
 *                    names recipients}
 * @param recipient   the tenant user to send it to, one of the stage's recipients
 * @param metric      the metric limited
 * @param category    the category the threshold narrows to, or {@code null} for every category;
 *                    always {@code null} on a metric with none
 * @param observed    the value observed when the stage tripped, in the metric's unit; at least
 *                    {@code level}
 * @param level       the stage's level, in the metric's unit; positive
 * @param window      the window the value was observed in, or {@code null} exactly when
 *                    {@code metric} is a level
 */
public record UsageNotification(
		long id,
		String schemaName,
		long thresholdId,
		int actionIndex,
		NotifyChannel channel,
		TenantUserKey recipient,
		UsageMetric metric,
		@Nullable UsageCategory category,
		long observed,
		long level,
		UsageThresholdWindow.@Nullable Span window) {

	/**
	 * Refuses a missing component by name and a notice that could not be sent or does not add up.
	 *
	 * @throws NullPointerException     when {@code schemaName}, {@code channel}, {@code recipient} or
	 *                                  {@code metric} is missing
	 * @throws IllegalArgumentException when the channel names no recipients, the
	 *                                  category does not fit the metric, {@code actionIndex} is
	 *                                  negative, {@code level} is not positive, {@code observed} is
	 *                                  below it, or {@code window} is absent on a windowed metric or
	 *                                  present on a level
	 */
	public UsageNotification {
		Objects.requireNonNull(schemaName, "schemaName");
		Objects.requireNonNull(channel, "channel");
		Objects.requireNonNull(recipient, "recipient");
		Objects.requireNonNull(metric, "metric");
		if (!channel.namesRecipients()) {
			throw new IllegalArgumentException("channel " + channel + " is not sent by web");
		}
		ThresholdRules.requireCategoryFits(metric, category);
		if (actionIndex < 0) {
			throw new IllegalArgumentException("actionIndex " + actionIndex + " is negative");
		}
		if (level <= 0) {
			throw new IllegalArgumentException("level " + level + " is not positive");
		}
		if (observed < level) {
			throw new IllegalArgumentException("observed " + observed + " is below level " + level);
		}
		final boolean isLevel = metric.aggregation() == UsageMetric.Aggregation.LEVEL;
		if (isLevel && window != null) {
			throw new IllegalArgumentException("metric " + metric + " is a level and has no window");
		}
		if (!isLevel && window == null) {
			throw new IllegalArgumentException("metric " + metric + " is windowed, so window is required");
		}
	}

	/**
	 * The category the threshold narrows to.
	 *
	 * @return the category, or empty for every category
	 */
	public Optional<UsageCategory> optCategory() {
		return Optional.ofNullable(category);
	}

	/**
	 * The window the value was observed in.
	 *
	 * @return the window, or empty for a level metric
	 */
	public Optional<UsageThresholdWindow.Span> optWindow() {
		return Optional.ofNullable(window);
	}
}
