package dev.bluestep.global.dto.aiunitrate;

import java.util.Optional;

import dev.bluestep.global.dto.ai.RateVariant;
import dev.bluestep.global.dto.ai.UnitAxis;
import dev.bluestep.global.dto.ai.UnitKeys;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

/**
 * Create/update payload for an {@code ai_unit_rate} row. Global provider cost, so unlike
 * {@code ai_tenant_config} this is not a per-tenant figure and there is no {@code tenantId}.
 *
 * <h2>The price is three numbers, and two of them are usually implied</h2>
 *
 * <p>A charge is {@code flatMicros} once, plus {@code rateMicros} for every {@code ratePerUnits}
 * consumed. Both optional components default to the conventional token quote — per million, with no
 * flat part — so the common payload is unchanged from the single-rate shape that preceded them.</p>
 *
 * <p>Sending {@code ratePerUnits} explicitly is what lets a price be stored the way its vendor quotes
 * it: {@code 30000000} micros per {@code 1} unit is $30 each, and expressing that as a per-million
 * figure is both unreadable and arithmetically unsafe at volume. See changeset 012.</p>
 *
 * <h2>When the charge is not a straight line</h2>
 *
 * <p>{@code pricingSpec} carries a JSON structure for a charge the three numbers cannot describe —
 * graduated tiers today, whatever a vendor invents next. Its {@code "type"} selects an evaluator, so a
 * new pricing shape is a new evaluator rather than a migration. Empty for nearly every rate, and when
 * empty the scalar components say everything. See changeset 014.</p>
 *
 * <h2>The axes are open</h2>
 *
 * <p>{@code unitCategory} and {@code unitMeasure} are validated strings, not enums. That is
 * load-bearing rather than a loosening for its own sake: the database stopped constraining them to a
 * fixed vocabulary in 012, and binding an enum here would have kept the vocabulary closed at the only
 * door anyone actually writes through — which is exactly the mistake {@code PricedUnit} made, where
 * widening the column bought nothing because the enum upstream was the real constraint.
 * {@link UnitAxis} names the shape and offers the known values.</p>
 */
public record AiUnitRateRequest(
		@NotBlank @Size(max = 50) String provider,
		@NotBlank @Size(max = 100) String model,
		@NotBlank @Size(max = UnitKeys.MAX_KEY_LENGTH) @Pattern(regexp = UnitKeys.KEY_PATTERN) String unitKey,
		@NotNull RateVariant rateVariant,
		@NotBlank @Size(max = UnitAxis.MAX_LENGTH) @Pattern(regexp = UnitAxis.PATTERN) String unitCategory,
		@NotBlank @Size(max = UnitAxis.MAX_LENGTH) @Pattern(regexp = UnitAxis.PATTERN) String unitMeasure,
		@NotNull @PositiveOrZero Long rateMicros,
		Optional<@Positive Long> ratePerUnits,
		Optional<@PositiveOrZero Long> flatMicros,
		Optional<String> pricingSpec
) {

	/** The per-million quote assumed when a payload does not say — what every rate meant before 012. */
	public static final long DEFAULT_RATE_PER_UNITS = 1_000_000L;

	/** The denominator to store: what was asked for, or the conventional per-million. */
	public long ratePerUnitsOrDefault() {
		return ratePerUnits.orElse(DEFAULT_RATE_PER_UNITS);
	}

	/** The flat component to store: what was asked for, or none. */
	public long flatMicrosOrZero() {
		return flatMicros.orElse(0L);
	}
}
