package dev.bluestep.global.dto.aitenantconfig;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

/**
 * Create/update payload for an ai_tenant_config row. {@code organizationId} and
 * {@code flag} may be null — a null acts as a wildcard against that dimension
 * (e.g. NULL flag = applies to all flags for the tenant).
 */
public record AiTenantConfigRequest(
		@NotBlank String schemaName,
		String organizationId,
		String flag,
		@NotNull @Positive Integer maxTokenBudget,
		@NotNull @Positive Integer maxIterations,
		Boolean enabled
) {}
