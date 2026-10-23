package dev.bluestep.global.dto.usage;

import java.time.Instant;

/**
 * One bucket of a {@link UsageCategorySeries}: one tenant's request metrics for one category over
 * one {@link UsageGrain}-wide stretch of time, folded from every stored row whose window starts
 * inside it.
 *
 * <p>Totals are sums over the bucket. {@code pageMillisMax} is the largest single-request maximum
 * anywhere in the bucket — the slowest single hit, not the slowest minute's total. A bucket with no
 * traffic is present with every figure zero (see {@link #empty(Instant)}), never absent.</p>
 *
 * <p>The figures mirror {@link UsageWindow}'s tenant-visible metrics; its internal ones (thread CPU
 * time, allocated bytes, and the per-request byte maxima) are not carried.</p>
 *
 * @param start         the bucket's start, UTC-aligned to the response's grain; the bucket ends one
 *                      {@link UsageGrain#width()} later, exclusive
 * @param hits          requests completed in the bucket
 * @param pageMillis    wall time spent serving them, in milliseconds
 * @param pageMillisMax the slowest single request, in milliseconds
 * @param dbMillis      time spent in database calls, in milliseconds
 * @param requestBytes  request body bytes received
 * @param responseBytes response bytes sent
 */
public record UsageSeriesPoint(
		Instant start,
		long hits,
		long pageMillis,
		long pageMillisMax,
		long dbMillis,
		long requestBytes,
		long responseBytes) {

	/**
	 * The point for a bucket with no traffic: every figure zero.
	 *
	 * @param start the bucket's start
	 * @return a zero point at {@code start}
	 */
	public static UsageSeriesPoint empty(final Instant start) {
		return new UsageSeriesPoint(start, 0, 0, 0, 0, 0, 0);
	}
}
