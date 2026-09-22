package dev.bluestep.global.dto.usage;

import java.time.OffsetDateTime;

/**
 * A tenant's storage as its most recent sample recorded it — the tenant-facing view of one
 * {@link StorageSample}. It has no physical-bytes figure by construction; see
 * {@link InternalStorageUsageResponse}.
 *
 * @param sampledAt        the sampling period the figures belong to
 * @param dbBytes          the schema's relation footprint
 * @param fileLogicalBytes the tenant's documents at face size (the billed figure)
 */
public record StorageLevel(OffsetDateTime sampledAt, long dbBytes, long fileLogicalBytes) {
}
