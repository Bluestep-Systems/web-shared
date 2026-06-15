package dev.bluestep.global.dto.ai;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Preflight authorization result. The spend budget is enforced server-side and not
 * echoed here; {@code maxIterations} is the per-turn loop guard the caller honors.
 */
public record AiPreflightResponse(
		@JsonProperty("a") boolean authorized,
		@JsonProperty("t") String trackingId,
		@JsonProperty("d") AiDenialCode denialCode,
		@JsonProperty("i") Integer maxIterations) {
}
