package dev.bluestep.global.dto.usage;

import java.time.Duration;
import java.time.Instant;
import java.util.EnumSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * One tenant's request metrics over a time range, in uniform buckets, one series per category —
 * the answer to {@code GET /api/v1/usage/series?schema=&from=&to=&grain=&category=…}.
 *
 * <p>web-global stores minute rows for recent usage and hour and day rows for older usage. This
 * shape hides that: every bucket is one {@link #grain()} wide, and each is the fold of every stored
 * row whose window starts inside it, so a chart's spacing is even across the whole range.</p>
 *
 * <p><b>Only what was asked for comes back.</b> The response carries exactly the categories the
 * request named, in the order it named them, and is in exactly the grain it named. A request that
 * omits {@code category} means {@link UsageCategory#HTTP} and {@link UsageCategory#FILE}, and will
 * keep meaning exactly those two even after a later release adds a category; likewise a grain added
 * later is answered only to a caller that asks for it. So a consumer built against this release
 * never receives an enum value it cannot bind, however far web-global has moved on.</p>
 *
 * <p><b>A bucket is never finer than the data under it.</b> A stored row is never split: a day row
 * folded into hour buckets would land whole in its midnight bucket and leave 23 false zeros. So an
 * {@link UsageGrain#HOUR} range reaches back no further than web-global keeps hour (or finer) rows,
 * and web-global refuses one that does rather than answering it.</p>
 *
 * <p><b>Every series covers every bucket.</b> A series holds exactly one point per bucket of
 * {@code [from, to)}, oldest first, and a bucket with no traffic is a zero point rather than a gap.
 * The constructor refuses anything else, so a consumer may pair the categories' points by index and
 * never has to fill holes. {@code from} and {@code to} are therefore whole buckets: aligned to the
 * grain, as web-global resolved the request, which may be wider than what was asked.</p>
 *
 * <p><b>The newest buckets may be partial.</b> {@code asOf} is when web-global folded the rows. A
 * bucket that ends after it (see {@link #isPartial(UsageSeriesPoint)}) holds only what had arrived by
 * then — and producers report each minute with a delay — so its figures, zero included, are a lower
 * bound rather than the bucket's usage. Only a bucket that ended by {@code asOf} reads a zero as no
 * traffic. A chart should mark or skip partial buckets rather than draw them as a dip.</p>
 *
 * @param schemaName the canonical {@code U<seqnum>} the figures belong to
 * @param from       the first bucket's start, inclusive, aligned to {@code grain}
 * @param to         the end of the last bucket, exclusive, aligned to {@code grain}
 * @param asOf       when web-global folded the figures; never before {@code from}, and not aligned
 *                   to anything
 * @param grain      the width of every bucket
 * @param series     one series per category requested, in request order, each category at most
 *                   once
 */
public record UsageSeriesResponse(
		String schemaName,
		Instant from,
		Instant to,
		Instant asOf,
		UsageGrain grain,
		List<UsageCategorySeries> series) {

	/**
	 * Refuses a missing reference component by name, folds an absent series list into its empty
	 * form, takes a defensive copy of a present one, and refuses a response that breaks the bucket
	 * contract above.
	 *
	 * @throws NullPointerException     when {@code schemaName}, {@code from}, {@code to},
	 *                                  {@code asOf} or {@code grain} is missing, naming it
	 * @throws IllegalArgumentException when {@code from} or {@code to} is not aligned to the grain,
	 *                                  {@code to} is before {@code from}, {@code asOf} is before
	 *                                  {@code from}, a category appears twice, or a series does not
	 *                                  hold exactly one point per bucket at that bucket's start
	 */
	public UsageSeriesResponse {
		Objects.requireNonNull(schemaName, "schemaName");
		Objects.requireNonNull(from, "from");
		Objects.requireNonNull(to, "to");
		Objects.requireNonNull(asOf, "asOf");
		Objects.requireNonNull(grain, "grain");
		series = series == null ? List.of() : List.copyOf(series);
		final long width = grain.width().toMillis();
		requireAligned("from", from, width);
		requireAligned("to", to, width);
		if (to.isBefore(from)) {
			throw new IllegalArgumentException("to " + to + " is before from " + from);
		}
		if (asOf.isBefore(from)) {
			throw new IllegalArgumentException("asOf " + asOf + " is before from " + from);
		}
		final long buckets = Duration.between(from, to).toMillis() / width;
		final Set<UsageCategory> seen = EnumSet.noneOf(UsageCategory.class);
		for (final UsageCategorySeries line : series) {
			if (!seen.add(line.category())) {
				throw new IllegalArgumentException("category " + line.category() + " appears twice");
			}
			requireEveryBucket(line, from, width, buckets);
		}
	}

	/**
	 * The categories this response carries, in series order.
	 *
	 * @return each series' category
	 */
	public List<UsageCategory> categories() {
		return series.stream().map(UsageCategorySeries::category).toList();
	}

	/**
	 * Whether a point's bucket was still open when the figures were folded, so that its figures are
	 * a lower bound rather than the bucket's usage.
	 *
	 * @param point a point of this response
	 * @return {@code true} when the point's bucket ends after {@link #asOf()}
	 */
	public boolean isPartial(final UsageSeriesPoint point) {
		return point.start().plus(grain.width()).isAfter(asOf);
	}

	private static void requireAligned(final String name, final Instant instant, final long width) {
		if (Math.floorMod(instant.toEpochMilli(), width) != 0 || instant.getNano() % 1_000_000 != 0) {
			throw new IllegalArgumentException(name + " " + instant + " is not on a bucket boundary");
		}
	}

	private static void requireEveryBucket(final UsageCategorySeries line, final Instant from,
			final long width, final long buckets) {
		final List<UsageSeriesPoint> points = line.points();
		if (points.size() != buckets) {
			throw new IllegalArgumentException("series " + line.category() + " has " + points.size()
					+ " points for " + buckets + " buckets");
		}
		for (int i = 0; i < points.size(); i++) {
			final Instant expected = from.plusMillis(i * width);
			if (!points.get(i).start().equals(expected)) {
				throw new IllegalArgumentException("series " + line.category() + " point " + i
						+ " starts at " + points.get(i).start() + ", not " + expected);
			}
		}
	}
}
