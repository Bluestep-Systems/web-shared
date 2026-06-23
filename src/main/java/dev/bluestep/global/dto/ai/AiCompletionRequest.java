package dev.bluestep.global.dto.ai;

import com.fasterxml.jackson.annotation.JsonProperty;

import jakarta.validation.constraints.NotBlank;

/**
 * Completion report for a turn. {@code totalInputTokens}/{@code totalOutputTokens} are the released
 * aggregates. The optional breakdown fields ({@code audioInputTokens}, {@code cachedInputTokens},
 * {@code audioOutputTokens}) and {@code audioSeconds} let web-global price the turn at per-unit rates
 * (feeding the spend budget) — any omitted bucket is treated as zero, so the text rate applies to the
 * unattributed remainder of the aggregates.
 */
public record AiCompletionRequest(
		@NotBlank @JsonProperty("t") String trackingId,
		@JsonProperty("i") int totalInputTokens,
		@JsonProperty("o") int totalOutputTokens,
		@JsonProperty("l") long totalLatencyMs,
		@JsonProperty("n") int iterationsUsed,
		@JsonProperty("s") String stopReason,
		@JsonProperty("e") String errorMessage,
		@JsonProperty("a") Integer audioSeconds,
		@JsonProperty("ai") Integer audioInputTokens,
		@JsonProperty("ci") Integer cachedInputTokens,
		@JsonProperty("ao") Integer audioOutputTokens) {
}
