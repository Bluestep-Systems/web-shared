package dev.bluestep.global.dto.usage;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;

import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

/**
 * The bucket contract {@link UsageSeriesResponse}'s constructor holds: whole-bucket bounds, each
 * category once, exactly one point per bucket at that bucket's start, a fold time no earlier than
 * the range, and every reference component present. These are what let a chart pair the categories'
 * points by index without filling holes, so each way of breaking them is refused here, and the same
 * refusal reaches a consumer binding a broken response.
 */
class UsageSeriesValidationTest {

	private static final ObjectMapper JSON = JsonMapper.builder().build();
	private static final Instant DAY_0 = Instant.parse("2026-09-07T00:00:00Z");
	private static final long HOUR_SECONDS = 3_600;
	private static final long DAY_SECONDS = 86_400;

	private static List<UsageSeriesPoint> zeros(final Instant from, final int count, final long stepSeconds) {
		final List<UsageSeriesPoint> points = new ArrayList<>();
		for (int i = 0; i < count; i++) {
			points.add(UsageSeriesPoint.empty(from.plusSeconds(i * stepSeconds)));
		}
		return points;
	}

	private static List<UsageSeriesPoint> hourlyZeros(final Instant from, final int count) {
		return zeros(from, count, HOUR_SECONDS);
	}

	/** An hourly response folded at {@code to}, so no bucket is partial. */
	private static UsageSeriesResponse hourly(final Instant from, final Instant to,
			final List<UsageCategorySeries> series) {
		return new UsageSeriesResponse("U1000001", from, to, to, UsageGrain.HOUR, series);
	}

	private static UsageSeriesResponse daily(final Instant from, final Instant to,
			final List<UsageCategorySeries> series) {
		return new UsageSeriesResponse("U1000001", from, to, to, UsageGrain.DAY, series);
	}

	private static UsageCategorySeries http(final List<UsageSeriesPoint> points) {
		return new UsageCategorySeries(UsageCategory.HTTP, points);
	}

	private static UsageCategorySeries file(final List<UsageSeriesPoint> points) {
		return new UsageCategorySeries(UsageCategory.FILE, points);
	}

	@Test
	void aFullyBucketedResponseIsAccepted() {
		final Instant to = DAY_0.plusSeconds(DAY_SECONDS);
		assertDoesNotThrow(() -> hourly(DAY_0, to, List.of(http(hourlyZeros(DAY_0, 24)),
				file(hourlyZeros(DAY_0, 24)))));
		assertDoesNotThrow(() -> daily(DAY_0, to, List.of(http(List.of(UsageSeriesPoint.empty(DAY_0))))));
	}

	/** Several DAY points, so the per-point start check runs past the first at day width. */
	@Test
	void dailyPointsAreSpacedADayApart() {
		final Instant to = DAY_0.plusSeconds(3 * DAY_SECONDS);
		assertDoesNotThrow(() -> daily(DAY_0, to, List.of(http(zeros(DAY_0, 3, DAY_SECONDS)))));
		final List<UsageSeriesPoint> hourOff = new ArrayList<>(zeros(DAY_0, 3, DAY_SECONDS));
		hourOff.set(2, UsageSeriesPoint.empty(DAY_0.plusSeconds(2 * DAY_SECONDS + HOUR_SECONDS)));
		assertThrows(IllegalArgumentException.class, () -> daily(DAY_0, to, List.of(http(hourOff))));
		assertThrows(IllegalArgumentException.class,
				() -> daily(DAY_0, to, List.of(http(hourlyZeros(DAY_0, 3)))), "hourly spacing in a DAY series");
	}

	/** Pre-epoch bounds align by floor, not by Java's truncating remainder. */
	@Test
	void alignmentHoldsBeforeTheEpoch() {
		final Instant from = Instant.parse("1969-12-31T00:00:00Z");
		assertDoesNotThrow(() -> daily(from, Instant.EPOCH, List.of(http(List.of(UsageSeriesPoint.empty(from))))));
	}

