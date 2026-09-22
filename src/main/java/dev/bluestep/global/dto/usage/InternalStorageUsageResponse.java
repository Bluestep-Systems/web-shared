package dev.bluestep.global.dto.usage;

import java.util.List;
import java.util.Optional;

/**
 * The internal-admin view of one tenant's storage: the tenant-facing figures plus the amortized
 * physical file bytes that feed the margin report.
 *
 * <p>Physical bytes move when a <em>different</em> tenant uploads the same file, so they are never
 * billed and never shown to a tenant. Keeping them on a separate type that wraps
 * {@link StorageUsageResponse}, rather than as optional fields on it, makes that a property of the
 * signature: only a handler declared to return this type can emit them.</p>
 *
 * @param usage                   exactly what a tenant-facing caller would receive for the same
 *                                request
 * @param latestFilePhysicalBytes the newest sample's {@code filePhysicalBytes}; present exactly when
 *                                {@code usage.latest()} is
 * @param physicalDays            one entry per day in {@code usage.days()}, same days, same order
 */
public record InternalStorageUsageResponse(
		StorageUsageResponse usage,
		Optional<Long> latestFilePhysicalBytes,
		List<StoragePhysicalDay> physicalDays) {

	/**
	 * Takes a defensive copy of {@code physicalDays}, folding an absent list into the empty series
	 * it means.
	 */
	public InternalStorageUsageResponse {
		physicalDays = physicalDays == null ? List.of() : List.copyOf(physicalDays);
	}
}
