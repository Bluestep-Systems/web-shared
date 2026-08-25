package dev.bluestep.global.dto.ai;

import java.util.Optional;

import com.fasterxml.jackson.annotation.JsonProperty;

import jakarta.validation.constraints.NotBlank;

/**
 * Preflight authorization request. The gate enforces a single universal cost cap
 * ({@code max_spend_micros}) over the configured budget window, so no per-call
 * dimension is declared — every call costs and is checked against the same ceiling.
 * {@code provider}/{@code model} let the gate fail closed when a model has no
 * {@code ai_unit_rate} pricing (it can't be metered, so it can't be authorized).
 * {@code flag} and {@code triggeringProcess} are empty when the caller does not
 * narrow by them.
 *
 * <p><b>The 4.0.0 rename did not touch this wire.</b> {@code tenantId} and {@code unitId} were
 * {@code schemaName} and {@code organizationId}; because every component here is pinned to a
 * one-character {@code @JsonProperty}, the rename is Java-source-breaking and wire-identical. That
 * is what lets the whole fleet keep preflighting across the 3.0.0/4.0.0 stagger while only the
 * admin surfaces need a version tag — see the {@code contract} package in web-global. Do not
 * "tidy" these annotations to match the new component names: the key is the contract, the
 * component name is not.</p>
 */
public record AiPreflightRequest(
		@NotBlank @JsonProperty("s") String tenantId,
		@NotBlank @JsonProperty("o") String unitId,
		@NotBlank @JsonProperty("u") String userId,
		@JsonProperty("f") Optional<String> flag,
		@JsonProperty("t") Optional<String> triggeringProcess,
		@NotBlank @JsonProperty("p") String provider,
		@NotBlank @JsonProperty("m") String model) {}
