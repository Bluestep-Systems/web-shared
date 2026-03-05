package dev.bluestep.global.dto.ai;

import com.fasterxml.jackson.annotation.JsonProperty;

import jakarta.validation.constraints.NotBlank;

public record AiCompletionRequest(
		@NotBlank @JsonProperty("t") String trackingId,
		@JsonProperty("i") int totalInputTokens,
		@JsonProperty("o") int totalOutputTokens,
		@JsonProperty("l") long totalLatencyMs,
		@JsonProperty("n") int iterationsUsed,
		@JsonProperty("s") String stopReason,
		@JsonProperty("e") String errorMessage) {
}
