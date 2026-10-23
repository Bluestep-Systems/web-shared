package dev.bluestep.global.dto.usage;

/**
 * Every usage figure a tenant can be shown, by name: the vocabulary the usage chart's metric picker
 * offers and that usage thresholds key on.
 *
 * <p>Each constant says its unit and how it behaves over a window, because a threshold on it has to
 * know. A <em>cumulative</em> metric adds up across a window, so a limit on it is a budget; a
 * <em>peak</em> or <em>average</em> is computed over the window rather than summed; a <em>level</em>
 * is a reading at a moment and does not accumulate at all. The request metrics are reported per
 * {@link UsageCategory}; the storage levels belong to no category.</p>
 *
 * <p>Thread CPU time and allocated bytes are deliberately absent. Producers report them
 * ({@link UsageWindow#cpuMillis()}, {@link UsageWindow#allocBytes()}), but they are internal metrics,
 * never shown to a tenant or priced, so they have no name here.</p>
 *
 * <p>Serialized under its constant name.</p>
 */
public enum UsageMetric {

	/** Completed requests, a count. Cumulative: {@link UsageSeriesPoint#hits()} summed over the window. */
	HITS,

	/**
	 * Wall-clock time spent serving requests, in milliseconds. Cumulative:
	 * {@link UsageSeriesPoint#pageMillis()} summed over the window.
	 */
	PAGE_MILLIS,

	/**
	 * Mean wall-clock time per request, in milliseconds. Derived, not stored: {@link #PAGE_MILLIS}
	 * divided by {@link #HITS} over the same window, and undefined for a window with no hits. Not
	 * cumulative — two windows' averages do not add.
	 */
	PAGE_MILLIS_AVG,

	/**
	 * The slowest single request's wall-clock time, in milliseconds. A maximum, not cumulative:
	 * {@link UsageSeriesPoint#pageMillisMax()} taken as the largest over the window.
	 */
	PAGE_MILLIS_PEAK,

	/**
	 * Time spent in database calls while serving requests, in milliseconds, as the request's own
	 * threads measured it. Cumulative: {@link UsageSeriesPoint#dbMillis()} summed over the window.
	 */
	DB_MILLIS,

	/**
	 * Request body bytes received. Cumulative: {@link UsageSeriesPoint#requestBytes()} summed over
	 * the window.
	 */
	REQUEST_BYTES,

	/**
	 * Response bytes sent. Cumulative: {@link UsageSeriesPoint#responseBytes()} summed over the
	 * window.
	 */
	RESPONSE_BYTES,

	/**
	 * The tenant's database size, in bytes: the {@code db} storage meter. A level, read from the
	 * latest storage sample; it does not accumulate over a window.
	 */
	STORAGE_DB_BYTES,

	/**
	 * The tenant's stored files, in bytes, as the tenant sees them: the {@code file_logical} storage
	 * meter. A level, read from the latest storage sample; it does not accumulate over a window.
	 */
	STORAGE_FILE_BYTES
}
