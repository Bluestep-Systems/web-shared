package dev.bluestep.global.dto.aitenantconfig;

import java.util.Optional;

import dev.bluestep.global.dto.ai.BudgetSchedule;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

/**
 * Create/update payload for an ai_tenant_config row. {@code unitId}, {@code userId} and
 * {@code flag} may be empty — an empty value acts as a wildcard against that dimension
 * (e.g. empty flag = applies to all flags for the tenant).
 *
 * <h2>The four dimensions, and why they are ordered</h2>
 *
 * <p>A scope is {@code (tenantId, unitId, userId, flag)}, and the three optional ones are
 * resolved most-specific-first in that order: unit beats user beats flag. That order is not
 * alphabetical and it is not arbitrary — it is the containment order of the things being
 * budgeted. A unit is a set of users; a user makes calls under many flags. So a limit set on
 * the narrower container has to win over one set on the wider, or provisioning a tenant-wide
 * flag budget would silently override the per-user cap somebody set deliberately.</p>
 *
 * <p>{@code userId} is the same identifier {@code AiPreflightRequest.userId} carries and
 * {@code ai_usage_metadata.user_id} has always recorded, so a per-user budget sums the rows that
 * were already there — this dimension adds a place to configure a limit, not a new thing to
 * measure.</p>
 *
 * <p>{@code budgetSchedule} and {@code utcOffsetMinutes} are optional on the wire;
 * absent values default to {@link BudgetSchedule#LIFETIME} and {@code 0} in the
 * service layer. {@code utcOffsetMinutes} is bounded by {@link java.time.ZoneOffset}'s
 * valid range ({@code -1080 .. 1080}).</p>
 *
 * <p>{@code maxSpendMicros} (cost ceiling in micro-USD over the window) is optional: omit it
 * to provision an <em>uncapped</em> scope — usage is still recorded and metered, just not
 * gated on spend. When present it must be positive. {@code maxIterations} is the per-turn loop
 * guard and is always required.</p>
 */
public record AiTenantConfigRequest(
		@NotBlank String tenantId,
		Optional<String> unitId,
		Optional<String> userId,
		Optional<String> flag,
		Optional<@Positive Long> maxSpendMicros,
		@NotNull @Positive Integer maxIterations,
		Optional<BudgetSchedule> budgetSchedule,
		Optional<@Min(-1080) @Max(1080) Integer> utcOffsetMinutes,
		Optional<Boolean> enabled
) {}
