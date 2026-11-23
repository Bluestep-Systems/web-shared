package dev.bluestep.global.dto.usage;

import java.time.LocalDate;
import java.util.List;

/**
 * One tenant's storage usage: each meter's newest level and the daily series over the requested
 * range — the answer to {@code GET /api/v1/usage/storage?schema=&from=&to=} and its
 * {@code /internal} twin.
 *
 * <p>One shape for both audiences. Which meters appear is decided by web-global's
 * {@code storage_meter} catalog: the tenant path carries only the meters marked tenant-visible,
 * the internal path carries every meter. A consumer must not assume a fixed meter set.</p>
 *
 * @param schemaName the canonical {@code U<seqnum>} the figures belong to
 * @param from       the first UTC day of the range the series covers, as resolved by web-global
 * @param to         the last UTC day of the range, inclusive
 * @param latest     each meter's newest raw level, whatever the range; a meter is absent when it
 *                   has no raw sample left — never sampled, or not within web-global's raw-sample
 *                   retention, even though its dailies may still be present
 * @param days       the daily rows inside the range, ordered by day then meter; a day with no row
 *                   for a meter had no sample of it
 */
public record StorageUsageResponse(
		String schemaName,
		LocalDate from,
		LocalDate to,
		List<StorageLevel> latest,
		List<StorageUsageDay> days) {

	/**
	 * Folds an absent list into its empty form and takes a defensive copy of a present one, so a
	 * Java caller passing {@code null} gets what an omitted JSON key binds to.
	 */
	public StorageUsageResponse {
		latest = latest == null ? List.of() : List.copyOf(latest);
		days = days == null ? List.of() : List.copyOf(days);
	}
}
