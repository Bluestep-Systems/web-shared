package dev.bluestep.global.dto.usage.threshold;

import java.util.Collection;
import java.util.Collections;
import java.util.EnumSet;
import java.util.LinkedHashSet;
import java.util.Objects;
import java.util.Set;

import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import com.fasterxml.jackson.annotation.JsonTypeName;

/**
 * One stage of a {@link UsageThreshold}: when the metric reaches {@code level}, do {@code kind}.
 *
 * <p>The level is in the metric's own unit (a count, milliseconds or bytes, as each
 * {@link dev.bluestep.global.dto.usage.UsageMetric} constant documents). A stage trips once the
 * observed value is at or above it, and fires once per window.</p>
 *
 * <p>Wire shape: {@code {"level":1000,"kind":{"type":"notify","channels":["EMAIL"],
 * "recipients":[{"classid":…,"seqnum":…}]}}} or {@code {"level":2000,"kind":{"type":"break"}}}.</p>
 *
 * @param level the value at which this stage trips; positive
 * @param kind  what happens when it does
 */
public record UsageThresholdAction(long level, Kind kind) {

	/**
	 * Refuses a missing kind by name and a level that is not positive.
	 *
	 * @throws NullPointerException     when {@code kind} is missing
	 * @throws IllegalArgumentException when {@code level} is zero or negative
	 */
	public UsageThresholdAction {
		Objects.requireNonNull(kind, "kind");
		if (level <= 0) {
			throw new IllegalArgumentException("level " + level + " is not positive");
		}
	}

	/**
	 * What a stage does when it trips.
	 *
	 * <p>On the wire, an object whose {@code type} names the kind: {@code notify} or {@code break}.
	 * Each permitted record declares its name with {@link JsonTypeName}, and {@link JsonSubTypes}
	 * lists every one; {@code UsageThresholdWireTest} holds the two lists equal. Closed: see the
	 * package javadoc for how a later kind reaches only consumers that know it.</p>
	 */
	@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, include = JsonTypeInfo.As.PROPERTY, property = "type")
	@JsonSubTypes({
		@JsonSubTypes.Type(Notify.class),
		@JsonSubTypes.Type(Break.class)
	})
	public sealed interface Kind permits Notify, Break {}

	/**
	 * Send a notice on each channel. {@link NotifyChannel#EMAIL} and {@link NotifyChannel#IN_APP}
	 * go to every recipient; {@link NotifyChannel#OPS} goes to Bluestep's operators and needs none.
	 *
	 * @param channels   where the notice goes, in {@link NotifyChannel} order; at least one
	 * @param recipients the tenant users an {@link NotifyChannel#EMAIL} or {@link NotifyChannel#IN_APP}
	 *                   notice goes to, in the order given, each once; at least one when a channel
	 *                   names recipients, and none when no channel does
	 */
	@JsonTypeName("notify")
	public record Notify(Set<NotifyChannel> channels, Set<TenantUserKey> recipients) implements Kind {

		/**
		 * Refuses a notice that goes nowhere or to nobody it could reach, and takes ordered copies:
		 * channels in constant order and recipients in the order given, a repeated one kept once.
		 * An absent recipient list is the empty one.
		 *
		 * @throws NullPointerException     when {@code channels} is missing or holds a null
		 * @throws IllegalArgumentException when {@code channels} is empty, when a channel names
		 *                                  recipients and there are none, or when there are
		 *                                  recipients and no channel names any
		 */
		public Notify {
			Objects.requireNonNull(channels, "channels");
			if (channels.isEmpty()) {
				throw new IllegalArgumentException("a notice needs at least one channel");
			}
			channels = Collections.unmodifiableSet(EnumSet.copyOf(channels));
			recipients = recipients == null ? Set.of() : ordered(recipients);
			final boolean named = channels.stream().anyMatch(NotifyChannel::namesRecipients);
			if (named && recipients.isEmpty()) {
				throw new IllegalArgumentException("channels " + channels + " need at least one recipient");
			}
			if (!named && !recipients.isEmpty()) {
				throw new IllegalArgumentException("channels " + channels + " name no recipients, but "
						+ recipients.size() + " were given");
			}
		}

		private static Set<TenantUserKey> ordered(final Collection<TenantUserKey> recipients) {
			for (final TenantUserKey recipient : recipients) {
				Objects.requireNonNull(recipient, "recipients element");
			}
			return Collections.unmodifiableSet(new LinkedHashSet<>(recipients));
		}
	}

	/**
	 * Break the circuit: refuse further usage of what the metric measures, for the tenant and the
	 * threshold's category, until the stage clears. A threshold has at most one.
	 */
	@JsonTypeName("break")
	public record Break() implements Kind {}
}