	@Test
	void anUnalignedBoundIsRefused() {
		final Instant halfPast = DAY_0.plusSeconds(1_800);
		assertThrows(IllegalArgumentException.class, () -> hourly(halfPast, halfPast, List.of()));
		assertThrows(IllegalArgumentException.class, () -> hourly(DAY_0, halfPast, List.of()));
		assertThrows(IllegalArgumentException.class,
				() -> hourly(DAY_0.plusNanos(1), DAY_0.plusNanos(1), List.of()), "sub-milli remainders count");
		final Instant hour1 = DAY_0.plusSeconds(HOUR_SECONDS);
		assertThrows(IllegalArgumentException.class, () -> daily(hour1, hour1, List.of()),
				"an hour boundary is not a day boundary");
	}

	@Test
	void aReversedRangeIsRefused() {
		assertThrows(IllegalArgumentException.class,
				() -> hourly(DAY_0.plusSeconds(HOUR_SECONDS), DAY_0, List.of()));
	}

	@Test
	void aRepeatedCategoryIsRefused() {
		final Instant to = DAY_0.plusSeconds(HOUR_SECONDS);
		assertThrows(IllegalArgumentException.class, () -> hourly(DAY_0, to,
				List.of(http(hourlyZeros(DAY_0, 1)), http(hourlyZeros(DAY_0, 1)))));
	}

	/** An empty bucket is a zero point: a missing one, or an extra one, is refused. */
	@Test
	void aSeriesMissingOrAddingABucketIsRefused() {
		final Instant to = DAY_0.plusSeconds(3 * HOUR_SECONDS);
		assertThrows(IllegalArgumentException.class, () -> hourly(DAY_0, to, List.of(http(hourlyZeros(DAY_0, 2)))));
		assertThrows(IllegalArgumentException.class, () -> hourly(DAY_0, to, List.of(http(hourlyZeros(DAY_0, 4)))));
		assertThrows(IllegalArgumentException.class, () -> hourly(DAY_0, to, List.of(http(List.of()))));
	}

	/** Every series is checked, not only the first. */
	@Test
	void aShortSecondSeriesIsRefused() {
		final Instant to = DAY_0.plusSeconds(3 * HOUR_SECONDS);
		assertThrows(IllegalArgumentException.class, () -> hourly(DAY_0, to,
				List.of(http(hourlyZeros(DAY_0, 3)), file(hourlyZeros(DAY_0, 2)))));
	}

	/** An empty range has no buckets, so a series in it holds no points. */
	@Test
	void anEmptyRangeHoldsNoPoints() {
		assertDoesNotThrow(() -> hourly(DAY_0, DAY_0, List.of(http(List.of()))));
		assertThrows(IllegalArgumentException.class, () -> hourly(DAY_0, DAY_0, List.of(http(hourlyZeros(DAY_0, 1)))));
	}

	@Test
	void aPointOffItsBucketIsRefused() {
		final Instant to = DAY_0.plusSeconds(2 * HOUR_SECONDS);
		final List<UsageSeriesPoint> reversed = new ArrayList<>(hourlyZeros(DAY_0, 2)).reversed();
		assertThrows(IllegalArgumentException.class, () -> hourly(DAY_0, to, List.of(http(reversed))));
		final List<UsageSeriesPoint> shifted = hourlyZeros(DAY_0.plusSeconds(60), 2);
		assertThrows(IllegalArgumentException.class, () -> hourly(DAY_0, to, List.of(http(shifted))));
	}

	/** {@code asOf} may fall anywhere from the range's start on, unaligned, but not before it. */
	@Test
	void asOfIsNeverBeforeFrom() {
		final Instant to = DAY_0.plusSeconds(2 * HOUR_SECONDS);
		final List<UsageCategorySeries> series = List.of(http(hourlyZeros(DAY_0, 2)));
		for (final Instant asOf : List.of(DAY_0, DAY_0.plusMillis(1), to, to.plusSeconds(DAY_SECONDS))) {
			assertDoesNotThrow(() -> new UsageSeriesResponse("U1000001", DAY_0, to, asOf, UsageGrain.HOUR, series));
		}
		assertThrows(IllegalArgumentException.class, () -> new UsageSeriesResponse("U1000001", DAY_0, to,
				DAY_0.minusMillis(1), UsageGrain.HOUR, series));
	}

