package dev.bluestep.global.dto.ai;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalInt;

import org.jspecify.annotations.Nullable;

import com.fasterxml.jackson.annotation.JsonProperty;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

/**
 * Completion report for a turn.
 *
 * <p>{@code totalInputTokens}/{@code totalOutputTokens} are the released aggregates, recorded whatever else is
 * reported. {@code unitAmounts} is what the turn is <em>priced</em> from: an open map from a metered key
 * ({@link UnitKeys}) to the amount consumed under it, emitted by each provider adapter under the keys that provider
 * actually meters. {@code stopReason} and {@code errorMessage} are absent on turns that ended without one.</p>
 *
 * <p><b>The vector is open because the provider decides what it charges for.</b> The four fixed breakdown
 * components below were the previous shape, and their fixedness was the defect: a charge with no component to land
 * in was not mispriced but invisible, and an amount could only ever be attributed to a unit somebody had already
 * thought of. A map costs the guarantee that every key is known and buys the guarantee that no charge is
 * unreportable, which is the trade the pricing side is built to take — an amount whose key has no rate row is
 * recorded as unpriced rather than silently priced at zero.</p>
 *
 * <p><b>The vector is optional because absent and empty are different facts.</b> A caller that predates the
 * vocabulary sends no vector at all; a caller that has one may still have metered nothing. Folding both into an
 * empty map would make the pricing side unable to tell a legacy report from a current one, and it would take the
 * legacy branch for a current caller whose adapter failed to populate the vector — pricing audio tokens at the text
 * rate while {@code unpriced_units} claimed the turn was fully priced. Absence is therefore carried as absence.</p>
 *
 * <p><b>Per-request amounts are optional for the same reason.</b> A turn with tool iterations makes several provider
 * API requests, and a price that depends on the size of one request (a threshold-spec model) cannot be computed
 * from the sum. {@code requestUnitAmounts} carries the amounts request by request. Absent means an older caller,
 * and the pricing side prices the turn as one request, an upper bound for such a model, so it never under-bills.</p>
 *
 * <p><b>Per-request metadata rides beside the amounts, never inside them.</b> {@code requestMeta} says when each
 * request ran and which model answered, which is what reconciling against the provider's own usage report needs.
 * It is a parallel list rather than extra keys in each amount map so the amount vocabulary stays amounts only, and so
 * metadata that turns out wrong can be set aside without touching what is billed. Nothing about it is thrown while
 * binding: a bad timestamp or model name is a constraint violation, so the amounts are never lost for it.</p>
 *
 * <p><b>The legacy components are a transition lane, not a second way of saying the same thing.</b> web and
 * web-global are separate deployments and web rolls out per namespace, so old and new callers report to the same
 * gate concurrently and for a long time. A pre-vector pod reports through
 * {@code audioSeconds}/{@code audioInputTokens}/{@code cachedInputTokens}/{@code audioOutputTokens} and must still
 * be priced from them — less accurately, since they cannot express a cache write, which is why the gate marks what
 * it priced that way. A caller emitting the vector may also send the components; the vector wins whenever it is
 * present. They come out once no deployed caller sends them.</p>
 *
 * @param trackingId        the preflight-issued id this completes
 * @param totalInputTokens  released input aggregate
 * @param totalOutputTokens released output aggregate
 * @param totalLatencyMs    wall-clock time of the turn
 * @param iterationsUsed    tool-loop iterations the turn took
 * @param stopReason        why the turn ended, where the provider said
 * @param errorMessage      what failed, on a turn that did
 * @param unitAmounts       metered key to amount consumed; what the turn is priced from, absent on a legacy caller
 * @param audioSeconds      legacy lane: a pre-vector caller's audio duration
 * @param audioInputTokens  legacy lane: a pre-vector caller's audio input
 * @param cachedInputTokens legacy lane: a pre-vector caller's cached input
 * @param audioOutputTokens legacy lane: a pre-vector caller's audio output
 * @param requestUnitAmounts one amount map per provider API request in the turn, in order, under the same
 *                          vocabulary and per-entry validation as {@code unitAmounts}; for every key the elements
 *                          sum to {@code unitAmounts}' value (web-global rejects a report that breaks that, so
 *                          the DTO does not). Absent on a caller that predates per-request reporting, which the
 *                          pricing side then treats as a single request
 * @param requestMeta       one {@link AiRequestMeta} per element of {@code requestUnitAmounts}, in the same order:
 *                          when each request ran, the model that answered and the provider's id for it. Present only
 *                          alongside {@code requestUnitAmounts} and the same size; web-global checks that alignment,
 *                          not the DTO, and on a mismatch records the turn without the metadata and marks it
 *                          {@link UnitKeys#INVALID_REQUEST_META_MARKER}. Absent on a caller that predates it
 */
