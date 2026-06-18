package dev.bluestep.global.dto.ai;

/**
 * The countable quantity a {@link PricedUnit}'s rate is expressed per. The same {@link UnitCategory}
 * can be billed in different measures — audio input is charged per {@link #TOKEN} by realtime models
 * but per {@link #SECOND} by whisper-style transcription — which is why measure is its own axis rather
 * than implied by the category. Mirrored by the {@code chk_ai_unit_rate_unit_measure} DB constraint
 * (changeset 006). Add a value here (and to the constraint) as providers bill in new units — e.g.
 * per-request fees or per-image charges.
 */
public enum UnitMeasure {
	/** Provider tokens (input or output). */
	TOKEN,
	/** Wall-clock seconds of media (duration-billed audio/video). */
	SECOND;
}
