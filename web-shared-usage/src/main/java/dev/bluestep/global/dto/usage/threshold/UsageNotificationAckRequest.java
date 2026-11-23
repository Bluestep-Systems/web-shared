package dev.bluestep.global.dto.usage.threshold;

import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * web's dispatcher telling web-global what became of the {@link UsageNotification}s it handled, so
 * they stop being offered.
 *
 * <p>Each notification is one (channel, recipient), so an outcome is per recipient. A notice the
 * dispatcher sent is {@link UsageNotificationOutcome#DELIVERED}; one it can never send, such as to a
 * user since deleted or with no email address, is {@link UsageNotificationOutcome#UNDELIVERABLE}, and
 * web-global keeps it as unsent rather than retrying it forever. A send that failed and may succeed
 * later is left out, so the notice stays pending and is offered again without resending the
 * rest.</p>
 *
 * <p>Idempotent: reporting a notification already reported, or one web-global no longer holds,
 * changes nothing and is not an error, so a dispatcher that lost the answer simply sends it again.
 * The first outcome recorded for a notification stands.</p>
 *
 * @param acks one outcome per notification handled, in the order given, each notification once; at
 *             least one
 */
public record UsageNotificationAckRequest(List<UsageNotificationAck> acks) {

	/**
	 * Refuses a missing or empty acknowledgement and one naming a notification twice, and copies the
	 * outcomes in order.
	 *
	 * @throws NullPointerException     when {@code acks} is missing or holds a null
	 * @throws IllegalArgumentException when {@code acks} is empty or names one notification twice
	 */
	public UsageNotificationAckRequest {
		Objects.requireNonNull(acks, "acks");
		acks = List.copyOf(acks);
		if (acks.isEmpty()) {
			throw new IllegalArgumentException("an acknowledgement names at least one notification");
		}
		final Set<Long> seen = new HashSet<>();
		for (final UsageNotificationAck ack : acks) {
			if (!seen.add(ack.id())) {
				throw new IllegalArgumentException("notification " + ack.id() + " is acknowledged twice");
			}
		}
	}
}
