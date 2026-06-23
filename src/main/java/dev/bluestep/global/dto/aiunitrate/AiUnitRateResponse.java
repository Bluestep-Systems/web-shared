package dev.bluestep.global.dto.aiunitrate;

import java.time.OffsetDateTime;

import dev.bluestep.global.dto.ai.PricedUnit;

/**
 * Read model for an {@code ai_unit_rate} row. {@code rateMicrosPerMillionUnits} is the price in
 * micro-USD per 1,000,000 units.
 */
public record AiUnitRateResponse(
		Long id,
		String provider,
		String model,
		PricedUnit pricedUnit,
		long rateMicrosPerMillionUnits,
		OffsetDateTime createdAt,
		OffsetDateTime updatedAt
) {}
