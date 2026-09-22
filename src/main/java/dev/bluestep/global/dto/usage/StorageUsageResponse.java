package dev.bluestep.global.dto.usage;

import java.util.List;
import java.util.Optional;

/**
 * One tenant's storage usage: its newest sample and its daily series over the requested range —
 * the answer to {@code GET /api/v1/usage/storage?schema=&from=&to=}.
 *
 * @param schemaName the canonical {@code U<seqnum>} the figures belong to
 * @param latest     the newest sample, whatever the range; empty when the tenant has never been
 *                   sampled
 * @param days       the daily rows inside the range, oldest first; empty when none fall in it
 */
public record StorageUsageResponse(
		String schemaName,
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
