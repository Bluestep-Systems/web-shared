package dev.bluestep.global.dto.ai;

/**
 * What a metered unit is: the content modality and request direction of a billed amount.
 *
 * <p>Descriptive rather than identifying. A rate row joins to a turn's amount vector on its {@code unit_key}, which
 * is a provider-defined string; this axis and {@link UnitMeasure} exist so that aggregation queries can group
 * across providers regardless of what each one calls the thing — {@code SUM(cost) WHERE unit_category =
 * 'AUDIO_INPUT'} works whether the key was {@code audio_input_tokens} or {@code audio_input_seconds}. Two keys may
 * therefore share a cell: a five-minute and a one-hour cache write are both {@code (CACHE_WRITE, TOKEN)}.</p>
 *
 * <p>Mirrored by the {@code chk_ai_unit_rate_unit_category} DB constraint; keep the two in sync when a category is
 * added.</p>
 */
public enum UnitCategory {
	/** Prompt tokens read fresh — neither served from a cache nor written into one. */
	TEXT_INPUT,
	/** Input tokens served from the provider's prompt cache, billed below the base input rate. */
	CACHED_INPUT,
	/**
	 * Input tokens written <em>into</em> the provider's prompt cache, billed above the base input rate.
	 *
	 * <p>Its own category rather than a shade of {@link #CACHED_INPUT}, because the two differ by a factor of
	 * 12.5 at Anthropic and move in opposite directions from base: a read is 0.1x and a write is 1.25x. Folded
	 * into the read category a write under-bills by that factor; folded into {@link #TEXT_INPUT} it under-bills
	 * by the write premium.</p>
	 */
	CACHE_WRITE,
	/** Audio supplied as input, whether tokenized by a realtime model or duration-billed by transcription. */
	AUDIO_INPUT,
	/** Completion tokens. */
	TEXT_OUTPUT,
	/** Audio produced as output. */
	AUDIO_OUTPUT,
	/**
	 * Provider-side tooling billed alongside the turn — a hosted web search, a code-interpreter container.
	 *
	 * <p>Metered per call or per hour rather than per token, which is what {@link UnitMeasure#COUNT} and
	 * {@link UnitMeasure#HOUR} are for. Nothing emits these today; the category exists so that the first
	 * provider that bills one has somewhere to land other than a new enum constant in a hotfix.</p>
	 */
	TOOL_USE;
}
