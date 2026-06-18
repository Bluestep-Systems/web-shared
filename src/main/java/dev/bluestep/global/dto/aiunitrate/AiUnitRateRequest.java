package dev.bluestep.global.dto.aiunitrate;

import dev.bluestep.global.dto.ai.PricedUnit;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;

/**
 * Create/update/upsert payload for an {@code ai_unit_rate} row — a global provider price for a
 * {@code (provider, model, pricedUnit)} scope, in micro-USD per 1,000,000 units. Unlike
 * {@code ai_tenant_config}, this is provider cost, not a per-tenant figure, so there is no
 * {@code schemaName}.
 *
 * <p>{@code provider}, {@code model} and {@code pricedUnit} form the unique scope and are treated as
 * immutable on update (the service rejects a change to any of them); only
 * {@code rateMicrosPerMillionUnits} is mutable. A rate is a price, so it may be zero (a free unit) but
 * never negative. An unrecognized {@code pricedUnit} fails JSON binding (400) before reaching the
 * service.</p>
 */
public record AiUnitRateRequest(
		@NotBlank String provider,
		@NotBlank String model,
		@NotNull PricedUnit pricedUnit,
		@NotNull @PositiveOrZero Long rateMicrosPerMillionUnits
) {}
