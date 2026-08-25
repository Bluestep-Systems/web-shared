package dev.bluestep.global.dto.ai;

/**
 * The countable quantity a rate is expressed per.
 *
 * <p>Its own axis rather than something a {@link UnitCategory} implies, because one category is billed in more than
 * one measure: audio input is charged per {@link #TOKEN} by realtime models and per {@link #SECOND} by
 * whisper-style transcription. Like the category this is descriptive — what a rate row joins on is its
 * {@code unit_key} — and exists so aggregation can group across providers.</p>
 *
 * <p>Mirrored by the {@code chk_ai_unit_rate_unit_measure} DB constraint; keep the two in sync when a measure is
 * added.</p>
 */
public enum UnitMeasure {
	/** Provider tokens, input or output. */
	TOKEN,
	/** Wall-clock seconds of media, for duration-billed audio. */
	SECOND,
	/** Whole occurrences — a per-request or per-search fee. */
	COUNT,
	/** Wall-clock hours, for a resource billed by how long it is held rather than by what passes through it. */
	HOUR;
}
