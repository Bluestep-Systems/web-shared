package dev.bluestep.global.dto.ai;

import java.util.Optional;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Preflight authorization result. The spend budget is enforced server-side and not
 * echoed here; {@code maxIterations} is the per-turn loop guard the caller honors.
 *
 * <p>The two optional components are complementary: an authorized result carries a
 * {@code maxIterations} and an empty {@code denialCode}; a denied result carries a
 * {@code denialCode} and an empty {@code maxIterations}.</p>
 */
public record AiPreflightResponse(
		@JsonProperty("a") boolean authorized,
		@JsonProperty("t") String trackingId,
		@JsonProperty("d") Optional<AiDenialCode> denialCode,
		@JsonProperty("i") Optional<Integer> maxIterations) {}
