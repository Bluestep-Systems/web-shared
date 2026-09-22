package dev.bluestep.global.dto.usage;

import java.time.OffsetDateTime;
import java.util.Optional;

/**
 * A tenant's storage as its most recent sample recorded it — the read-side view of one
 * {@link StorageSample}.
 *
 * @param sampledAt         the sampling period the figures belong to
 * @param dbBytes           the schema's relation footprint
 * @param fileLogicalBytes  the tenant's documents at face size (the billed figure)
 * @param filePhysicalBytes the amortized share of real file disk; present only for the
 *                          internal-admin credential, and empty — never zero — for every other
 *                          caller, so a tenant-facing reader cannot mistake "not yours to see" for
 *                          "nothing stored"
 */
public record StorageLevel(
		OffsetDateTime sampledAt,
		long dbBytes,
		long fileLogicalBytes,
		Optional<Long> filePhysicalBytes) {
}
