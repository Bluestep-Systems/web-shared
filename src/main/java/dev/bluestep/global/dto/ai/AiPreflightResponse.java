package dev.bluestep.global.dto.ai;

import com.fasterxml.jackson.annotation.JsonProperty;

public record AiPreflightResponse(
		@JsonProperty("a") boolean authorized,
		@JsonProperty("t") String trackingId,
		@JsonProperty("d") AiDenialCode denialCode,
		@JsonProperty("b") Integer maxTokenBudget,
		@JsonProperty("i") Integer maxIterations) {
}
