package dev.bluestep.global.dto.usage;

import dev.bluestep.global.dto.constraints.CodePointSize;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.PositiveOrZero;

/**
 * One tenant's server-side database work over one {@link DbUsageBatchRequest} window: the growth of
 * the {@code pg_stat_statements} counters of every statement that names the tenant's schema.
 *
 * <p>These are <em>deltas</em> over the window, but web-global stores them by replacing, not adding:
 * a window is keyed by its namespace and start, and a producer that re-sends a window re-sends its
 * whole content (see {@link DbUsageBatchRequest}).</p>
 *
 * <p>The wire keys are the component names. A push carries one row per tenant per window, so unlike
 * {@link UsageWindow} there is no volume argument for single-character keys.</p>
 *
 * <p>Every numeric component is a required primitive: under Jackson 3 an omitted primitive fails
 * binding, so web-global answers 400. A component added later must therefore be boxed or
 * {@code Optional} to stay additive for producers built against the older shape.</p>
 *
 * @param schemaName      canonical {@code U<seqnum>} (a lowercase {@code u} or the bare number is
 *                        normalized at ingest), or the reserved literal {@code unattributed} for
 *                        statements that name no tenant schema — shared overhead
 * @param calls           statements executed
 * @param execMicros      server execution time in whole microseconds: the growth of
 *                        {@code total_exec_time} (double milliseconds), summed across the tenant's
 *                        statements and converted once at the end
 * @param sharedBlksRead  shared buffer blocks read from outside the buffer cache
 * @param sharedBlksHit   shared buffer blocks found in the buffer cache
 */
public record DbUsageRow(
		@NotBlank @CodePointSize(max = MAX_SCHEMA_NAME_LENGTH) String schemaName,
		@PositiveOrZero @Max(MAX_TOTAL) long calls,
		@PositiveOrZero @Max(MAX_TOTAL) long execMicros,
		@PositiveOrZero @Max(MAX_TOTAL) long sharedBlksRead,
		@PositiveOrZero @Max(MAX_TOTAL) long sharedBlksHit) {

	/**
	 * The largest figure one component may carry: 1e15, reusing {@link UsageWindow#MAX_TOTAL}.
	 *
	 * <p>That constant was sized as headroom on an additive {@code bigint} column; these rows replace
	 * rather than accumulate, so here it is only a sentinel/sign-bug catch. No window comes near it —
	 * 1e15 microseconds is 31 years of execution time — so it refuses a corrupt figure without ever
	 * refusing a busy window.</p>
	 */
	public static final long MAX_TOTAL = UsageWindow.MAX_TOTAL;

	/**
	 * Length bound on {@code schemaName}, matching {@code varchar(50)} on
	 * {@code tenant_db_usage.schema_name}; the same bound as {@link StorageSample#MAX_SCHEMA_NAME_LENGTH}.
	 */
	public static final int MAX_SCHEMA_NAME_LENGTH = StorageSample.MAX_SCHEMA_NAME_LENGTH;
}
