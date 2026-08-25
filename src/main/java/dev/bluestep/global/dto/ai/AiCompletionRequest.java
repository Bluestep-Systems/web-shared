package dev.bluestep.global.dto.ai;

import java.util.Map;
import java.util.Optional;

import com.fasterxml.jackson.annotation.JsonProperty;

import jakarta.validation.constraints.NotBlank;

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
 * <p><b>The legacy components are a transition lane, not a second way of saying the same thing.</b> web and
 * web-global are separate deployments, so a monolith pod that has not rolled yet still reports through
 * {@code audioSeconds}/{@code audioInputTokens}/{@code cachedInputTokens}/{@code audioOutputTokens} and must still
 * be priced correctly while it does. The pricing side prefers {@code unitAmounts} and falls back to these only when
 * it is empty; a caller emitting the vector leaves them absent. They come out once no deployed caller sends
 * them.</p>
 *
 * @param trackingId        the preflight-issued id this completes
 * @param totalInputTokens  released input aggregate
 * @param totalOutputTokens released output aggregate
 * @param totalLatencyMs    wall-clock time of the turn
 * @param iterationsUsed    tool-loop iterations the turn took
 * @param stopReason        why the turn ended, where the provider said
 * @param errorMessage      what failed, on a turn that did
 * @param unitAmounts       metered key to amount consumed; what the turn is priced from
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
		@JsonProperty("u") Map<String, Long> unitAmounts,
		@JsonProperty("a") Optional<Integer> audioSeconds,
		@JsonProperty("ai") Optional<Integer> audioInputTokens,
		@JsonProperty("ci") Optional<Integer> cachedInputTokens,
		@JsonProperty("ao") Optional<Integer> audioOutputTokens) {

	/**
	 * Absent vector to empty vector, and a defensive copy of a present one.
	 *
	 * <p>Jackson binds an omitted {@code "u"} to {@code null} whatever this package's null-marking says, and a
	 * pre-vector caller omits it on every single report — so the normalisation is not defensive style, it is the
	 * only thing standing between the legacy lane and a null component. Copying is the same decision the map
	 * itself forces: the amounts are what a turn is priced from, and a caller that could mutate them after
	 * reporting could change a bill after it was rendered.</p>
	 */
	public AiCompletionRequest {
		unitAmounts = unitAmounts == null ? Map.of() : Map.copyOf(unitAmounts);
	}

	/**
	 * A report carrying an amount vector and none of the legacy breakdown, which is what a current caller sends.
	 *
	 * @param trackingId      the preflight-issued id this completes
	 * @param totalInput      released input aggregate
	 * @param totalOutput     released output aggregate
	 * @param totalLatencyMs  wall-clock time of the turn
	 * @param iterationsUsed  tool-loop iterations the turn took
	 * @param stopReason      why the turn ended, where the provider said
	 * @param errorMessage    what failed, on a turn that did
	 * @param unitAmounts     metered key to amount consumed
	 */
	public static AiCompletionRequest of(String trackingId, int totalInput, int totalOutput, long totalLatencyMs,
			int iterationsUsed, Optional<String> stopReason, Optional<String> errorMessage,
			Map<String, Long> unitAmounts) {
		return new AiCompletionRequest(trackingId, totalInput, totalOutput, totalLatencyMs, iterationsUsed,
				stopReason, errorMessage, unitAmounts, Optional.empty(), Optional.empty(), Optional.empty(),
				Optional.empty());
	}
}
