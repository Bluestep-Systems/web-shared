package dev.bluestep.global.dto.ai;

/**
 * The keys BlueStep already meters, spelled once so the emitting and pricing sides cannot disagree about them.
 *
 * <p><b>Constants, deliberately not an enum.</b> A metered key is the provider's to define — it decides what it
 * charges for, and a closed type here would mean a rate row for a real charge could be seeded and then never read,
 * which is the exact failure this vocabulary was opened to end. So a provider adapter may emit any key at all; these
 * are the ones more than one repository has to spell identically, and a shared constant is what keeps a typo from
 * becoming a silently unpriced unit.</p>
 *
 * <p>Keys are lower_snake_case and name the charge rather than the model: a cache write's time-to-live is part of
 * what is charged for, so {@link #CACHE_WRITE_5M} and a future one-hour key are two keys in one
 * {@code (CACHE_WRITE, TOKEN)} cell rather than one key that means different things by model.</p>
 */
public final class UnitKeys {

	/** Prompt tokens read fresh — not served from a cache, not written into one. */
	public static final String TEXT_INPUT = "text_input";

	/** Prompt tokens served from the provider's cache. */
	public static final String CACHED_INPUT = "cached_input";

	/**
	 * Audio input served from the provider's cache, on a model that prices cached audio apart from cached text.
	 *
	 * <p>Its own key rather than a shade of {@link #CACHED_INPUT} because the two are priced five-fold apart on
	 * the models that separate them, and one key per model could only ever carry one of the two rates.</p>
	 */
	public static final String CACHED_AUDIO_INPUT = "cached_audio_input";

	/**
	 * Prompt tokens written into a five-minute cache.
	 *
	 * <p>The time-to-live is in the key because it is in the price: Anthropic charges 1.25x base for a five-minute
	 * write and 2x for an hour, and nothing about a token says which was bought. One key per TTL keeps that a
	 * lookup rather than a rule someone has to remember.</p>
	 */
	public static final String CACHE_WRITE_5M = "cache_write_5m";

	/**
	 * Prompt tokens written into a one-hour cache.
	 *
	 * <p>Rated but not yet metered: the client asks for an ephemeral cache without naming a TTL, which buys the
	 * five-minute one. The key exists so that whoever adds the longer request finds the spelling already agreed
	 * rather than inventing a second one.</p>
	 */
	public static final String CACHE_WRITE_1H = "cache_write_1h";

	/** Audio input on models that tokenize it. */
	public static final String AUDIO_INPUT_TOKENS = "audio_input_tokens";

	/** Audio input on models that bill it by duration. */
	public static final String AUDIO_INPUT_SECONDS = "audio_input_seconds";

	/** Completion tokens. */
	public static final String TEXT_OUTPUT = "text_output";

	/** Audio produced as output. */
	public static final String AUDIO_OUTPUT = "audio_output";

	private UnitKeys() {
	}
}
