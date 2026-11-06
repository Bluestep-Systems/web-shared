package dev.bluestep.global.dto.ai;

import java.util.Set;

/**
 * The keys BlueStep already meters, spelled once so every repository that has to name one names it the same way.
 *
 * <p><b>Constants, deliberately not an enum.</b> A metered key is the provider's to define — it decides what it
 * charges for, and a closed type here would mean a rate row for a real charge could be seeded and then never read,
 * which is the exact failure this vocabulary was opened to end. So a provider adapter may emit any key at all; these
 * are the ones more than one repository has to spell identically.</p>
 *
 * <p><b>What sharing these does and does not buy.</b> They keep a typo out of the emitting side at compile time, and
 * no further: the pricing side never reads this class, because it joins on {@code unit_key} values read from
 * {@code ai_unit_rate} — values that reach the database as string literals in a Liquibase changeset. So the drift
 * that costs money is a constant here disagreeing with a changeset literal there, and no Java linkage can catch it;
 * a test that parses the seeded literals and diffs them against {@link #KNOWN} is what actually holds the two
 * together. Note also that these are compile-time constants, so consumers inline them: respelling one is a
 * coordinated release across every consumer, never a patch, and under staggered deploys the window in which an old
 * emitter still sends the old spelling is long.</p>
 *
 * <p>Keys are lower_snake_case (the {@code chk_ai_unit_rate_unit_key} constraint enforces the shape) and name the
 * charge rather than the model: a cache write's time-to-live is part of what is charged for, so
 * {@link #CACHE_WRITE_5M} and {@link #CACHE_WRITE_1H} are two keys in one {@code (CACHE_WRITE, TOKEN)} cell rather
 * than one key that means different things by model.</p>
 *
 * <p><b>Two patterns, two places.</b> {@link #KEY_PATTERN} governs every key that becomes, or selects, a rate row
 * ({@code AiUnitRateRequest.unitKey}, the DB constraint, anything that seeds or looks up {@code ai_unit_rate}).
 * {@link #REPORTED_KEY_PATTERN} governs only the keys of a completion report, which may additionally carry a
 * reserved marker such as {@link #INVALID_BREAKDOWN_MARKER}.</p>
 */
public final class UnitKeys {

	/**
	 * The shape every metered key must have, spelled here because two sides enforce it.
	 *
	 * <p>Identical to the {@code chk_ai_unit_rate_unit_key} DB constraint. A key that fails this is not merely
	 * rejected on the way in — it is <em>unseedable</em>, so a charge reported under one can never be given a rate
	 * and stays unpriced however many times an operator retries.</p>
	 */
	public static final String KEY_PATTERN = "^[a-z0-9]+(_[a-z0-9]+)*$";

	/**
	 * The prefix that makes a reported key a reserved marker rather than a unit key.
	 *
	 * <p>Forbidden by {@link #KEY_PATTERN}, so a marker can never be seeded into, or select, a rate row.</p>
	 */
	public static final String MARKER_PREFIX = "!";

	/**
	 * The shape a key may have in a completion report: a {@link #KEY_PATTERN} key, or {@link #MARKER_PREFIX}
	 * followed by a body of that same shape.
	 *
	 * <p>Applies only to the keys of {@code AiCompletionRequest}. Everywhere a key becomes or selects a rate row,
	 * {@link #KEY_PATTERN} stays the rule.</p>
	 */
	public static final String REPORTED_KEY_PATTERN = "^!?[a-z0-9]+(_[a-z0-9]+)*$";

	/**
	 * The marker reported when a turn's token breakdown could not be trusted.
	 *
	 * <p>It is not a unit key: it is never seedable, never priced, and has no rate row. It exists so the metering
	 * side can tell that the turn's breakdown is invalid (and handle it as such) instead of the whole report being
	 * rejected and the turn never metered.</p>
	 */
	public static final String INVALID_BREAKDOWN_MARKER = MARKER_PREFIX + "invalid_token_breakdown";

	/**
	 * The marker recorded when a completion's per-request metadata ({@code AiCompletionRequest}'s {@code "rm"}) is
	 * unusable: not aligned with its per-request amounts, or failing validation. The amounts are recorded without it.
	 */
	public static final String INVALID_REQUEST_META_MARKER = MARKER_PREFIX + "invalid_request_meta";

	/**
	 * The marker recorded when at least one of a turn's provider requests did not finish cleanly, so its amounts are
	 * what was observed rather than what the provider finally reported.
	 */
	public static final String PARTIAL_USAGE_MARKER = MARKER_PREFIX + "partial_usage";

	/**
	 * The marker recorded on a turn that was authorized and never completed, closed by web-global after it could no
	 * longer be in flight.
	 */
	public static final String ABANDONED_MARKER = MARKER_PREFIX + "abandoned";

	/**
	 * The marker recorded when a completion's itemization is inconsistent (its per-request amounts do not sum to the
	 * turn's, or cannot be used), so the turn was priced as a single request from its turn amounts.
	 */
	public static final String INVALID_COMPLETION_MARKER = MARKER_PREFIX + "invalid_completion";

	/**
	 * The marker a caller reports when the provider gave no usage for an audio request and the caller measured the
	 * audio's duration itself and reported that as {@link #AUDIO_INPUT_MILLIS}: the amount is the caller's
	 * measurement, not the provider's figure. Provider usage, when there is any, is always reported instead.
	 */
	public static final String MEASURED_AUDIO_MARKER = MARKER_PREFIX + "measured_audio";

	/** Matches {@code ai_unit_rate.unit_key}'s column width; a longer key is likewise unseedable. */
	public static final int MAX_KEY_LENGTH = 64;

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

	/**
	 * Audio input on models that bill it by duration.
	 *
	 * <p>Milliseconds, not seconds: see {@link UnitMeasure#MILLISECOND} — a sub-unit amount truncates to zero and
	 * a zero amount is dropped, so the finest measure is the only one that cannot silently discard a short clip.
	 * The rate is re-based to match, which is arithmetic rather than a change in what is charged.</p>
	 */
	public static final String AUDIO_INPUT_MILLIS = "audio_input_millis";

	/** Completion tokens. */
	public static final String TEXT_OUTPUT = "text_output";

	/** Audio produced as output. */
	public static final String AUDIO_OUTPUT = "audio_output";

	/**
	 * Every key named above.
	 *
	 * <p>Not a closed vocabulary — a provider may meter something none of these name, and the rate table will hold
	 * it. This exists so the changeset-versus-constant drift described in the class javadoc can be asserted, and so
	 * an admin UI can offer the known spellings without forbidding a new one.</p>
	 */
	public static final Set<String> KNOWN = Set.of(
			TEXT_INPUT,
			CACHED_INPUT,
			CACHED_AUDIO_INPUT,
			CACHE_WRITE_5M,
			CACHE_WRITE_1H,
			AUDIO_INPUT_TOKENS,
			AUDIO_INPUT_MILLIS,
			TEXT_OUTPUT,
			AUDIO_OUTPUT);

	private UnitKeys() {
	}
}
