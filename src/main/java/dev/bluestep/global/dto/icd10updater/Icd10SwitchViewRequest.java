package dev.bluestep.global.dto.icd10updater;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

public record Icd10SwitchViewRequest(
		@NotNull Icd10ViewTarget target,
		@Min(1900) @Max(2099) int year,
		@Min(1) @Max(99) int version) {
}
