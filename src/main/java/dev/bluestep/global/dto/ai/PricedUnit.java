package dev.bluestep.global.dto.ai;

/**
 * A priced unit on an {@code ai_unit_rate} row: one of the known, billable {@code (category, measure)}
 * combinations a turn can be charged for. Each constant pairs a {@link UnitCategory} (what is metered —
 * modality and direction) with the {@link UnitMeasure} it is counted in; a turn's cost is the sum over
 * the units it consumed of {@code amount × rate}.
 *
 * <p>This is a closed enum rather than an open {@code (category, measure)} pair, so only meaningful
 * combinations exist — there is no "text measured in seconds". The same category may appear under more
 * than one measure: audio input is billed per {@link UnitMeasure#TOKEN} by realtime models
 * ({@link #AUDIO_INPUT_TOKENS}) but per {@link UnitMeasure#SECOND} by whisper-style transcription
 * ({@link #AUDIO_INPUT_SECONDS}). (The former flat {@code AUDIO_SECOND} constant was really "audio
 * input, measured in seconds".)</p>
 *
 * <p>Lives in web-shared so it is both the JPA-persisted column type in web-global ({@code AiUnitRate})
 * and the wire type on {@code AiUnitRateRequest}/{@code AiUnitRateResponse}. The
 * {@code chk_ai_unit_rate_priced_unit} DB constraint (changeset 006) mirrors these names; keep the two
 * in sync when a unit is added.</p>
 */
public enum PricedUnit {
	/** Text input tokens — the unattributed remainder of the released input aggregate. */
	TEXT_INPUT(UnitCategory.TEXT_INPUT, UnitMeasure.TOKEN),
	/** Input tokens served from cache, billed at the (cheaper) cached rate. */
	CACHED_INPUT(UnitCategory.CACHED_INPUT, UnitMeasure.TOKEN),
	/** Audio input tokens — realtime/audio models that tokenize audio. */
	AUDIO_INPUT_TOKENS(UnitCategory.AUDIO_INPUT, UnitMeasure.TOKEN),
	/** Audio input billed by duration — whisper-style transcription priced per second. */
	AUDIO_INPUT_SECONDS(UnitCategory.AUDIO_INPUT, UnitMeasure.SECOND),
	/** Text output tokens — the unattributed remainder of the released output aggregate. */
	TEXT_OUTPUT(UnitCategory.TEXT_OUTPUT, UnitMeasure.TOKEN),
	/** Audio output tokens. */
	AUDIO_OUTPUT(UnitCategory.AUDIO_OUTPUT, UnitMeasure.TOKEN);

	private final UnitCategory category;
	private final UnitMeasure measure;

	PricedUnit(UnitCategory category, UnitMeasure measure) {
		this.category = category;
		this.measure = measure;
	}

	/** What this unit meters — modality and direction. */
	public UnitCategory category() {
		return category;
	}

	/** The quantity this unit's rate is expressed per. */
	public UnitMeasure measure() {
		return measure;
	}

	/**
	 * The unit for a {@code (category, measure)} pair. Only the combinations declared above are valid —
	 * an unmodelled pair (e.g. text billed per second) throws, which is the point of the closed enum.
	 */
	public static PricedUnit from(UnitCategory category, UnitMeasure measure) {
		for (PricedUnit unit : values()) {
			if (unit.category == category && unit.measure == measure) {
				return unit;
			}
		}
		throw new IllegalArgumentException("No PricedUnit for category=" + category + " measure=" + measure);
	}
}