	/** A bucket ending after {@code asOf} is partial; one ending at or before it is complete. */
	@Test
	void bucketsEndingAfterAsOfArePartial() {
		final Instant to = DAY_0.plusSeconds(3 * HOUR_SECONDS);
		final List<UsageSeriesPoint> points = hourlyZeros(DAY_0, 3);
		final UsageSeriesResponse midSecond = new UsageSeriesResponse("U1000001", DAY_0, to,
				DAY_0.plusSeconds(HOUR_SECONDS + 1_800), UsageGrain.HOUR, List.of(http(points)));
		assertEquals(List.of(false, true, true), points.stream().map(midSecond::isPartial).toList());
		final UsageSeriesResponse atBoundary = new UsageSeriesResponse("U1000001", DAY_0, to,
				DAY_0.plusSeconds(2 * HOUR_SECONDS), UsageGrain.HOUR, List.of(http(points)));
		assertEquals(List.of(false, false, true), points.stream().map(atBoundary::isPartial).toList(),
				"a bucket ending exactly at asOf is complete");
		assertFalse(hourly(DAY_0, to, List.of(http(points))).isPartial(points.get(2)));
	}

	/** A Java caller's missing reference component is refused by name, not by a later NPE. */
	@Test
	void aMissingReferenceComponentIsRefusedByName() {
		final Instant to = DAY_0.plusSeconds(HOUR_SECONDS);
		assertEquals("schemaName", assertThrows(NullPointerException.class,
				() -> new UsageSeriesResponse(null, DAY_0, to, to, UsageGrain.HOUR, List.of())).getMessage());
		assertEquals("asOf", assertThrows(NullPointerException.class,
				() -> new UsageSeriesResponse("U1000001", DAY_0, to, null, UsageGrain.HOUR, List.of())).getMessage());
		assertEquals("grain", assertThrows(NullPointerException.class,
				() -> new UsageSeriesResponse("U1000001", DAY_0, to, to, null, List.of())).getMessage());
		assertEquals("category", assertThrows(NullPointerException.class,
				() -> new UsageCategorySeries(null, List.of())).getMessage());
		assertEquals("start", assertThrows(NullPointerException.class,
				() -> UsageSeriesPoint.empty(null)).getMessage());
	}

	/** The refusal reaches a consumer binding a broken response — this refusal, not just any. */
	@Test
	void aBrokenResponseFailsBindingWithTheBucketRefusal() {
		final JacksonException refused = assertThrows(JacksonException.class, () -> JSON.readValue("""
				{"schemaName":"U1000001","from":"2026-09-07T00:00:00Z","to":"2026-09-07T02:00:00Z",
				 "asOf":"2026-09-07T02:00:00Z","grain":"HOUR","series":[{"category":"http","points":[
				  {"start":"2026-09-07T00:00:00Z","hits":1,"pageMillis":2,"pageMillisMax":3,
				   "dbMillis":4,"requestBytes":5,"responseBytes":6}]}]}
				""", UsageSeriesResponse.class));
		boolean found = false;
		for (Throwable cause = refused; cause != null; cause = cause.getCause()) {
			if (cause instanceof IllegalArgumentException && cause.getMessage().contains("points for")) {
				found = true;
			}
		}
		assertTrue(found, "the cause chain carries the bucket-count refusal");
	}

	@Test
	void emptyIsAZeroPointAtItsStart() {
		assertEquals(new UsageSeriesPoint(DAY_0, 0, 0, 0, 0, 0, 0), UsageSeriesPoint.empty(DAY_0));
	}
}
