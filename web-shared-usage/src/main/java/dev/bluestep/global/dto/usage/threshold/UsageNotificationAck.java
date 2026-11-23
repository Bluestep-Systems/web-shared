package dev.bluestep.global.dto.usage.threshold;

import java.util.Objects;

/**
 * What became of one {@link UsageNotification}: an entry of {@link UsageNotificationAckRequest}.
 *
 * @param id      the {@link UsageNotification#id()} handled
 * @param outcome whether it was sent or can never be
 */
public record UsageNotificationAck(long id, UsageNotificationOutcome outcome) {

	/**
	 * Refuses a missing outcome by name.
	 *
	 * @throws NullPointerException when {@code outcome} is missing
	 */
	public UsageNotificationAck {
		Objects.requireNonNull(outcome, "outcome");
	}
}
