package dev.bluestep.global.dto.usage;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonProperty;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * One producer's minute batch: every window it accumulated since the last push, under one batch id.
 *
 * <p><b>The single-character keys are the wire contract.</b> Every producer and web-global's
 * ingest bind this one record, but a deployed pod keeps pushing whatever keys its jar was built
 * with — so renaming any {@code @JsonProperty} value breaks every pod still on an older release.
 * {@code UsageWireContractTest} pins each key so a drift fails this build rather than the
 * fleet.</p>
 *
 * <p>The batch id is what makes a retried push safe: the transport is fire-and-forget with
 * bounded retry, so the same batch can legitimately arrive twice, and web-global's additive
 * upsert would double-count it without a marker saying "already folded in". The id is
 * pod-generated UUID text and treated as opaque here — see
 * web-global's {@code UsageIngestService} for the dedup's scope and lifetime.</p>
 *
 * @param batchId   producer-generated UUID text, unique per push; a batch retried after a
 *                  transport failure carries the same id
 * @param namespace the Kubernetes namespace the producer runs in, recorded on every row of the
 *                  batch — a billing row must say where usage historically happened, and the
 *                  producer is the only party that knows. Trimmed and lowercased at ingest, then
 *                  held to {@code UsageIngestService.KEY_SPELLING}: it is a primary-key column,
 *                  and {@code b6p-7} beside {@code b6p-07} is one tenant-minute billed twice
 * @param windows   the accumulated windows; an omitted or empty list is a legitimate quiet minute,
 *                  and at most {@value #MAX_WINDOWS} of them
 */
public record UsageBatchRequest(
		@NotBlank @Size(max = MAX_BATCH_ID_LENGTH) @JsonProperty("b") String batchId,
		@NotBlank @Size(max = MAX_NAMESPACE_LENGTH) @JsonProperty("n") String namespace,
		@Size(max = MAX_WINDOWS) @JsonProperty("w") List<@Valid UsageWindow> windows) {

	/**
	 * The most windows one push may carry: 10,000.
	 *
	 * <p>Without a cap the batch size is whatever a caller cares to send, and the whole batch is
	 * one transaction — so a single push could hold the ingest connection, and the row locks it
	 * takes on {@code tenant_usage}, for as long as it liked. The number is far above any honest
	 * producer: a window is one (tenant, minute, category) triple, so filling this would mean one
	 * pod serving 10,000 distinct tenants in a minute — while still bounding the work an unbounded
	 * caller can hand this endpoint.</p>
	 */
	public static final int MAX_WINDOWS = 10_000;

	/** Length bound on the opaque batch id — a UUID's 36 characters with room to spare. */
	public static final int MAX_BATCH_ID_LENGTH = 64;

	/**
	 * Length bound on {@code namespace}, matching {@code varchar(255)} on
	 * {@code tenant_usage.namespace} (Kubernetes itself stops at 63).
	 */
	public static final int MAX_NAMESPACE_LENGTH = 255;

	/**
	 * Normalises the list's absence and takes a defensive copy of a present one.
	 *
	 * <p>Jackson binds an omitted {@code "w"} to {@code null} whatever this package's null-marking
	 * says, and a producer with a quiet minute may omit it — so absence is folded into the empty
	 * batch it means rather than surfacing as a null component.</p>
	 */
	public UsageBatchRequest {
		windows = windows == null ? List.of() : List.copyOf(windows);
	}
}
