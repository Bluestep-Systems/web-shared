package dev.bluestep.global.dto.usage;

import java.util.List;

/**
 * One category's line on a usage chart: the {@link UsageSeriesPoint}s of a
 * {@link UsageSeriesResponse}, one per bucket of its range, in time order.
 *
 * <p>Every series in one response has the same buckets — the response's constructor holds it to
 * that — so a consumer charting the categories together, or summing them into one line, can pair
 * points by index.</p>
 *
 * @param category the category every point counts
 * @param points   one point per bucket, oldest first; a bucket with no traffic is a zero point
 */
public record UsageCategorySeries(UsageCategory category, List<UsageSeriesPoint> points) {

	/**
	 * Folds an absent list into its empty form and takes a defensive copy of a present one, so a
	 * Java caller passing {@code null} gets what an omitted JSON key binds to.
	 */
	public UsageCategorySeries {
		points = points == null ? List.of() : List.copyOf(points);
	}
}
