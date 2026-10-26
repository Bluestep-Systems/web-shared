package dev.bluestep.global.dto.usage.threshold;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Objects;
import java.util.Optional;

import dev.bluestep.global.dto.ai.BudgetSchedule;
import dev.bluestep.global.dto.usage.ContractRelease;
import dev.bluestep.global.dto.usage.UsageMetric;

/**
 * The stretch of time a {@link UsageThreshold} measures its metric over.
 *
 * <p>The four calendar windows are UTC-aligned for every tenant: an hour, a day, a week starting
 * Monday, or a calendar month, each starting at a UTC boundary. A threshold's usage is what fell
 * inside the current window, and a tripped stage clears when the window rolls over.
 * {@link #LATEST} is not a window at all: the metric is a reading at a moment, the latest one is
 * compared, and a stage clears when the reading falls back below its limit.</p>
 *
 * <p>Not {@link BudgetSchedule} on the wire, though the four calendar windows match it. That enum
 * also has {@code LIFETIME}, computes its windows in a per-tenant UTC offset, and has nothing for a
 * reading at a moment; a threshold has no lifetime budget, is UTC-only, and needs the reading. The
 * arithmetic is still {@link BudgetSchedule}'s, anchored at UTC, so the two cannot drift: each
 * calendar constant delegates to the schedule of the same name.</p>
 *
 * <p>Serialized under its constant name.</p>
 */
public enum UsageThresholdWindow {

	/** One UTC hour. */
	HOURLY {
		@Override
		public Optional<Span> spanAt(final Instant at) {
			return calendar(BudgetSchedule.HOURLY, at);
		}

		@Override
		public ContractRelease introducedIn() {
			return ContractRelease.V5_3_0;
		}
	},

	/** One UTC day. */
	DAILY {
		@Override
		public Optional<Span> spanAt(final Instant at) {
			return calendar(BudgetSchedule.DAILY, at);
		}

		@Override
		public ContractRelease introducedIn() {
			return ContractRelease.V5_3_0;
		}
	},

	/** One week, Monday 00:00 UTC to the next Monday. */
	WEEKLY {
		@Override
		public Optional<Span> spanAt(final Instant at) {
			return calendar(BudgetSchedule.WEEKLY, at);
		}

		@Override
		public ContractRelease introducedIn() {
			return ContractRelease.V5_3_0;
		}
	},

	/** One calendar month, from the first at 00:00 UTC. */
	MONTHLY {
		@Override
		public Optional<Span> spanAt(final Instant at) {
			return calendar(BudgetSchedule.MONTHLY, at);
		}

		@Override
		public ContractRelease introducedIn() {
			return ContractRelease.V5_3_0;
		}
	},

	/**
	 * No window: the threshold compares the latest reading of a {@link UsageMetric.Aggregation#LEVEL}
	 * metric against each limit.
	 */
	LATEST {
		@Override
		public Optional<Span> spanAt(final Instant at) {
			return Optional.empty();
		}

		@Override
		public ContractRelease introducedIn() {
			return ContractRelease.V5_3_0;
		}
	};

	/**
	 * The window that contains a moment.
	 *
	 * @param at any instant
	 * @return the window {@code at} falls in, start inclusive and end exclusive, or empty for
	 *         {@link #LATEST}, which has no window
	 */
	public abstract Optional<Span> spanAt(Instant at);

	/**
	 * The release that introduced this window: a caller whose contract tag is older cannot bind it,
	 * so web-global leaves rows that name it out of that caller's answer. Not on the wire.
	 *
	 * @return the release this constant arrived in
	 */
	public abstract ContractRelease introducedIn();

	/**
	 * Whether this measures usage over a window, which every value but {@link #LATEST} does.
	 *
	 * @return {@code true} for the calendar windows
	 */
	public boolean isWindowed() {
		return spanAt(Instant.EPOCH).isPresent();
	}

	/**
	 * Whether a threshold on a metric of this aggregation may use this window. A level is read at a
	 * moment, so it takes {@link #LATEST} and only that; every other aggregation is measured over a
	 * window, so it takes any value but {@link #LATEST}.
	 *
	 * @param aggregation how the metric behaves over a window
	 * @return {@code true} when the pair is coherent
	 */
	public boolean suits(final UsageMetric.Aggregation aggregation) {
		return isWindowed() != (aggregation == UsageMetric.Aggregation.LEVEL);
	}

	/** The UTC window of {@code schedule} that holds {@code at}. */
	private static Optional<Span> calendar(final BudgetSchedule schedule, final Instant at) {
		final OffsetDateTime start = schedule.windowStart(at.atOffset(ZoneOffset.UTC), ZoneOffset.UTC);
		final OffsetDateTime end = schedule.windowEnd(start).orElseThrow();
		return Optional.of(new Span(start.toInstant(), end.toInstant()));
	}

	/**
	 * One window: {@code [start, end)}.
	 *
	 * @param start the window's first instant, inclusive
	 * @param end   the instant after the window, exclusive; after {@code start}
	 */
	public record Span(Instant start, Instant end) {

		/**
		 * Refuses a missing bound by name and an empty or inverted span.
		 *
		 * @throws NullPointerException     when {@code start} or {@code end} is missing
		 * @throws IllegalArgumentException when {@code end} is not after {@code start}
		 */
		public Span {
			Objects.requireNonNull(start, "start");
			Objects.requireNonNull(end, "end");
			if (!end.isAfter(start)) {
				throw new IllegalArgumentException("end " + end + " is not after start " + start);
			}
		}

		/**
		 * Whether a moment falls inside this window.
		 *
		 * @param at any instant
		 * @return {@code true} when {@code start <= at < end}
		 */
		public boolean contains(final Instant at) {
			return !at.isBefore(start) && at.isBefore(end);
		}
	}
}
