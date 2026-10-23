package dev.bluestep.global.dto.usage;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;

import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

/**
 * The bucket contract {@link UsageSeriesResponse}'s constructor holds: whole-bucket bounds, each
 * category once, and exactly one point per bucket at that bucket's start. These are what let a chart
 * pair the categories' points by index without filling holes, so each way of breaking them is
 * refused here, and the same refusal reaches a consumer binding a broken response.
 */
class UsageSeriesValidationTest {

	private static final ObjectMapper JSON = JsonMapper.builder().build();
	private static final Instant DAY_0 = Instant.parse("2026-09-07T00:00:00Z");
	private static final long HOUR_SECONDS = 3_600;

	private static List<UsageSeriesPoint> hourlyZeros(final Instant from, final int count) {
		final List<UsageSeriesPoint> points = new ArrayList<>();
		for (int i = 0; i < count; i++) {
			points.add(UsageSeriesPoint.empty(from.plusSeconds(i * HOUR_SECONDS)));
		}
		return points;
	}

	private static UsageSeriesResponse hourly(final Instant from, final Instant to,
			final List<UsageCategorySeries> series) {
		return new UsageSeriesResponse("U1000001", from, to, UsageGrain.HOUR, series);
	}

	private static UsageCategorySeries http(final List<UsageSeriesPoint> points) {
		return new UsageCategorySeries(UsageCategory.HTTP, points);
	}

	@Test
	void aFullyBucketedResponseIsAccepted() {
		final Instant to = DAY_0.plusSeconds(24 * HOUR_SECONDS);
		assertDoesNotThrow(() -> hourly(DAY_0, to, List.of(http(hourlyZeros(DAY_0, 24)),
				new UsageCategorySeries(UsageCategory.FILE, hourlyZeros(DAY_0, 24)))));
		assertDoesNotThrow(() -> new UsageSeriesResponse("U1000001", DAY_0, to, UsageGrain.DAY,
				List.of(http(List.of(UsageSeriesPoint.empty(DAY_0))))));
	}

	/** Pre-epoch bounds align by floor, not by Java's truncating remainder. */
	@Test
	void alignmentHoldsBeforeTheEpoch() {
		final Instant from = Instant.parse("1969-12-31T00:00:00Z");
		assertDoesNotThrow(() -> new UsageSeriesResponse("U1000001", from, Instant.EPOCH, UsageGrain.DAY,
				List.of(http(List.of(UsageSeriesPoint.empty(from))))));
	}

	@Test
	void anUnalignedBoundIsRefused() {
		final Instant halfPast = DAY_0.plusSeconds(1_800);
		assertThrows(IllegalArgumentException.class, () -> hourly(halfPast, halfPast, List.of()));
		assertThrows(IllegalArgumentException.class, () -> hourly(DAY_0, halfPast, List.of()));
		assertThrows(IllegalArgumentException.class,
				() -> hourly(DAY_0.plusNanos(1), DAY_0.plusNanos(1), List.of()), "sub-milli remainders count");
		final Instant hour1 = DAY_0.plusSeconds(HOUR_SECONDS);
		assertThrows(IllegalArgumentException.class, () -> new UsageSeriesResponse("U1000001", hour1, hour1,
				UsageGrain.DAY, List.of()), "an hour boundary is not a day boundary");
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

	@Test
	void aPointOffItsBucketIsRefused() {
		final Instant to = DAY_0.plusSeconds(2 * HOUR_SECONDS);
		final List<UsageSeriesPoint> reversed = new ArrayList<>(hourlyZeros(DAY_0, 2)).reversed();
		assertThrows(IllegalArgumentException.class, () -> hourly(DAY_0, to, List.of(http(reversed))));
		final List<UsageSeriesPoint> shifted = hourlyZeros(DAY_0.plusSeconds(60), 2);
		assertThrows(IllegalArgumentException.class, () -> hourly(DAY_0, to, List.of(http(shifted))));
	}

	/** The refusal reaches a consumer binding a broken response, rather than handing it holes. */
	@Test
	void aBrokenResponseFailsBinding() {
		assertThrows(JacksonException.class, () -> JSON.readValue("""
				{"schemaName":"U1000001","from":"2026-09-07T00:00:00Z","to":"2026-09-07T02:00:00Z",
				 "grain":"HOUR","series":[{"category":"http","points":[
				  {"start":"2026-09-07T00:00:00Z","hits":1,"pageMillis":2,"pageMillisMax":3,
				   "dbMillis":4,"requestBytes":5,"responseBytes":6}]}]}
				""", UsageSeriesResponse.class));
	}

	@Test
	void emptyIsAZeroPointAtItsStart() {
		assertEquals(new UsageSeriesPoint(DAY_0, 0, 0, 0, 0, 0, 0), UsageSeriesPoint.empty(DAY_0));
	}
}