public record AiCompletionRequest(
		@NotBlank @JsonProperty("t") String trackingId,
		@JsonProperty("i") int totalInputTokens,
		@JsonProperty("o") int totalOutputTokens,
		@JsonProperty("l") long totalLatencyMs,
		@JsonProperty("n") int iterationsUsed,
		@JsonProperty("s") @Nullable String stopReason,
		@JsonProperty("e") @Nullable String errorMessage,
		@JsonProperty("u") @Size(max = MAX_UNIT_KEYS) @Nullable Map<
				@NotBlank @Size(max = UnitKeys.MAX_KEY_LENGTH) @Pattern(regexp = UnitKeys.REPORTED_KEY_PATTERN) String,
				@PositiveOrZero Long> unitAmounts,
		@JsonProperty("a") @Nullable Integer audioSeconds,
		@JsonProperty("ai") @Nullable Integer audioInputTokens,
		@JsonProperty("ci") @Nullable Integer cachedInputTokens,
		@JsonProperty("ao") @Nullable Integer audioOutputTokens,
		@JsonProperty("r") @Size(max = MAX_REQUESTS) @Nullable List<
				@NotNull @Size(max = MAX_UNIT_KEYS) Map<
						@NotBlank @Size(max = UnitKeys.MAX_KEY_LENGTH) @Pattern(regexp = UnitKeys.REPORTED_KEY_PATTERN) String,
						@PositiveOrZero Long>> requestUnitAmounts,
		@JsonProperty("rm") @Size(max = MAX_REQUESTS) @Nullable List<@NotNull @Valid AiRequestMeta> requestMeta) {

	/**
	 * How many distinct keys one turn may report.
	 *
	 * <p>Far above any real turn — the richest provider meters a handful — and low enough that a runaway emitter
	 * is rejected rather than persisted. Without a bound the map is unbounded input on a path that writes to the
	 * billing table.</p>
	 */
	public static final int MAX_UNIT_KEYS = 64;

	/**
	 * How many provider API requests one turn may itemize in {@code requestUnitAmounts}.
	 *
	 * <p>Sized from the monolith's tool-loop hard cap of 200 iterations (one request each) with headroom for a
	 * retried request, and low enough that a runaway emitter is rejected rather than persisted.</p>
	 */
	public static final int MAX_REQUESTS = 256;

	/**
	 * Takes a checked, defensive copy of a present vector; an absent one (a pre-vector caller omits {@code "u"}
	 * on every report) stays absent.
	 *
	 * <p>Copying is the same decision the map
	 * itself forces: the amounts are what a turn is priced from, and a caller that could mutate them after
	 * reporting could change a bill after it was rendered.</p>
	 *
	 * <p><b>Entries are checked here rather than left to bean validation</b>, because Jackson invokes this
	 * constructor while binding and validation runs afterwards on the object that binding produced — so a null
	 * amount would reach {@link Map#copyOf} first and surface as a bare {@code NullPointerException} inside a
	 * {@code ValueInstantiationException}, naming nothing. A null amount is bad code rather than a state a turn
	 * can be in, so it is rejected; the message names the key, because the whole point of failing here is that
	 * somebody can tell which one.</p>
	 *
	 * <p>{@code requestMeta} is copied the same way. Unlike an amount, a null element is not thrown on: it is a
	 * statement about timing, not about what was consumed, so it binds as {@link AiRequestMeta#ABSENT}, whose missing
	 * fields bean validation reports like every other fault in the metadata. A wrong or missing element never costs
	 * the amounts.</p>
	 *
	 * @throws IllegalArgumentException if any key or amount is null, any amount is negative, or any element of
	 *                                  {@code requestUnitAmounts} is null
	 */
	public AiCompletionRequest {
		unitAmounts = unitAmounts == null ? null : checkedCopy(unitAmounts);
		requestUnitAmounts = requestUnitAmounts == null ? null : checkedRequests(requestUnitAmounts);
		requestMeta = requestMeta == null ? null : checkedMeta(requestMeta);
	}

	/**
	 * The pre-per-request shape of the constructor, kept so a caller built against 6.1.0 and earlier still
	 * compiles. Leaves {@code requestUnitAmounts} and {@code requestMeta} absent.
	 */
	public AiCompletionRequest(String trackingId, int totalInputTokens, int totalOutputTokens, long totalLatencyMs,
			int iterationsUsed, @Nullable String stopReason, @Nullable String errorMessage,
			@Nullable Map<String, Long> unitAmounts, @Nullable Integer audioSeconds,
			@Nullable Integer audioInputTokens, @Nullable Integer cachedInputTokens,
			@Nullable Integer audioOutputTokens) {
		this(trackingId, totalInputTokens, totalOutputTokens, totalLatencyMs, iterationsUsed, stopReason,
				errorMessage, unitAmounts, audioSeconds, audioInputTokens, cachedInputTokens,
				audioOutputTokens, null, null);
	}

	/**
	 * The 6.2.x shape of the constructor, kept so a caller built against it still compiles. Leaves
	 * {@code requestMeta} absent.
	 */
	public AiCompletionRequest(String trackingId, int totalInputTokens, int totalOutputTokens, long totalLatencyMs,
			int iterationsUsed, @Nullable String stopReason, @Nullable String errorMessage,
			@Nullable Map<String, Long> unitAmounts, @Nullable Integer audioSeconds,
			@Nullable Integer audioInputTokens, @Nullable Integer cachedInputTokens,
			@Nullable Integer audioOutputTokens, @Nullable List<Map<String, Long>> requestUnitAmounts) {
		this(trackingId, totalInputTokens, totalOutputTokens, totalLatencyMs, iterationsUsed, stopReason,
				errorMessage, unitAmounts, audioSeconds, audioInputTokens, cachedInputTokens,
				audioOutputTokens, requestUnitAmounts, null);
	}

	private static List<AiRequestMeta> checkedMeta(List<AiRequestMeta> meta) {
		List<AiRequestMeta> copy = new ArrayList<>(meta.size());
		for (@Nullable AiRequestMeta element : meta) {
			// A null element stands for an element with nothing in it, which validation reports field by field.
			copy.add(element == null ? AiRequestMeta.ABSENT : element);
		}
		return List.copyOf(copy);
	}

	private static List<Map<String, Long>> checkedRequests(List<Map<String, Long>> requests) {
		List<Map<String, Long>> copy = new ArrayList<>(requests.size());
		int index = 0;
		for (Map<String, Long> request : requests) {
			if (request == null) {
				throw new IllegalArgumentException("request unit amounts contain a null entry at index " + index);
			}
			copy.add(checkedCopy(request));
			index++;
		}
		return List.copyOf(copy);
	}

	private static Map<String, Long> checkedCopy(Map<String, Long> amounts) {
		amounts.forEach((key, amount) -> {
			if (key == null) {
				throw new IllegalArgumentException("unit amounts contain a null key");
			}
			if (amount == null) {
				throw new IllegalArgumentException("unit amount for key '" + key + "' is null");
			}
			if (amount < 0) {
				throw new IllegalArgumentException(
						"unit amount for key '" + key + "' is negative: " + amount);
			}
		});
		return Map.copyOf(amounts);
	}

	/**
	 * A report carrying an amount vector and none of the legacy breakdown, which is what a current caller sends.
	 *
	 * @param trackingId     the preflight-issued id this completes
	 * @param totalInput     released input aggregate
	 * @param totalOutput    released output aggregate
	 * @param totalLatencyMs wall-clock time of the turn
	 * @param iterationsUsed tool-loop iterations the turn took
	 * @param stopReason     why the turn ended, where the provider said
	 * @param errorMessage   what failed, on a turn that did
	 * @param unitAmounts    metered key to amount consumed
	 * @return the report, with every legacy breakdown component absent
	 */
	public static AiCompletionRequest of(String trackingId, int totalInput, int totalOutput, long totalLatencyMs,
			int iterationsUsed, Optional<String> stopReason, Optional<String> errorMessage,
			Map<String, Long> unitAmounts) {
		return new AiCompletionRequest(trackingId, totalInput, totalOutput, totalLatencyMs, iterationsUsed,
				stopReason.orElse(null), errorMessage.orElse(null), unitAmounts, null, null, null, null, null, null);
	}

	/**
	 * {@link #of(String, int, int, long, int, Optional, Optional, Map)} that also itemizes the amounts per provider
	 * API request.
	 *
	 * @param requestUnitAmounts one amount map per provider API request, in order; for every key the elements sum
	 *                           to the matching {@code unitAmounts} value, which web-global enforces
	 * @return the report, with every legacy breakdown component absent
	 * @see #of(String, int, int, long, int, Optional, Optional, Map)
	 */
	public static AiCompletionRequest of(String trackingId, int totalInput, int totalOutput, long totalLatencyMs,
			int iterationsUsed, Optional<String> stopReason, Optional<String> errorMessage,
			Map<String, Long> unitAmounts, List<Map<String, Long>> requestUnitAmounts) {
		return new AiCompletionRequest(trackingId, totalInput, totalOutput, totalLatencyMs, iterationsUsed,
				stopReason.orElse(null), errorMessage.orElse(null), unitAmounts, null, null,
				null, null, requestUnitAmounts, null);
	}

	/**
	 * {@link #of(String, int, int, long, int, Optional, Optional, Map, List)} that also says, per provider API
	 * request, when it ran, which model answered and the provider's id for it.
	 *
	 * @param requestMeta one element per element of {@code requestUnitAmounts}, in the same order; web-global
	 *                    checks the alignment
	 * @return the report, with every legacy breakdown component absent
	 * @see #of(String, int, int, long, int, Optional, Optional, Map, List)
	 */
	public static AiCompletionRequest of(String trackingId, int totalInput, int totalOutput, long totalLatencyMs,
			int iterationsUsed, Optional<String> stopReason, Optional<String> errorMessage,
			Map<String, Long> unitAmounts, List<Map<String, Long>> requestUnitAmounts,
			List<AiRequestMeta> requestMeta) {
		return new AiCompletionRequest(trackingId, totalInput, totalOutput, totalLatencyMs, iterationsUsed,
				stopReason.orElse(null), errorMessage.orElse(null), unitAmounts, null, null,
				null, null, requestUnitAmounts, requestMeta);
	}

	/** {@link #stopReason}, as an {@code Optional}: empty when the provider gave none. */
	public Optional<String> optStopReason() {
		return Optional.ofNullable(stopReason);
	}

	/** {@link #errorMessage}, as an {@code Optional}: empty on a turn that did not fail. */
	public Optional<String> optErrorMessage() {
		return Optional.ofNullable(errorMessage);
	}

	/** {@link #unitAmounts}, as an {@code Optional}: empty on a legacy caller that sends no vector. */
	public Optional<Map<String, Long>> optUnitAmounts() {
		return Optional.ofNullable(unitAmounts);
	}

	/** {@link #requestUnitAmounts}, as an {@code Optional}: empty on a caller that does not itemize requests. */
	public Optional<List<Map<String, Long>>> optRequestUnitAmounts() {
		return Optional.ofNullable(requestUnitAmounts);
	}

	/** {@link #requestMeta}, as an {@code Optional}: empty on a caller that does not report per-request metadata. */
	public Optional<List<AiRequestMeta>> optRequestMeta() {
		return Optional.ofNullable(requestMeta);
	}

	/** {@link #audioSeconds}, as an {@code OptionalInt}: legacy lane. */
	public OptionalInt optAudioSeconds() {
		return optInt(audioSeconds);
	}

	/** {@link #audioInputTokens}, as an {@code OptionalInt}: legacy lane. */
	public OptionalInt optAudioInputTokens() {
		return optInt(audioInputTokens);
	}

	/** {@link #cachedInputTokens}, as an {@code OptionalInt}: legacy lane. */
	public OptionalInt optCachedInputTokens() {
		return optInt(cachedInputTokens);
	}

	/** {@link #audioOutputTokens}, as an {@code OptionalInt}: legacy lane. */
	public OptionalInt optAudioOutputTokens() {
		return optInt(audioOutputTokens);
	}

	private static OptionalInt optInt(@Nullable Integer value) {
		return value == null ? OptionalInt.empty() : OptionalInt.of(value);
	}
}
