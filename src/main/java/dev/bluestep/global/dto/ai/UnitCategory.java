package dev.bluestep.global.dto.ai;

/**
 * What a {@link PricedUnit} meters: the content modality and request direction of a billed unit.
 * Paired with a {@link UnitMeasure} (the quantity it is counted in) on each {@code ai_unit_rate} row.
 * Mirrored by the {@code chk_ai_unit_rate_unit_category} DB constraint (changeset 006); keep the two
 * in sync when a category is added.
 */
public enum UnitCategory {
	/** Text/other prompt tokens — the unattributed remainder of the released input aggregate. */
	TEXT_INPUT,
	/** Input tokens served from the provider's prompt cache, billed at the cached rate. */
	CACHED_INPUT,
	/** Audio supplied as input (tokenized by realtime models, or duration-billed by transcription). */
	AUDIO_INPUT,
	/** Text/other completion tokens — the unattributed remainder of the released output aggregate. */
	TEXT_OUTPUT,
	/** Audio produced as output. */
	AUDIO_OUTPUT;
}
