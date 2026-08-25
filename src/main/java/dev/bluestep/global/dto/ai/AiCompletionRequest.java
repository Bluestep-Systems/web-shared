package dev.bluestep.global.dto.ai;

import java.util.Map;
import java.util.Optional;

import com.fasterxml.jackson.annotation.JsonProperty;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

/**
 * Completion report for a turn.
 *
 * <p>{@code totalInputTokens}/{@code totalOutputTokens} are the released aggregates, recorded whatever else is
 * reported. {@code unitAmounts} is what the turn is <em>priced</em> from: an open map from a metered key
 * ({@link UnitKeys}) to the amount consumed under it, emitted by each provider adapter under the keys that provider
 * actually meters. {@code stopReason} and {@code errorMessage} are empty on turns that ended without one.</p>
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
 */
public record AiCompletionRequest(
		@NotBlank @JsonProperty("t") String trackingId,
		@JsonProperty("i") int totalInputTokens,
		@JsonProperty("o") int totalOutputTokens,
		@JsonProperty("l") long totalLatencyMs,
		@JsonProperty("n") int iterationsUsed,
		@JsonProperty("s") Optional<String> stopReason,
		@JsonProperty("e") Optional<String> errorMessage,
		@JsonProperty("u") Optional<@Size(max = MAX_UNIT_KEYS) Map<
				@NotBlank @Size(max = UnitKeys.MAX_KEY_LENGTH) @Pattern(regexp = UnitKeys.KEY_PATTERN) String,
				@PositiveOrZero Long>> unitAmounts,
		@JsonProperty("a") Optional<Integer> audioSeconds,
		@JsonProperty("ai") Optional<Integer> audioInputTokens,
		@JsonProperty("ci") Optional<Integer> cachedInputTokens,
		@JsonProperty("ao") Optional<Integer> audioOutputTokens) {

	/**
	 * How many distinct keys one turn may report.
	 *
	 * <p>Far above any real turn — the richest provider meters a handful — and low enough that a runaway emitter
	 * is rejected rather than persisted. Without a bound the map is unbounded input on a path that writes to the
	 * billing table.</p>
	 */
	public static final int MAX_UNIT_KEYS = 64;

	/**
	 * Normalises the vector's absence and takes a defensive copy of a present one.
	 *
	 * <p>Jackson binds an omitted {@code "u"} to {@code null} whatever this package's null-marking says, and a
	 * pre-vector caller omits it on every single report — so the normalisation is not defensive style, it is the
	 * only thing standing between the legacy lane and a null component. Copying is the same decision the map
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
	 * @throws IllegalArgumentException if any key or amount is null, or any amount is negative
	 */
	public AiCompletionRequest {
		unitAmounts = unitAmounts == null ? Optional.empty() : unitAmounts.map(AiCompletionRequest::checkedCopy);
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
				stopReason, errorMessage, Optional.of(unitAmounts), Optional.empty(), Optional.empty(),
				Optional.empty(), Optional.empty());
	}
}
