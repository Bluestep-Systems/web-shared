package dev.bluestep.global.dto.usage;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

/**
 * One tenant's storage levels as one sampling pass measured them.
 *
 * <p>These are <em>levels</em>, not deltas: web-global writes a sample over whatever row already
 * holds its {@code (sampledAt, schemaName)} key rather than adding to it, so a re-measured tenant
 * simply replaces its earlier figure for the period.</p>
 *
 * <p>The wire keys are the component names. A sample is one row per tenant per sampling period,
 * so unlike {@link UsageWindow} there is no volume argument for single-character keys.</p>
 *
 * @param schemaName        canonical {@code U<seqnum>} (a lowercase {@code u} or the bare number
 *                          is normalized at ingest)
 * @param dbBytes           the schema's relation footprint, {@code pg_total_relation_size} summed
 *                          over its relations
 * @param fileLogicalBytes  the tenant's documents at face size — what it uploaded, and the billed
 *                          file figure
 * @param filePhysicalBytes the tenant's dedup- and compression-amortized share of real file disk.
 *                          Internal margin visibility only: it moves when a <em>different</em>
 *                          tenant uploads the same file, so it must never reach an invoice
 */
public record StorageSample(
		@NotBlank @Size(max = MAX_SCHEMA_NAME_LENGTH) String schemaName,
		@PositiveOrZero @Max(MAX_BYTES) long dbBytes,
		@PositiveOrZero @Max(MAX_BYTES) long fileLogicalBytes,
		@PositiveOrZero @Max(MAX_BYTES) long filePhysicalBytes) {

	/**
	 * The largest byte level one sample may carry: 1e15, a petabyte for one tenant.
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
}
