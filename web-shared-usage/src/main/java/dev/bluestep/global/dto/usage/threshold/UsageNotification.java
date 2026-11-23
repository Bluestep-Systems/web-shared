package dev.bluestep.global.dto.usage.threshold;

import java.time.Instant;
import java.util.Objects;
import java.util.Optional;

import org.jspecify.annotations.Nullable;

import dev.bluestep.global.dto.tenantaccess.TenantUserKey;
import dev.bluestep.global.dto.usage.UsageCategory;
import dev.bluestep.global.dto.usage.UsageMetric;

/**
 * One notice web-global has queued for web's dispatcher to send: a {@link UsageThresholdAction.Notify}
 * stage tripped, on one channel that names recipients, for one recipient.
 *
 * <p>web-global queues one notification per (channel, recipient), and acknowledgements and retries
 * are per notification, so per recipient: an email that failed for one user is retried without
 * mailing the others again or sending anyone's in-app alert twice. {@link NotifyChannel#OPS} is never
 * queued here; web-global alerts its operators itself.</p>
 *
 * <p>The dispatcher runs on each namespace's task pod, one per namespace. It asks for the pending
 * notifications of the schemas its namespace serves ({@link UsageNotificationRequest}), is answered a
 * {@link UsageNotificationBatch}, sends, then reports each one's outcome in a
 * {@link UsageNotificationAckRequest}. Until then a notification stays pending and is offered
 * again.</p>
 *
 * <p>Everything the notice says is snapshotted onto it, so the dispatcher never reads the threshold
 * back: the metric, the category, the limit and what was observed, the span it was observed in, when
 * the stage tripped, and whether the circuit broke with it.</p>
 *
 * @param id          web-global's id for the notification, which the acknowledgement names
 * @param tripId      the {@link UsageTripState#id()} of the trip the notice is for
 * @param schemaName  the canonical {@code U<seqnum>} of the tenant the threshold limits
 * @param thresholdId the {@link UsageThreshold#id()} whose stage tripped
 * @param actionIndex the stage: its index in {@link UsageThreshold#actions()} of the version the trip
 *                    was evaluated against; never negative
 * @param channel     how to send the notice; always one that {@linkplain NotifyChannel#namesRecipients
 *                    names recipients}
 * @param recipient   the tenant user to send it to, one of the stage's recipients
 * @param metric      the metric limited
 * @param category    the category the threshold narrows to, or {@code null} for every category;
 *                    always {@code null} on a metric with none
 * @param observed    the value observed when the stage tripped, in the metric's unit; at least
 *                    {@code limit}
 * @param limit       the stage's limit, in the metric's unit; positive
 * @param breaks      whether the threshold's break tripped with this stage, at the same limit, so the
 *                    notice can say the usage is now refused
 * @param span        the window the value was observed in, or {@code null} exactly when
 *                    {@code metric} is a level; never starting after {@code trippedAt}
 * @param trippedAt   when the stage tripped
 */
public record UsageNotification(
		long id,
		long tripId,
		String schemaName,
		long thresholdId,
		int actionIndex,
		NotifyChannel channel,
		TenantUserKey recipient,
		UsageMetric metric,
		@Nullable UsageCategory category,
		long observed,
		long limit,
		boolean breaks,
		UsageThresholdWindow.@Nullable Span span,
		Instant trippedAt) {

	/**
	 * Refuses a missing component by name and a notice that could not be sent or does not add up.
	 *
	 * @throws NullPointerException     when {@code schemaName}, {@code channel}, {@code recipient},
	 *                                  {@code metric} or {@code trippedAt} is missing
	 * @throws IllegalArgumentException when the channel names no recipients, the category does not fit
	 *                                  the metric, {@code actionIndex} is negative, {@code limit} is
	 *                                  not positive, {@code observed} is below it, or {@code span} is
	 *                                  absent on a windowed metric, present on a level, or starts
	 *                                  after {@code trippedAt}
	 */
	public UsageNotification {
		Objects.requireNonNull(schemaName, "schemaName");
		Objects.requireNonNull(channel, "channel");
		Objects.requireNonNull(recipient, "recipient");
		Objects.requireNonNull(metric, "metric");
		Objects.requireNonNull(trippedAt, "trippedAt");
		if (!channel.namesRecipients()) {
			throw new IllegalArgumentException("channel " + channel + " is not sent by web");
		}
		ThresholdRules.requireCategoryFits(metric, category);
		ThresholdRules.requireNotNegative("actionIndex", actionIndex);
		ThresholdRules.requireReached(observed, limit);
		ThresholdRules.requireWindowFits(metric, "span", span != null);
		if (span != null) {
			ThresholdRules.requireNotBefore("trippedAt", trippedAt, "span start", span.start());
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
	public Optional<UsageThresholdWindow.Span> optSpan() {
		return Optional.ofNullable(span);
	}
}
