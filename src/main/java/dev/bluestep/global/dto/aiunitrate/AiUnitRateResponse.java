package dev.bluestep.global.dto.aiunitrate;

import java.time.OffsetDateTime;

import dev.bluestep.global.dto.ai.RateVariant;
import dev.bluestep.global.dto.ai.UnitCategory;
import dev.bluestep.global.dto.ai.UnitMeasure;

/**
 * Read model for an {@code ai_unit_rate} row. {@code rateMicrosPerMillionUnits} is the price in micro-USD per
 * 1,000,000 units; {@code unitKey} plus {@code rateVariant} is what a turn's amount vector joins on, and the two
 * axes below them are descriptive — see {@link AiUnitRateRequest}.
 */
public record AiUnitRateResponse(
		Long id,
		String provider,
		String model,
		String unitKey,
		RateVariant rateVariant,
		UnitCategory unitCategory,
		UnitMeasure unitMeasure,
		long rateMicrosPerMillionUnits,
		OffsetDateTime createdAt,
		OffsetDateTime updatedAt
) {}
