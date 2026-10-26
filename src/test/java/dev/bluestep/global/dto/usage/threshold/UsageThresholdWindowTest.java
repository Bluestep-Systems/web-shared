package dev.bluestep.global.dto.usage.threshold;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import dev.bluestep.global.dto.ai.BudgetSchedule;
import dev.bluestep.global.dto.usage.UsageMetric;

/** The UTC window arithmetic each {@link UsageThresholdWindow} carries. */
class UsageThresholdWindowTest {

	/** A Wednesday, mid-hour, with a sub-second part that every window must truncate. */
	private static final Instant AT = Instant.parse("2026-09-09T13:45:12.345Z");

	private static UsageThresholdWindow.Span span(final String start, final String end) {
		return new UsageThresholdWindow.Span(Instant.parse(start), Instant.parse(end));
	}

	@Test
	void hourly_isTheUtcHour() {
		assertEquals(Optional.of(span("2026-09-09T13:00:00Z", "2026-09-09T14:00:00Z")),
				UsageThresholdWindow.HOURLY.spanAt(AT));
	}

	@Test
	void daily_isTheUtcDay() {
		assertEquals(Optional.of(span("2026-09-09T00:00:00Z", "2026-09-10T00:00:00Z")),
				UsageThresholdWindow.DAILY.spanAt(AT));
	}

	@Test
	void weekly_runsMondayToMondayUtc() {
		assertEquals(Optional.of(span("2026-09-07T00:00:00Z", "2026-09-14T00:00:00Z")),
				UsageThresholdWindow.WEEKLY.spanAt(AT));
		assertEquals(Optional.of(span("2026-09-07T00:00:00Z", "2026-09-14T00:00:00Z")),
				UsageThresholdWindow.WEEKLY.spanAt(Instant.parse("2026-09-07T00:00:00Z")),
				"Monday midnight starts its own week");
		assertEquals(Optional.of(span("2026-08-31T00:00:00Z", "2026-09-07T00:00:00Z")),
				UsageThresholdWindow.WEEKLY.spanAt(Instant.parse("2026-09-06T23:59:59.999Z")),
				"Sunday's last instant is still the week before");
	}

	@Test
	void monthly_isTheUtcCalendarMonth() {
		assertEquals(Optional.of(span("2026-09-01T00:00:00Z", "2026-10-01T00:00:00Z")),
				UsageThresholdWindow.MONTHLY.spanAt(AT));
		assertEquals(Optional.of(span("2028-02-01T00:00:00Z", "2028-03-01T00:00:00Z")),
				UsageThresholdWindow.MONTHLY.spanAt(Instant.parse("2028-02-29T12:00:00Z")),
				"a leap February is 29 days");
		assertEquals(Optional.of(span("2027-02-01T00:00:00Z", "2027-03-01T00:00:00Z")),
				UsageThresholdWindow.MONTHLY.spanAt(Instant.parse("2027-02-28T23:59:59Z")),
				"a non-leap February is 28 days");
		assertEquals(Optional.of(span("2026-12-01T00:00:00Z", "2027-01-01T00:00:00Z")),
				UsageThresholdWindow.MONTHLY.spanAt(Instant.parse("2026-12-31T23:59:59.999Z")),
				"December rolls into January");
		assertEquals(Optional.of(span("2026-10-01T00:00:00Z", "2026-11-01T00:00:00Z")),
				UsageThresholdWindow.MONTHLY.spanAt(Instant.parse("2026-10-31T12:00:00Z")),
				"the 31st is in its own month");
	}

	@Test
	void weekly_crossesAYearBoundary() {
		assertEquals(Optional.of(span("2025-12-29T00:00:00Z", "2026-01-05T00:00:00Z")),
				UsageThresholdWindow.WEEKLY.spanAt(Instant.parse("2026-01-01T09:00:00Z")),
				"2026-01-01 is a Thursday, so its week starts Monday 2025-12-29");
	}

	@Test
	void hourlyAndDaily_rollOverAtTheYearEnd() {
		final Instant lastMoment = Instant.parse("2026-12-31T23:59:59.999Z");
		assertEquals(Optional.of(span("2026-12-31T23:00:00Z", "2027-01-01T00:00:00Z")),
				UsageThresholdWindow.HOURLY.spanAt(lastMoment));
		assertEquals(Optional.of(span("2026-12-31T00:00:00Z", "2027-01-01T00:00:00Z")),
				UsageThresholdWindow.DAILY.spanAt(lastMoment));
	}

	/**
	 * Each calendar window delegates to the {@link BudgetSchedule} of the same name, anchored at UTC,
	 * so the names must keep lining up.
	 */
	@ParameterizedTest
	@EnumSource(value = UsageThresholdWindow.class, names = "LATEST", mode = EnumSource.Mode.EXCLUDE)
	void everyCalendarWindow_matchesTheBudgetScheduleOfItsName(final UsageThresholdWindow window) {
		final BudgetSchedule schedule = BudgetSchedule.valueOf(window.name());
		final OffsetDateTime start = schedule.windowStart(AT.atOffset(ZoneOffset.UTC), ZoneOffset.UTC);
		assertEquals(Optional.of(new UsageThresholdWindow.Span(start.toInstant(),
				schedule.windowEnd(start).orElseThrow().toInstant())), window.spanAt(AT));
	}

	@Test
	void latest_hasNoWindow() {
		assertEquals(Optional.empty(), UsageThresholdWindow.LATEST.spanAt(AT));
		assertFalse(UsageThresholdWindow.LATEST.isWindowed());
	}

	@ParameterizedTest
	@EnumSource(value = UsageThresholdWindow.class, names = "LATEST", mode = EnumSource.Mode.EXCLUDE)
	void everyCalendarWindow_containsItsMomentAndAbutsTheNext(final UsageThresholdWindow window) {
		assertTrue(window.isWindowed());
		final UsageThresholdWindow.Span span = window.spanAt(AT).orElseThrow();
		assertTrue(span.contains(AT));
		assertEquals(Optional.of(span), window.spanAt(span.start()), "the start is in its own window");
		assertEquals(span.end(), window.spanAt(span.end()).orElseThrow().start(),
				"the end starts the next window");
		assertEquals(Optional.of(span), window.spanAt(span.end().minusNanos(1)));
	}

	@Test
	void suits_pairsLevelWithLatestOnly() {
		for (final UsageMetric.Aggregation aggregation : UsageMetric.Aggregation.values()) {
			final boolean level = aggregation == UsageMetric.Aggregation.LEVEL;
			assertEquals(level, UsageThresholdWindow.LATEST.suits(aggregation), "LATEST / " + aggregation);
			assertEquals(!level, UsageThresholdWindow.MONTHLY.suits(aggregation), "MONTHLY / " + aggregation);
		}
	}
}
