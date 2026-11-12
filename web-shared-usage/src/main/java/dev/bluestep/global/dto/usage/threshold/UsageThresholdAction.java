package dev.bluestep.global.dto.usage.threshold;

import java.util.Collections;
import java.util.EnumSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

import org.jspecify.annotations.Nullable;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import com.fasterxml.jackson.annotation.JsonTypeName;

import dev.bluestep.global.dto.tenantaccess.TenantUserKey;
import dev.bluestep.global.dto.usage.ContractRelease;

/**
 * One stage of a {@link UsageThreshold}: when the metric reaches {@code limit}, do {@code kind}.
 *
 * <p>The limit is in the metric's own unit (a count, milliseconds or bytes, as each
 * {@link dev.bluestep.global.dto.usage.UsageMetric} constant documents). A stage trips once the
 * observed value is at or above it, and fires once per window.</p>
 *
 * <p>Wire shape: {@code {"limit":1000,"kind":{"type":"notify","channels":["EMAIL"],
 * "recipients":[{"classid":…,"seqnum":…}]}}} or {@code {"limit":2000,"kind":{"type":"break"}}}.</p>
 *
 * @param limit the value at which this stage trips; positive
 * @param kind  what happens when it does
 */
public record UsageThresholdAction(long limit, Kind kind) {

	/**
	 * Refuses a missing kind by name and a limit that is not positive.
	 *
	 * @throws NullPointerException     when {@code kind} is missing
	 * @throws IllegalArgumentException when {@code limit} is zero or negative
	 */
	public UsageThresholdAction {
		Objects.requireNonNull(kind, "kind");
		if (limit <= 0) {
			throw new IllegalArgumentException("limit " + limit + " is not positive");
		}
	}

	/**
	 * What a stage does when it trips.
	 *
	 * <p>On the wire, an object whose {@code type} names the kind: {@code notify} or {@code break}.
	 * Each permitted record declares its name with {@link JsonTypeName}, and {@link JsonSubTypes}
	 * lists every one; {@code UsageThresholdWireTest} holds the two lists equal. Closed: see the
	 * package javadoc for how a later kind reaches only callers that can bind it.</p>
	 */
	@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, include = JsonTypeInfo.As.PROPERTY, property = "type")
	@JsonSubTypes({
		@JsonSubTypes.Type(Notify.class),
		@JsonSubTypes.Type(Break.class)
	})
	public sealed interface Kind permits Notify, Break {

		/**
		 * The release that introduced this kind: a caller whose contract tag is older cannot bind it,
		 * so web-global leaves thresholds that use it out of that caller's answer. Not on the wire.
		 *
		 * @return the release this kind arrived in
		 */
		@JsonIgnore
		ContractRelease introducedIn();
	}

	/**
	 * Send a notice on each channel. {@link NotifyChannel#EMAIL} and {@link NotifyChannel#IN_APP}
	 * go to every recipient; {@link NotifyChannel#OPS} goes to Bluestep's operators and needs none.
	 *
	 * @param channels   where the notice goes, in {@link NotifyChannel} order; at least one
	 * @param recipients the tenant users an {@link NotifyChannel#EMAIL} or {@link NotifyChannel#IN_APP}
	 *                   notice goes to, in the order given, each once; between one and
	 *                   {@value #MAX_RECIPIENTS} when a channel names recipients, and none when no
	 *                   channel does
	 */
	@JsonTypeName("notify")
	public record Notify(Set<NotifyChannel> channels, List<TenantUserKey> recipients) implements Kind {

		/**
		 * The most recipients one stage names. web-global queues a notification per channel and
		 * recipient on every trip, so this bounds what one trip can queue.
		 */
		public static final int MAX_RECIPIENTS = 50;

		/**
		 * Refuses a notice that goes nowhere or to nobody it could reach, and takes ordered copies:
		 * channels in constant order and recipients in the order given. An absent recipient list is
		 * the empty one.
		 *
		 * @throws NullPointerException     when {@code channels} is missing or holds a null, or a
		 *                                  recipient is null
		 * @throws IllegalArgumentException when {@code channels} is empty, when a channel names
		 *                                  recipients and there are none, when there are recipients
		 *                                  and no channel names any, when a recipient is named twice,
		 *                                  or when there are more than {@value #MAX_RECIPIENTS}
		 */
		public Notify(final Set<NotifyChannel> channels, final @Nullable List<TenantUserKey> recipients) {
			Objects.requireNonNull(channels, "channels");
			if (channels.isEmpty()) {
				throw new IllegalArgumentException("a notice needs at least one channel");
			}
			this.channels = Collections.unmodifiableSet(EnumSet.copyOf(channels));
			this.recipients = recipients == null ? List.of() : ThresholdRules.requireDistinct("recipients", recipients);
			final boolean named = this.channels.stream().anyMatch(NotifyChannel::namesRecipients);
			if (named && this.recipients.isEmpty()) {
				throw new IllegalArgumentException("channels " + this.channels + " need at least one recipient");
			}
			if (!named && !this.recipients.isEmpty()) {
				throw new IllegalArgumentException("channels " + this.channels + " name no recipients, but "
						+ this.recipients.size() + " were given");
			}
			if (this.recipients.size() > MAX_RECIPIENTS) {
				throw new IllegalArgumentException(this.recipients.size() + " recipients is more than "
						+ MAX_RECIPIENTS);
			}
		}

		@Override
		public ContractRelease introducedIn() {
			return ContractRelease.V5_3_0;
		}
	}

	/**
	 * Break the circuit: refuse further usage of what the metric measures, for the tenant and the
	 * threshold's category, until the stage clears. A threshold has at most one, only on a metric
	 * that refusing usage can hold still (see {@link UsageThreshold}), and no stage above it.
	 */
	@JsonTypeName("break")
	public record Break() implements Kind {

		@Override
		public ContractRelease introducedIn() {
			return ContractRelease.V5_3_0;
		}
	}
}
