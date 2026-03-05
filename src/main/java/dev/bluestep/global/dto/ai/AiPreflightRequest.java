package dev.bluestep.global.dto.ai;

import com.fasterxml.jackson.annotation.JsonProperty;

import jakarta.validation.constraints.NotBlank;

public record AiPreflightRequest(
		@NotBlank @JsonProperty("s") String schemaName,
		@NotBlank @JsonProperty("o") String organizationId,
		@NotBlank @JsonProperty("u") String userId,
		@JsonProperty("f") String flag,
		@JsonProperty("t") String triggeringProcess,
		@NotBlank @JsonProperty("p") String provider,
		@NotBlank @JsonProperty("m") String model) {
}
