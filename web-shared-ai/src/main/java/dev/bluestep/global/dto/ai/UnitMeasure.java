package dev.bluestep.global.dto.ai;

/**
 * The countable quantity a rate is expressed per.
 *
 * <p>Its own axis rather than something a {@link UnitCategory} implies, because one category is billed in more than
 * one measure: audio input is charged per {@link #TOKEN} by realtime models and per {@link #MILLISECOND} by
 * whisper-style transcription. Like the category this is descriptive — what a rate row joins on is its
 * {@code unit_key} — and exists so aggregation can group across providers.</p>
 *
 * <p><b>Amounts are whole units.</b> There is one time measure rather than a family of them, and it is the finest
 * one, because an amount is a {@code long} and a coarser measure silently truncates: a container held forty minutes
 * is zero hours, and a zero amount is dropped rather than billed or reported. Anything billed per minute or per hour
 * is expressed here in milliseconds with its rate re-based to match — the rate column is micro-dollars per
 * <em>million</em> units, so re-basing is arithmetic ({@code USD-per-hour / 3_600_000 × 1e12}) and loses nothing.
 * A measure whose real consumption can fall below one whole unit does not belong here; add a finer one instead of
 * rounding into it.</p>
 *
 * <p>Mirrored by the {@code chk_ai_unit_rate_unit_measure} DB constraint. The two are kept in step by a test that
 * diffs this enum against {@code pg_constraint}, not by hand — but changing either still means changing both in the
 * same release.</p>
 */
public enum UnitMeasure {
	/** Provider tokens, input or output. */
	TOKEN,
	/** Milliseconds of wall-clock time — media duration, or a resource billed by how long it is held. */
	MILLISECOND,
	/** Whole occurrences — a per-request or per-search fee. */
	COUNT;
}
