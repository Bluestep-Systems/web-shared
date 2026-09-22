package dev.bluestep.global.dto.usage;

import java.util.List;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;

/**
 * One namespace's storage sampling pass: every tenant it measured, for one sampling period.
 *
 * <p>No batch id, unlike {@link UsageBatchRequest}, because none is needed. {@code sampledAt} is
 * the <em>period</em> the pass belongs to, not the moment of measurement, and web-global replaces
 * rather than adds on {@code (sampledAt, schemaName)} — so a retried push, or a pass re-run after
 * a restart, lands on the same rows and overwrites them. The endpoint is idempotent by key.</p>
 *
 * <p>The wire keys are the component names; see {@link StorageSample} for why.</p>
 *
 * @param namespace the Kubernetes namespace the sampler runs in, recorded on every row — held to
 *                  the same spelling rules as {@link UsageBatchRequest#namespace()}
 * @param sampledAt epoch millis of the start of the sampling period, UTC. The period is the UTC
 *                  day while the cadence is daily; web-global refuses a value that is not
 *                  aligned to it rather than truncating, since a misaligned value would write a
 *                  second row for the same period
 * @param samples   one entry per tenant measured, at most {@value #MAX_SAMPLES}. Never empty: a
 *                  pass that measured nothing has nothing to push
 */
public record StorageSampleBatchRequest(
		@NotBlank @Size(max = UsageBatchRequest.MAX_NAMESPACE_LENGTH) String namespace,
		long sampledAt,
		@NotEmpty @Size(max = MAX_SAMPLES) List<@Valid StorageSample> samples) {

	/**
	 * The most samples one push may carry: 10,000 — one per tenant, far above any namespace's
	 * tenant count, and the same bound {@link UsageBatchRequest#MAX_WINDOWS} puts on the length of
	 * one ingest transaction.
	 */
	public static final int MAX_SAMPLES = 10_000;

	/**
	 * Normalises the list's absence and takes a defensive copy of a present one.
	 *
	 * <p>Jackson binds an omitted {@code samples} to {@code null} whatever this package's
	 * null-marking says. Folding it to the empty list here, rather than letting
	 * {@code List.copyOf} throw during binding, is what lets {@code @NotEmpty} answer with a
	 * field-level 400 that names {@code samples}.</p>
	 */
	public StorageSampleBatchRequest {
		samples = samples == null ? List.of() : List.copyOf(samples);
	}
}
