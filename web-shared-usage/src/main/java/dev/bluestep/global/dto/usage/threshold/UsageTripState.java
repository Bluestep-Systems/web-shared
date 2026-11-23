package dev.bluestep.global.dto.usage.threshold;

import java.time.Instant;
import java.util.Objects;
import java.util.Optional;

import org.jspecify.annotations.Nullable;

import com.fasterxml.jackson.annotation.JsonIgnore;

/**
 * One stage of a {@link UsageThreshold} that has tripped: when, in which window, against what limit,
 * and whether it has cleared since.
 *
 * <p>A trip belongs to the {@linkplain UsageThreshold#version() version} of its threshold it was
 * evaluated against. web-global closes a threshold's open trips when the threshold is replaced, so
 * {@code actionIndex} is only ever read against {@code thresholdVersion}: a replace that reorders or
 * drops stages never re-points a trip at a different stage.</p>
 *
 * <p>The stage's limit, what was observed and whether it breaks are snapshotted onto the trip, with
 * the window it tripped in, so a banner or a history row renders from the trip alone, even after the
 * threshold has moved on to a later version.</p>
 *
 * <p>A stage trips at most once per window. It clears when its window rolls over, when a reading
 * falls back below the stage's limit, or when a writer resets it; a cleared trip is history, and the
 * stage may trip again. While a {@link UsageThresholdAction.Break} stage's trip is uncleared, the
 * circuit it guards is broken.</p>
 *
 * @param id               web-global's id for the trip, which a reset names
 * @param thresholdId      the {@link UsageThreshold#id()} that tripped
 * @param thresholdVersion the {@link UsageThreshold#version()} the trip was evaluated against;
 *                         never negative
 * @param actionIndex      the stage that tripped: its index in {@link UsageThreshold#actions()} of
 *                         that version; never negative
 * @param limit            the stage's {@link UsageThresholdAction#limit()}; positive
 * @param observed         the value observed when it tripped, in the metric's unit; at least
 *                         {@code limit}
 * @param breaks           whether the stage is a {@link UsageThresholdAction.Break}
 * @param span             the window the stage tripped in, or {@code null} for a
 *                         {@link UsageThresholdWindow#LATEST} threshold, which has none; never
 *                         starting after {@code trippedAt}
 * @param trippedAt        when the stage tripped
 * @param clearedAt        when the trip cleared, or {@code null} while it stands; never before
 *                         {@code trippedAt}
 * @param notifiedAt       when the stage's notice went out (queued for web, or raised to operators
 *                         for {@link NotifyChannel#OPS}), or {@code null} for a stage that only
 *                         breaks; never before {@code trippedAt}
 */
public record UsageTripState(
		long id,
		long thresholdId,
		long thresholdVersion,
		int actionIndex,
		long limit,
		long observed,
		boolean breaks,
		UsageThresholdWindow.@Nullable Span span,
		Instant trippedAt,
		@Nullable Instant clearedAt,
		@Nullable Instant notifiedAt) {

	/**
	 * Refuses a missing trip time by name, a value that does not reach its limit, and moments out of
	 * order.
	 *
	 * @throws NullPointerException     when {@code trippedAt} is missing
	 * @throws IllegalArgumentException when {@code thresholdVersion} or {@code actionIndex} is
	 *                                  negative, {@code limit} is not positive, {@code observed} is
	 *                                  below it, {@code span} starts after {@code trippedAt}, or
	 *                                  {@code clearedAt} or {@code notifiedAt} is before it
	 */
	public UsageTripState {
		Objects.requireNonNull(trippedAt, "trippedAt");
		ThresholdRules.requireNotNegative("thresholdVersion", thresholdVersion);
		ThresholdRules.requireNotNegative("actionIndex", actionIndex);
		ThresholdRules.requireReached(observed, limit);
		if (span != null) {
			ThresholdRules.requireNotBefore("trippedAt", trippedAt, "span start", span.start());
		}
		ThresholdRules.requireNotBefore("clearedAt", clearedAt, "trippedAt", trippedAt);
		ThresholdRules.requireNotBefore("notifiedAt", notifiedAt, "trippedAt", trippedAt);
	}

	/**
	 * The window the stage tripped in.
	 *
	 * @return the window, or empty for a threshold on the latest reading
	 */
	public Optional<UsageThresholdWindow.Span> optSpan() {
		return Optional.ofNullable(span);
	}

	/**
	 * When the trip cleared.
	 *
	 * @return the time, or empty while the trip stands
	 */
	public Optional<Instant> optClearedAt() {
		return Optional.ofNullable(clearedAt);
	}

	/**
	 * When the stage's notice went out.
	 *
	 * @return the time, or empty for a stage that only breaks
	 */
	public Optional<Instant> optNotifiedAt() {
		return Optional.ofNullable(notifiedAt);
	}

	/**
	 * Whether the trip still stands.
	 *
	 * <p>Derived, so not on the wire.</p>
	 *
	 * @return {@code true} until it clears
	 */
	@JsonIgnore
	public boolean isActive() {
		return clearedAt == null;
	}
}
