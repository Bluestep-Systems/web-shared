package dev.bluestep.global.dto.usage;

import java.util.Map;

import dev.bluestep.global.dto.constraints.CodePointSize;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

/**
 * One tenant's storage levels as one sampling pass measured them, one level per <em>meter</em>.
 *
 * <p>These are <em>levels</em>, not deltas: web-global writes each over whatever row already holds
 * its {@code (sampledAt, schemaName, meter)} key rather than adding to it, so a re-measured tenant
 * simply replaces its earlier figure for the period.</p>
 *
 * <h2>Meters are an open vocabulary</h2>
 *
 * <p>A meter is a key, not a component, so a new kind of storage — a cold tier, a backup share, a
 * revised method that must not be mixed with the old one on one invoice — is a new key rather than
 * a new record shape, and no deployed producer has to change for another producer's meter to
 * exist. What a key means, whether it is billed and whether a tenant may see it is web-global's
 * {@code storage_meter} catalog, and ingest refuses a key the catalog does not list: a meter is
 * catalogued first and sent second. Today's keys are {@code db} (the schema's relation footprint),
 * {@code file_logical} (documents at face size, the billed file figure) and {@code file_physical}
 * (the dedup- and compression-amortized share of real disk — internal margin visibility only,
 * because it moves when a <em>different</em> tenant uploads the same file).</p>
 *
 * <p>The wire keys are the component names, and the meter keys travel as JSON/CBOR object keys.
 * A sample is one row per tenant per sampling period, so unlike {@link UsageWindow} there is no
 * volume argument for single-character keys.</p>
 *
 * @param schemaName canonical {@code U<seqnum>} (a lowercase {@code u} or the bare number is
 *                   normalized at ingest)
 * @param levels     bytes per meter key; at least one, at most {@value #MAX_METERS}. Keys are
 *                   trimmed, lowercased and spelling-checked at ingest
 */
public record StorageSample(
		@NotBlank @CodePointSize(max = MAX_SCHEMA_NAME_LENGTH) String schemaName,
		@NotEmpty @Size(max = MAX_METERS)
		Map<@NotBlank @CodePointSize(max = MAX_METER_LENGTH) String,
				@PositiveOrZero @Max(MAX_BYTES) Long> levels) {

	/**
	 * The largest byte level one meter may carry: 1e15, a petabyte for one tenant.
	 *
	 * <p>A level column never accumulates, so this is a plausibility bound rather than overflow
	 * headroom — three orders of magnitude above anything a tenant stores, and there to catch a
	 * sentinel or sign bug before it lands on a row that pricing reads.</p>
	 */
	public static final long MAX_BYTES = 1_000_000_000_000_000L;

	/**
	 * Length bound on {@code schemaName}, matching {@code varchar(50)} on
	 * {@code tenant_storage_sample.schema_name}.
	 */
	public static final int MAX_SCHEMA_NAME_LENGTH = 50;

	/** Length bound on a meter key, matching {@code varchar(50)} on {@code storage_meter.meter}. */
	public static final int MAX_METER_LENGTH = 50;

	/** The most meters one sample may carry — far above the catalog's size, and a cap on one row set. */
	public static final int MAX_METERS = 50;

	/**
	 * Folds an absent {@code levels} into an empty map (which {@code @NotEmpty} then refuses by
	 * name) and takes a defensive copy of a present one. A null key or value is not folded:
	 * {@code Map.copyOf} refuses it during binding, a 400 that names no field — acceptable for a
	 * payload no correct producer can build.
	 */
	public StorageSample {
		levels = levels == null ? Map.of() : Map.copyOf(levels);
	}
}
