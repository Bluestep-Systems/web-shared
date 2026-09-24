package dev.bluestep.global.dto.usage;

import java.time.OffsetDateTime;
import java.util.List;

import com.fasterxml.jackson.annotation.JsonIgnore;

import dev.bluestep.global.dto.constraints.CodePointSize;
import jakarta.validation.Valid;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

/**
 * One namespace's {@code pg_stat_statements} diff: every tenant's server-side database work between
 * two reads of the counters, plus what the namespace's Postgres says about how trustworthy that diff
 * is.
 *
 * <h2>Idempotent by window start</h2>
 *
 * <p>Web-global keys a push on {@code (namespace, windowStart)} and <em>replaces</em> whatever that
 * key holds. A producer advances its baseline only once a push is acknowledged, so when one is lost it
 * sends the next window from the same start to a later end, carrying both intervals' work — and that
 * push overwrites a first attempt which did land but whose answer was lost, instead of adding to it.</p>
 *
 * <p>An empty {@code tenants} is valid and meaningful: the namespace's Postgres ran no tenant
 * statements in the window, and the push is still the sampler's heartbeat.</p>
 *
 * <h2>How far to trust the window</h2>
 *
 * <p>An entry evicted between two reads takes whatever it gained since the first read with it, so the
 * window under-counts by an amount nobody can know. A non-zero {@code deallocDelta} says that
 * happened to some statements in the window; {@code entries} against {@code entriesMax} says how
 * close the store is to it. {@code statsReset} says the counters were reset inside the window, and
 * every figure is then counted from the reset — again an under-count, never a double count.</p>
 *
 * <h2>What web-global refuses beyond these constraints</h2>
 *
 * <p>Never a silent correction. A window overlapping another stored window of the same namespace
 * with a different start, which would count the overlap twice, is a <b>409 Conflict</b>. Each of
 * these is a 400:</p>
 * <ul>
 *   <li>a {@code windowEnd} not after {@code windowStart}, or a window longer than a day;</li>
 *   <li>a {@code windowEnd} more than an hour ahead of server time, or a {@code windowStart} more
 *       than seven days old;</li>
 *   <li>two rows naming the same tenant once schema names are normalized;</li>
 *   <li>any failure of the bean-validation constraints declared here, including the record-level
 *       {@code entries <= entriesMax}.</li>
 * </ul>
 *
 * <p>The wire keys are the component names. Every component but {@code tenants} is required, and
 * the primitives are required by binding itself: under Jackson 3 an omitted primitive fails binding,
 * so web-global answers 400. A component added later must therefore be boxed or {@code Optional} to
 * stay additive for producers built against the older shape.</p>
 *
 * @param namespace    the Kubernetes namespace the sampler runs in; trimmed, lowercased and
 *                     spelling-checked at ingest exactly as {@link UsageBatchRequest#namespace()} is
 * @param windowStart  when the baseline read was taken
 * @param windowEnd    when the closing read was taken
 * @param deallocDelta growth of {@code pg_stat_statements_info.dealloc} over the window. That counts
 *                     eviction <em>passes</em>, not evicted entries: in PG14 each pass evicts
 *                     max(10, 5% of entries), about 250 entries at {@code max=5000}. Non-zero means
 *                     some statements' growth in the window was lost to eviction
 * @param statsReset   whether the counters were reset during the window
 * @param entries      entries held at the closing read; never more than {@code entriesMax}
 * @param entriesMax   the store's capacity, {@code pg_stat_statements.max}; never below 100, the minimum
 *                     Postgres accepts for that setting
 * @param tenants      one row per tenant with work in the window, at most {@value #MAX_TENANTS}
 */
public record DbUsageBatchRequest(
		@NotBlank @CodePointSize(max = UsageBatchRequest.MAX_NAMESPACE_LENGTH) String namespace,
		@NotNull OffsetDateTime windowStart,
		@NotNull OffsetDateTime windowEnd,
		@PositiveOrZero @Max(DbUsageRow.MAX_TOTAL) long deallocDelta,
		boolean statsReset,
		@PositiveOrZero @Max(DbUsageRow.MAX_TOTAL) long entries,
		@Positive @Max(DbUsageRow.MAX_TOTAL) long entriesMax,
		@Size(max = MAX_TENANTS) List<@Valid DbUsageRow> tenants) {

	/** The most rows one push may carry: one per tenant, the same bound as {@link StorageSampleBatchRequest#MAX_SAMPLES}. */
	public static final int MAX_TENANTS = StorageSampleBatchRequest.MAX_SAMPLES;

	/**
	 * Folds an absent list into the heartbeat it means, and takes a defensive copy of a present one.
	 * A list containing a null is refused by {@code List.copyOf} during binding.
	 */
	public DbUsageBatchRequest {
		tenants = tenants == null ? List.of() : List.copyOf(tenants);
	}

	/**
	 * Whether the store holds no more entries than its capacity; a store over capacity is a
	 * producer bug, refused as a 400.
	 *
	 * <p>{@code @JsonIgnore} because a {@code @AssertTrue} method is a JavaBeans getter: without it
	 * Jackson would serialize {@code entriesWithinCapacity} onto the wire as a property the record
	 * cannot bind back.</p>
	 *
	 * @return {@code true} when {@code entries <= entriesMax}
	 */
	@JsonIgnore
	@AssertTrue(message = "entries must not exceed entriesMax")
	public boolean isEntriesWithinCapacity() {
		return entries <= entriesMax;
	}
}
