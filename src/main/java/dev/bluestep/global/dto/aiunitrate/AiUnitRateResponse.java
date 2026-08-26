package dev.bluestep.global.dto.aiunitrate;

import java.time.OffsetDateTime;
import java.util.Optional;

import dev.bluestep.global.dto.ai.RateVariant;
import dev.bluestep.global.dto.ai.UnitAxis;

/**
 * Read model for an {@code ai_unit_rate} row: {@code flatMicros} once per turn that meters the key,
 * plus {@code rateMicros} per {@code ratePerUnits} consumed.
 *
 * <p>{@code unitCategory} and {@code unitMeasure} come back exactly as stored, including a value
 * outside {@link dev.bluestep.global.dto.ai.UnitCategory} or
 * {@link dev.bluestep.global.dto.ai.UnitMeasure} — the axes are an open vocabulary. Use
 * {@link UnitAxis#category(String)} / {@link UnitAxis#measure(String)} to resolve one when a typed
 * constant is wanted, and expect empty for a unit this codebase has not heard of.</p>
 */
public record AiUnitRateResponse(
		Long id,
		String provider,
		String model,
		String unitKey,
		RateVariant rateVariant,
		String unitCategory,
		String unitMeasure,
		long rateMicros,
		long ratePerUnits,
		long flatMicros,
		Optional<String> pricingSpec,
		OffsetDateTime createdAt,
		OffsetDateTime updatedAt
) {}
