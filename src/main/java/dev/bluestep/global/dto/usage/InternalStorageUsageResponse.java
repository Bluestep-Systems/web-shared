package dev.bluestep.global.dto.usage;

import java.util.List;
import java.util.Objects;
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
 * <p>The two series are parallel, and the constructor refuses one that is not: a consumer may pair
 * {@code physicalDays} with {@code usage.days()} by index. A mismatch therefore fails where the
 * response is built — on the server, or on binding a malformed body — instead of silently pairing
 * margin figures with the wrong day.</p>
 *
 * @param usage                   exactly what a tenant-facing caller would receive for the same
 *                                request
 * @param latestFilePhysicalBytes the newest sample's {@code filePhysicalBytes}; present exactly
 *                                when {@code usage.latest()} is
 * @param physicalDays            one entry per entry of {@code usage.days()}, with the same
 *                                {@code day} at each index
 */
public record InternalStorageUsageResponse(
		StorageUsageResponse usage,
		Optional<Long> latestFilePhysicalBytes,
		List<StoragePhysicalDay> physicalDays) {

	/**
	 * Folds absent optional and list components into their empty forms, and refuses a response
	 * whose physical figures do not line up with {@code usage}.
	 *
	 * @throws NullPointerException     if {@code usage} is absent — without it there is nothing the
	 *                                  physical figures describe
	 * @throws IllegalArgumentException if {@code latestFilePhysicalBytes} and {@code usage.latest()}
	 *                                  disagree on presence, or the day series differ in length or in
	 *                                  the day at any index
	 */
	public InternalStorageUsageResponse {
		Objects.requireNonNull(usage, "usage");
		latestFilePhysicalBytes = latestFilePhysicalBytes == null ? Optional.empty() : latestFilePhysicalBytes;
		physicalDays = physicalDays == null ? List.of() : List.copyOf(physicalDays);
		if (usage.latest().isPresent() != latestFilePhysicalBytes.isPresent()) {
			throw new IllegalArgumentException("latestFilePhysicalBytes must be present exactly when"
					+ " usage.latest() is");
		}
		final List<StorageUsageDay> days = usage.days();
		if (physicalDays.size() != days.size()) {
			throw new IllegalArgumentException("physicalDays has " + physicalDays.size()
					+ " entries but usage.days() has " + days.size());
		}
		for (int i = 0; i < days.size(); i++) {
			if (!physicalDays.get(i).day().equals(days.get(i).day())) {
				throw new IllegalArgumentException("physicalDays[" + i + "] is " + physicalDays.get(i).day()
						+ " but usage.days()[" + i + "] is " + days.get(i).day());
			}
		}
	}
}
