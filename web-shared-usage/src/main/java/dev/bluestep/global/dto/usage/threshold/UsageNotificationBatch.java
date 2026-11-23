package dev.bluestep.global.dto.usage.threshold;

import java.time.Instant;
import java.util.List;
import java.util.Objects;

/**
 * The pending {@link UsageNotification}s of the schemas a {@link UsageNotificationRequest} named:
 * what web's dispatcher sends next.
 *
 * <p>An envelope rather than a bare list, as {@link UsageBreakerList} is, so the answer can say when
 * it was read and later grow without reshaping. Every notification here is still pending: offered
 * again until its outcome is reported in a {@link UsageNotificationAckRequest}.</p>
 *
 * @param generatedAt   when web-global read the pending notifications
 * @param notifications the pending notifications, in the order web-global queued them; empty when
 *                      there are none
 */
public record UsageNotificationBatch(Instant generatedAt, List<UsageNotification> notifications) {

	/**
	 * Refuses a missing component by name and copies the list. An absent {@code notifications} is
	 * refused rather than read as empty, so a misspelled key cannot pass for "nothing pending".
	 *
	 * @throws NullPointerException when {@code generatedAt} or {@code notifications} is missing, or a
	 *                              notification is null
	 */
	public UsageNotificationBatch {
		Objects.requireNonNull(generatedAt, "generatedAt");
		Objects.requireNonNull(notifications, "notifications");
		notifications = List.copyOf(notifications);
	}
}
