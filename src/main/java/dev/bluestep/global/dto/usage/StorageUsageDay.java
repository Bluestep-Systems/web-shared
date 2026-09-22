package dev.bluestep.global.dto.usage;

import java.time.LocalDate;

/**
 * One tenant's storage over one UTC day, as web-global's daily storage rollup holds it — the row
 * pricing reads, so a report built from these cannot disagree with an invoice.
 *
 * @param day                  the UTC day
 * @param sampleCount          how many samples fell in the day. Zero never appears (a day with no
 *                             sample has no row); a count below the cadence's expectation means
 *                             the sampler missed runs, and the day's figures rest on fewer
 *                             measurements than usual
 * @param maxDbBytes           the day's largest {@code dbBytes}
 * @param maxFileLogicalBytes  the day's largest {@code fileLogicalBytes}
 * @param dbByteHours          {@code dbBytes} over time, in byte-hours, as web-global's daily
 *                             rollup integrates it — that rollup, not this record, defines how
 *                             samples are credited to days
 * @param fileLogicalByteHours {@code fileLogicalBytes} over time, in byte-hours, integrated the
 *                             same way
 */
public record StorageUsageDay(
		LocalDate day,
		int sampleCount,
		long maxDbBytes,
		long maxFileLogicalBytes,
		long dbByteHours,
		long fileLogicalByteHours) {
}
