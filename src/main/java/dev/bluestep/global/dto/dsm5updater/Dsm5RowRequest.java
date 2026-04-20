package dev.bluestep.global.dto.dsm5updater;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record Dsm5RowRequest(
		@NotBlank @Size(max = 10) String code,
		@NotBlank @Size(max = 150) String label,
		@NotBlank @Size(max = 150) String category,
		@NotBlank String criteria) {
}
