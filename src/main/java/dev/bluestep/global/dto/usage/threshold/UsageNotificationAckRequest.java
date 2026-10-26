package dev.bluestep.global.dto.usage.threshold;

import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Objects;
import java.util.Set;

/**
 * web's dispatcher telling web-global which {@link UsageNotification}s it has sent, so they stop
 * being offered.
 *
 * <p>Each notification is one (channel, recipient), so an acknowledgement is per recipient: the
 * dispatcher names exactly the notices it delivered, and any it left out, a failed send to one user
 * on one channel, stay pending and are offered again without resending the rest.</p>
 *
 * <p>Idempotent: acknowledging a notification already acknowledged, or one web-global no longer
 * holds, changes nothing and is not an error, so a dispatcher that lost the answer to an
 * acknowledgement simply sends it again.</p>
 *
 * @param ids the {@link UsageNotification#id()}s sent, each once; at least one
 */
public record UsageNotificationAckRequest(Set<Long> ids) {

	/**
	 * Refuses a missing or empty acknowledgement and copies the ids in order.
	 *
	 * @throws NullPointerException     when {@code ids} is missing or holds a null
	 * @throws IllegalArgumentException when {@code ids} is empty
	 */
	public UsageNotificationAckRequest {
		Objects.requireNonNull(ids, "ids");
		for (final Long id : ids) {
			Objects.requireNonNull(id, "ids element");
		}
		ids = Collections.unmodifiableSet(new LinkedHashSet<>(ids));
		if (ids.isEmpty()) {
			throw new IllegalArgumentException("an acknowledgement names at least one notification");
		}
	}
}
