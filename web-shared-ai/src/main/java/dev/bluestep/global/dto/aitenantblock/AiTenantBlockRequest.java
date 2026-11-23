package dev.bluestep.global.dto.aitenantblock;

import java.util.Optional;

import org.jspecify.annotations.Nullable;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Create payload for an ai_tenant_block row. An empty {@code unitId}
 * means the entire tenant is blocked; a present value scopes the block to a
 * single organization within the tenant. {@code reason} is empty when no
 * explanation was recorded.
 */
public record AiTenantBlockRequest(
		@NotBlank String tenantId,
		@Nullable String unitId,
		@Size(max = 500) @Nullable String reason
) {

	/** {@link #unitId}, as an {@code Optional}. */
	public Optional<String> optUnitId() {
		return Optional.ofNullable(unitId);
	}

	/** {@link #reason}, as an {@code Optional}. */
	public Optional<String> optReason() {
		return Optional.ofNullable(reason);
	}
}
