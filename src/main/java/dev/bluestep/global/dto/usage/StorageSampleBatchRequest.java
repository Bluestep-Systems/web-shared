package dev.bluestep.global.dto.usage;

import java.time.OffsetDateTime;
import java.util.List;

import dev.bluestep.global.dto.constraints.CodePointSize;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * One namespace's storage sampling pass: every tenant it measured, for one sampling period.
 *
 * <p>No batch id, unlike {@link UsageBatchRequest}, because none is needed. {@code sampledAt} is
 * the <em>period</em> the pass belongs to, not the moment of measurement, and web-global replaces
 * rather than adds on {@code (sampledAt, schemaName, meter)} — so a retried push, or a pass
 * re-run after a restart, lands on the same rows and overwrites them. The endpoint is idempotent by key.</p>
 *
 * <p>An empty {@code samples} is valid and meaningful: it is the namespace's heartbeat, saying the
 * sampler ran and found no tenant to measure. Web-global records the pass per namespace, so a
 * namespace with no tenants stays distinguishable from one whose sampler has stopped.</p>
 *
 * <p>The wire keys are the component names; see {@link StorageSample} for why.</p>
 *
 * <h2>What web-global refuses beyond these constraints</h2>
 *
 * <p>Each of these is a 400 — dropped and counted by the producer, never retried — and never a
 * silent correction. They live at ingest rather than here because each needs server time or the
 * server's normalization:</p>
 * <ul>
 *   <li>a {@code sampledAt} not aligned to the sampling period, which would write a second row
 *       for the same period;</li>
 *   <li>a {@code sampledAt} ahead of server time by more than a clock-skew allowance, or older
 *       than a few periods — an uninitialized or sign-flipped producer value would otherwise
 *       write a row pricing reads;</li>
 *   <li>two samples naming the same tenant once schema names are normalized ({@code U1000001},
 *       {@code u1000001} and {@code 1000001} are one tenant). A pass measures each tenant once,
 *       so a duplicate is a producer bug;</li>
 *   <li>a meter web-global's catalog does not list. A meter is catalogued before any producer
 *       sends it; a figure silently dropped would be storage nobody bills;</li>
 *   <li>two levels naming the same meter for one tenant once meter keys are normalized
 *       (trimmed and lowercased, like the namespace).</li>
 * </ul>
 *
 * @param namespace the Kubernetes namespace the sampler runs in, recorded on every row; trimmed,
 *                  lowercased and spelling-checked at ingest exactly as
 *                  {@link UsageBatchRequest#namespace()} is
 * @param sampledAt the start of the sampling period — midnight UTC while the cadence is daily
 * @param samples   one entry per tenant measured, at most {@value #MAX_SAMPLES}; empty for a
 *                  heartbeat
 */
public record StorageSampleBatchRequest(
		@NotBlank @CodePointSize(max = UsageBatchRequest.MAX_NAMESPACE_LENGTH) String namespace,
		@NotNull OffsetDateTime sampledAt,
		@Size(max = MAX_SAMPLES) List<@Valid StorageSample> samples) {

	/**
	 * The most samples one push may carry: 10,000 — one per tenant, far above any namespace's
	 * tenant count, and the same bound {@link UsageBatchRequest#MAX_WINDOWS} puts on the length of
	 * one ingest transaction.
	 */
	public static final int MAX_SAMPLES = 10_000;

	/**
	 * Folds an absent list into the heartbeat it means, and takes a defensive copy of a present one.
	 *
	 * <p>Jackson binds an omitted {@code samples} to {@code null} whatever this package's
	 * null-marking says. A list <em>containing</em> a null is not folded: {@code List.copyOf}
	 * refuses it during binding, which surfaces as a 400 that names no field — acceptable for a
	 * payload no correct producer can build.</p>
	 */
	public StorageSampleBatchRequest {
		samples = samples == null ? List.of() : List.copyOf(samples);
	}
}
