package dev.bluestep.global.dto.aiunitrate;

import dev.bluestep.global.dto.ai.RateVariant;
import dev.bluestep.global.dto.ai.UnitCategory;
import dev.bluestep.global.dto.ai.UnitMeasure;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

/**
 * Create/update/upsert payload for an {@code ai_unit_rate} row — a global provider price for a
 * {@code (provider, model, unitKey, rateVariant)} scope, in micro-USD per 1,000,000 units. Unlike
 * {@code ai_tenant_config} this is provider cost, not a per-tenant figure, so there is no {@code schemaName}.
 *
 * <p>Those four fields form the unique scope and are treated as immutable on update (the service rejects a change
 * to any of them); {@code rateMicrosPerMillionUnits} and the two descriptive axes are mutable. A rate is a price,
 * so it may be zero — a genuinely free unit — but never negative.</p>
 *
 * <p>{@code unitKey} is free text because the provider decides what it charges for; {@code unitCategory} and
 * {@code unitMeasure} say what kind of thing it is, so that spend can be aggregated across providers that spell
 * the same charge differently. They are not a second identity for the row and nothing joins on them.</p>
 */
public record AiUnitRateRequest(
		@NotBlank String provider,
		@NotBlank String model,
		@NotBlank @Size(max = 64) String unitKey,
		@NotNull RateVariant rateVariant,
		@NotNull UnitCategory unitCategory,
		@NotNull UnitMeasure unitMeasure,
		@NotNull @PositiveOrZero Long rateMicrosPerMillionUnits
) {}
