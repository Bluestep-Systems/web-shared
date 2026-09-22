package dev.bluestep.global.dto.usage;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

/**
 * One tenant's storage usage: its newest sample and its daily series over the requested range —
 * the tenant-facing answer to {@code GET /api/v1/usage/storage?schema=&from=&to=}.
 *
 * <p>Carries no physical-bytes figure anywhere, by type rather than by convention: the internal
 * view is {@link InternalStorageUsageResponse}, which wraps this one, so a handler that answers a
 * tenant with it cannot leak margin data without changing its declared return type.</p>
 *
 * @param schemaName the canonical {@code U<seqnum>} the figures belong to
 * @param from       the first UTC day of the range the series covers, as resolved by web-global
 * @param to         the last UTC day of the range, inclusive
 * @param latest     the newest sample, whatever the range; empty when the tenant has never been
 *                   sampled
 * @param days       the daily rows inside the range, oldest first; a day with no row had no
 *                   sample at all
 */
public record StorageUsageResponse(
		String schemaName,
		LocalDate from,
		LocalDate to,
		Optional<StorageLevel> latest,
		List<StorageUsageDay> days) {

	/**
	 * Takes a defensive copy of {@code days}, folding an absent list — which is what Jackson binds
	 * an omitted key to — into the empty series it means.
	 */
	public StorageUsageResponse {
		days = days == null ? List.of() : List.copyOf(days);
	}
}
