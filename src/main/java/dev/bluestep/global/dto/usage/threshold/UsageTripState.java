package dev.bluestep.global.dto.usage.threshold;

import java.time.Instant;
import java.util.Objects;
import java.util.Optional;

import org.jspecify.annotations.Nullable;

import com.fasterxml.jackson.annotation.JsonIgnore;

/**
 * One stage of a {@link UsageThreshold} that has tripped: when, in which window, and whether it has
 * cleared since.
 *
 * <p>A trip belongs to the {@linkplain UsageThreshold#version() version} of its threshold it was
 * evaluated against. web-global closes a threshold's open trips when the threshold is replaced, so
 * {@code actionIndex} is only ever read against {@code thresholdVersion}: a replace that reorders or
 * drops stages never re-points a trip at a different stage.</p>
 *
 * <p>A stage trips at most once per window. It clears when its window rolls over, when a level
 * falls back below the stage's level, or when a writer resets it; a cleared trip is history, and the
 * stage may trip again. While a {@link UsageThresholdAction.Break} stage's trip is uncleared, the
 * circuit it guards is broken.</p>
 *
 * @param id               web-global's id for the trip, which a reset names
 * @param thresholdId      the {@link UsageThreshold#id()} that tripped
 * @param thresholdVersion the {@link UsageThreshold#version()} the trip was evaluated against;
 *                         never negative
 * @param actionIndex      the stage that tripped: its index in {@link UsageThreshold#actions()} of
 *                         that version
 * @param windowStart      the start of the window the stage tripped in, or {@code null} for a
 *                         {@link UsageThresholdWindow#LEVEL} threshold, which has no window; never
 *                         after {@code trippedAt}
 * @param trippedAt        when the stage tripped
 * @param clearedAt        when the trip cleared, or {@code null} while it stands; never before
 *                         {@code trippedAt}
 * @param lastNotifiedAt   when a notice for the trip was last queued, or {@code null} if never, as
 *                         for a stage that only breaks; never before {@code trippedAt}
 */
public record UsageTripState(
		long id,
		long thresholdId,
		long thresholdVersion,
		int actionIndex,
		@Nullable Instant windowStart,
		Instant trippedAt,
		@Nullable Instant clearedAt,
		@Nullable Instant lastNotifiedAt) {

	/**
	 * Refuses a missing trip time by name and moments out of order.
	 *
	 * @throws NullPointerException     when {@code trippedAt} is missing
	 * @throws IllegalArgumentException when {@code thresholdVersion} or {@code actionIndex} is
	 *                                  negative, {@code windowStart} is after {@code trippedAt}, or
	 *                                  {@code clearedAt} or {@code lastNotifiedAt} is before it
	 */
	public UsageTripState {
		Objects.requireNonNull(trippedAt, "trippedAt");
		if (thresholdVersion < 0) {
			throw new IllegalArgumentException("thresholdVersion " + thresholdVersion + " is negative");
		}
		if (actionIndex < 0) {
			throw new IllegalArgumentException("actionIndex " + actionIndex + " is negative");
		}
		if (windowStart != null && windowStart.isAfter(trippedAt)) {
			throw new IllegalArgumentException("windowStart " + windowStart + " is after trippedAt "
					+ trippedAt);
		}
		ThresholdRules.requireNotBefore("clearedAt", clearedAt, "trippedAt", trippedAt);
		ThresholdRules.requireNotBefore("lastNotifiedAt", lastNotifiedAt, "trippedAt", trippedAt);
	}

	/**
	 * The start of the window the stage tripped in.
	 *
	 * @return the window start, or empty for a level threshold
	 */
	public Optional<Instant> optWindowStart() {
		return Optional.ofNullable(windowStart);
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
	 * When a notice for the trip was last queued.
	 *
	 * @return the time, or empty if none ever was
	 */
	public Optional<Instant> optLastNotifiedAt() {
		return Optional.ofNullable(lastNotifiedAt);
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
