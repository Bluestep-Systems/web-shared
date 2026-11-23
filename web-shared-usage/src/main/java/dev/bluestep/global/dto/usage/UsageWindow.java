package dev.bluestep.global.dto.usage;

import com.fasterxml.jackson.annotation.JsonProperty;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

/**
 * One tenant's aggregates for one minute window and one category, as a producer accumulated them.
 *
 * <p><b>The single-character keys are the wire contract.</b> Every producer (the web
 * monolith today) and web-global's ingest bind this one record, but a deployed pod keeps pushing
 * whatever keys its jar was built with — so renaming any {@code @JsonProperty} value, or changing a
 * component's type, breaks every pod still on an older release, silently on CBOR.
 * {@code UsageWireContractTest} pins each key so a drift fails this build rather than the
 * fleet.</p>
 *
 * <p>The totals are additive: web-global folds a window into {@code tenant_usage} with
 * {@code ON CONFLICT ... DO UPDATE SET hits = hits + EXCLUDED.hits, ...}, so concurrent pods
 * reporting the same window land on one row. The four {@code *Max} components merge by
 * {@code GREATEST} instead, which is what turns a per-pod max into a fleet-wide one.</p>
 *
 * <h2>Why every total is bounded</h2>
 *
 * <p>Every numeric component is {@code [0, }{@value #MAX_TOTAL}{@code ]}, enforced here and again
 * in web-global's {@code UsageIngestService} for callers that do not arrive over HTTP. The
 * columns behind them are {@code bigint} and <em>additive</em>, so an absurd value is not a bad
 * row — it is a poisoned primary key: one {@code Long.MAX_VALUE} lands, and every later merge into
 * that minute fails with {@code bigint out of range} until someone deletes the row by hand. A
 * negative is the same story in reverse, subtracting from a bill. Both used to be clamped
 * silently; they are refused now, because a producer bug that quietly reports zero is a bug
 * nobody finds.</p>
 *
 * <p>{@code windowStart} is bounded too, but against server time rather than by a constant — see
 * web-global's {@code UsageIngestService.MAX_WINDOW_AGE}. It is still truncated to its minute at ingest, so a
 * producer that already aligns sends exactly what is stored.</p>
 *
 * @param windowStart      epoch millis of the window's start, minute-aligned UTC
 * @param schemaName       canonical {@code U<seqnum>} (a lowercase {@code u} or the bare number is
 *                         normalized), or the reserved literal {@code unattributed} for work no
 *                         tenant could be attributed to
 * @param category         {@code http} for the request lane; background lanes keep their
 *                         processstat category. Trimmed and lowercased at ingest, then held to
 *                         {@code UsageIngestService.KEY_SPELLING} — it is a primary-key column, so
 *                         a stray capital would split one tenant-minute into two billing rows
 * @param hits             requests (or lane-defined units of work) completed in the window
 * @param pageMillis       total wall-clock millis spent serving them
 * @param pageMillisMax    the single slowest hit's wall-clock millis
 * @param cpuMillis        total thread CPU millis (internal metric — unpriced, per decision D5
 *                         of the web repo's tenant usage metering design)
 * @param dbMillis         total millis spent in DAO/query execution
 * @param allocBytes       cumulative bytes allocated on the serving threads (internal metric)
 * @param requestBytes     total request body bytes received
 * @param requestBytesMax  the single largest request's bytes
 * @param responseBytes    total response bytes sent
 * @param responseBytesMax the single largest response's bytes
 */
public record UsageWindow(
		@JsonProperty("t") long windowStart,
		@NotBlank @Size(max = MAX_KEY_LENGTH) @JsonProperty("s") String schemaName,
		@NotBlank @Size(max = MAX_KEY_LENGTH) @JsonProperty("c") String category,
		@PositiveOrZero @Max(MAX_TOTAL) @JsonProperty("h") long hits,
		@PositiveOrZero @Max(MAX_TOTAL) @JsonProperty("p") long pageMillis,
		@PositiveOrZero @Max(MAX_TOTAL) @JsonProperty("q") long pageMillisMax,
		@PositiveOrZero @Max(MAX_TOTAL) @JsonProperty("u") long cpuMillis,
		@PositiveOrZero @Max(MAX_TOTAL) @JsonProperty("d") long dbMillis,
		@PositiveOrZero @Max(MAX_TOTAL) @JsonProperty("a") long allocBytes,
		@PositiveOrZero @Max(MAX_TOTAL) @JsonProperty("i") long requestBytes,
		@PositiveOrZero @Max(MAX_TOTAL) @JsonProperty("j") long requestBytesMax,
		@PositiveOrZero @Max(MAX_TOTAL) @JsonProperty("o") long responseBytes,
		@PositiveOrZero @Max(MAX_TOTAL) @JsonProperty("x") long responseBytesMax) {

	/**
	 * The largest value any single component of one window may carry: 1e15.
	 *
	 * <p>Chosen for headroom on an <em>additive</em> {@code bigint} column, not as a plausibility
	 * limit. A minute cannot produce anything near it — 1e15 millis is 32,000 years of wall clock,
	 * and 1e15 bytes is a petabyte allocated by one pod in sixty seconds — while leaving roughly
	 * 9,000x room under {@code bigint}'s 9.22e18 ceiling for the column to keep accumulating over a
	 * billing period, even if a producer somehow pushed the maximum every minute.</p>
	 *
	 * <p>Deliberately generous, because a validation failure is now a <em>permanent drop</em> for
	 * the producer: a rejected batch is discarded and counted, never retried. The bound is here to
	 * catch a genuine bug (an uninitialized counter, a sentinel value, a sign flip), not to
	 * second-guess a busy minute — no real workload can come within four orders of magnitude of
	 * it.</p>
	 */
	public static final long MAX_TOTAL = 1_000_000_000_000_000L;

	/**
	 * Length bound on {@code schemaName} and {@code category}, matching {@code varchar(50)} on both
	 * {@code tenant_usage} columns — so an over-long key is a 400 at the edge rather than a
	 * {@code value too long} 500 from the driver.
	 */
	public static final int MAX_KEY_LENGTH = 50;
}
