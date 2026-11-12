package dev.bluestep.global.dto.aitenantblock;

import java.time.OffsetDateTime;
import java.util.Optional;

import org.jspecify.annotations.Nullable;

/**
 * Read model for an ai_tenant_block row. An empty {@code unitId} means the
 * block covers the whole tenant; {@code reason} is empty when none was recorded.
 */
public record AiTenantBlockResponse(
		Long id,
		String tenantId,
		@Nullable String unitId,
		@Nullable String reason,
		OffsetDateTime blockedAt) {

	/** {@link #unitId}, as an {@code Optional}. */
	public Optional<String> optUnitId() {
		return Optional.ofNullable(unitId);
	}

	/** {@link #reason}, as an {@code Optional}. */
	public Optional<String> optReason() {
		return Optional.ofNullable(reason);
	}
}
