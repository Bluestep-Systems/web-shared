package dev.bluestep.global.dto.ai;

import java.util.Optional;

import com.fasterxml.jackson.annotation.JsonProperty;

import jakarta.validation.constraints.NotBlank;

/**
 * Completion report for a turn. {@code totalInputTokens}/{@code totalOutputTokens} are the released
 * aggregates. The optional breakdown fields ({@code audioInputTokens}, {@code cachedInputTokens},
 * {@code audioOutputTokens}) and {@code audioSeconds} let web-global price the turn at per-unit rates
 * (feeding the spend budget) — any empty bucket is treated as zero, so the text rate applies to the
 * unattributed remainder of the aggregates. {@code stopReason} and {@code errorMessage} are empty on
 * turns that ended without one.
 */
public record AiCompletionRequest(
		@NotBlank @JsonProperty("t") String trackingId,
		@JsonProperty("i") int totalInputTokens,
		@JsonProperty("o") int totalOutputTokens,
		@JsonProperty("l") long totalLatencyMs,
		@JsonProperty("n") int iterationsUsed,
		@JsonProperty("s") Optional<String> stopReason,
		@JsonProperty("e") Optional<String> errorMessage,
		@JsonProperty("a") Optional<Integer> audioSeconds,
		@JsonProperty("ai") Optional<Integer> audioInputTokens,
		@JsonProperty("ci") Optional<Integer> cachedInputTokens,
		@JsonProperty("ao") Optional<Integer> audioOutputTokens) {}
