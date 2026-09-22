package dev.bluestep.global.dto.usage;

import java.time.OffsetDateTime;
import java.util.List;

import dev.bluestep.global.dto.constraints.CodePointSize;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PastOrPresent;
import jakarta.validation.constraints.Size;

/**
 * One namespace's storage sampling pass: every tenant it measured, for one sampling period.
 *
 * <p>No batch id, unlike {@link UsageBatchRequest}, because none is needed. {@code sampledAt} is
 * the <em>period</em> the pass belongs to, not the moment of measurement, and web-global replaces
 * rather than adds on {@code (sampledAt, schemaName)} — so a retried push, or a pass re-run after
 * a restart, lands on the same rows and overwrites them. The endpoint is idempotent by key.</p>
 *
 * <p>An empty {@code samples} is valid and meaningful: it is the namespace's heartbeat, saying the
 * sampler ran and found no tenant to measure. Without it a namespace with no tenants would be
 * indistinguishable from one whose sampler has stopped.</p>
 *
 * <p>The wire keys are the component names; see {@link StorageSample} for why.</p>
 *
 * @param namespace the Kubernetes namespace the sampler runs in, recorded on every row — held to
 *                  the same spelling rules as {@link UsageBatchRequest#namespace()}
 * @param sampledAt the start of the sampling period — midnight UTC while the cadence is daily.
 *                  Never in the future, enforced here; web-global additionally refuses a value
 *                  not aligned to the period (a misaligned value would write a second row for
 *                  the same period) or older than a few periods (an uninitialized or sign-flipped
 *                  producer value would otherwise write a row pricing reads). Both are refusals,
 *                  never silent corrections
 * @param samples   one entry per tenant measured, at most {@value #MAX_SAMPLES}; empty for a
 *                  heartbeat
 */
public record StorageSampleBatchRequest(
		@NotBlank @CodePointSize(max = UsageBatchRequest.MAX_NAMESPACE_LENGTH) String namespace,
		@NotNull @PastOrPresent OffsetDateTime sampledAt,
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
