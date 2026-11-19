package dev.bluestep.global.dto.consoleusage;

import java.util.Objects;

import dev.bluestep.global.dto.usage.StorageUsageResponse;
import dev.bluestep.global.dto.usage.UsageSeriesResponse;

/**
 * web-global's answer to {@code GET /api/contract/console-usage/{tenantId}?days=N} (from 6.12.0): one tenant's request
 * metrics and storage over its last {@code N} complete UTC days, in one fetch, for the gateway's Console usage and
 * storage tabs.
 *
 * <p>Both halves are the shapes the tenant-visible usage reads already answer, so a consumer that binds those binds
 * these: {@code usage} is a {@link UsageSeriesResponse} at day grain for the {@code http} and {@code file}
 * categories (the foreground traffic a usage chart draws), and {@code storage} is a {@link StorageUsageResponse}
 * carrying only the meters web-global's catalog marks tenant-visible ({@code db} and {@code file_logical}). Both cover
 * the same days: {@code N} whole UTC days ending with yesterday, so no bucket is still open when it is read. Nothing
 * is made up: a day with no stored usage row is a zero point (no traffic, over a range web-global still keeps), a
 * storage day with no sample of a meter has no row for it, and a meter never sampled is absent from
 * {@code storage.latest()}.</p>
 *
 * @param usage   the request series, day grain, {@code http} then {@code file}
 * @param storage the storage levels and daily series over the same days
 */
public record ConsoleUsageResponse(UsageSeriesResponse usage, StorageUsageResponse storage) {

	/** Requires both halves. */
	public ConsoleUsageResponse {
		Objects.requireNonNull(usage, "usage");
		Objects.requireNonNull(storage, "storage");
	}
}
