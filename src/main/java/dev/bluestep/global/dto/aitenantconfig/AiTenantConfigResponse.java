package dev.bluestep.global.dto.aitenantconfig;

import java.time.OffsetDateTime;
import java.util.Optional;
import java.util.OptionalLong;

import org.jspecify.annotations.Nullable;

import dev.bluestep.global.dto.ai.BudgetSchedule;

/**
 * Read model for an ai_tenant_config row. Empty {@code unitId}/{@code userId}/{@code flag}
 * are wildcards against that dimension; an empty {@code maxSpendMicros} means the
 * scope is uncapped (metered but not gated on spend).
 *
 * <p>See {@link AiTenantConfigRequest} for why the optional dimensions resolve unit-before-
 * user-before-flag.</p>
 */
public record AiTenantConfigResponse(
		Long id,
		String tenantId,
		@Nullable String unitId,
		@Nullable String userId,
		@Nullable String flag,
		@Nullable Long maxSpendMicros,
		Integer maxIterations,
		BudgetSchedule budgetSchedule,
		int utcOffsetMinutes,
		boolean enabled,
		OffsetDateTime createdAt,
		OffsetDateTime updatedAt
) {

	/** {@link #unitId}, as an {@code Optional}. */
	public Optional<String> optUnitId() {
		return Optional.ofNullable(unitId);
	}

	/** {@link #userId}, as an {@code Optional}. */
	public Optional<String> optUserId() {
		return Optional.ofNullable(userId);
	}

	/** {@link #flag}, as an {@code Optional}. */
	public Optional<String> optFlag() {
		return Optional.ofNullable(flag);
	}

	/** {@link #maxSpendMicros}, as an {@code OptionalLong}. */
	public OptionalLong optMaxSpendMicros() {
		return maxSpendMicros == null ? OptionalLong.empty() : OptionalLong.of(maxSpendMicros);
	}
}
