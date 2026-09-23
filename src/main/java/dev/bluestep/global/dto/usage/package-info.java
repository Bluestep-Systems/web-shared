/**
 * Wire shapes for web-global's tenant usage metering endpoints.
 *
 * <ul>
 *   <li>{@code POST /api/v1/usage/batch} — {@link UsageBatchRequest} of {@link UsageWindow}s, the
 *       per-minute request and background-job aggregates. Additive upsert.</li>
 *   <li>{@code POST /api/v1/usage/storage-samples} — {@link StorageSampleBatchRequest} of
 *       {@link StorageSample}s, one sampling pass of a namespace's per-tenant storage levels.
 *       Replacing upsert.</li>
 *   <li>{@code GET /api/v1/usage/storage} and {@code GET /api/v1/usage/storage/internal} —
 *       {@link StorageUsageResponse}, one tenant's newest level and daily series per meter; the
 *       tenant path carries only the meters web-global's catalog marks tenant-visible.</li>
 * </ul>
 *
 * <p>These paths are not {@code ContractVersion}-tagged: a change to a shape here is a change
 * to what every deployed producer sends, and no versioned path absorbs it. Each record's javadoc
 * says what is pinned; {@code UsageWireContractTest} holds the keys in place.</p>
 *
 * <p>Null-marked: every type in this package is non-null unless explicitly annotated
 * {@link org.jspecify.annotations.Nullable}. Record components in particular are never
 * null — a component that may be absent is declared {@code Optional<T>} instead.</p>
 */
@NullMarked
package dev.bluestep.global.dto.usage;

import org.jspecify.annotations.NullMarked;
