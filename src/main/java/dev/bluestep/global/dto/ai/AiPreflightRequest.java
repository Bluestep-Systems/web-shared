package dev.bluestep.global.dto.ai;

import com.fasterxml.jackson.annotation.JsonProperty;

import jakarta.validation.constraints.NotBlank;

/**
 * Preflight authorization request. The gate enforces a single universal cost cap
 * ({@code max_spend_micros}) over the configured budget window, so no per-call
 * dimension is declared — every call costs and is checked against the same ceiling.
 * {@code provider}/{@code model} let the gate fail closed when a model has no
 * {@code ai_unit_rate} pricing (it can't be metered, so it can't be authorized).
 */
public record AiPreflightRequest(
		@NotBlank @JsonProperty("s") String schemaName,
		@NotBlank @JsonProperty("o") String organizationId,
		@NotBlank @JsonProperty("u") String userId,
		@JsonProperty("f") String flag,
		@JsonProperty("t") String triggeringProcess,
		@NotBlank @JsonProperty("p") String provider,
		@NotBlank @JsonProperty("m") String model) {
}
