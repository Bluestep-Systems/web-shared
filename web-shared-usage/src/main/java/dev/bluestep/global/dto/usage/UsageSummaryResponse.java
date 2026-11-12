package dev.bluestep.global.dto.usage;

import java.time.OffsetDateTime;
import java.util.List;

/**
 * Per-tenant usage totals over a time range, one row per tenant — the answer to
 * {@code GET /api/v1/usage/summary?from=&to=&category=…[&schema=]}.
 *
 * <p>The figures fold every grain web-global holds: its minute rows and the hour and day rows those
 * age into. A row counts when its window <em>starts</em> inside {@code [from, to)}, so a range
 * edge falling inside an hour or day row takes that row whole. Past about seven days the effective
 * resolution is therefore the hour, and past about ninety days the day.</p>
 *
 * @param from       the start of the range, inclusive
 * @param to         the end of the range, exclusive
 * @param categories the usage categories the totals cover, as requested
 * @param tenants    one row per tenant with usage in the range, ordered by schema name; a tenant
 *                   with none has no row
 */
public record UsageSummaryResponse(
		OffsetDateTime from,
		OffsetDateTime to,
		List<String> categories,
		List<UsageSummaryRow> tenants) {

	/**
	 * Folds an absent list into its empty form and takes a defensive copy of a present one, so a
	 * Java caller passing {@code null} gets what an omitted JSON key binds to.
	 */
	public UsageSummaryResponse {
		categories = categories == null ? List.of() : List.copyOf(categories);
		tenants = tenants == null ? List.of() : List.copyOf(tenants);
	}
}
