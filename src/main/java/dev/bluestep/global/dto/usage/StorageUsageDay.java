package dev.bluestep.global.dto.usage;

import java.time.LocalDate;

/**
 * One tenant's storage over one UTC day, as web-global's daily storage rollup holds it — the row
 * pricing reads, so a report built from these cannot disagree with an invoice.
 *
 * @param day                  the UTC day
 * @param sampleCount          how many samples the day's figures were built from. Zero never
 *                             appears (a day with no sample has no row); a count below the
 *                             cadence's expectation is a sampler gap, and the byte-hours below
 *                             under-state the day by that much
 * @param maxDbBytes           the day's largest {@code dbBytes}
 * @param maxFileLogicalBytes  the day's largest {@code fileLogicalBytes}
 * @param dbByteHours          {@code dbBytes} integrated over the day by web-global's rollup, in
 *                             byte-hours
 * @param fileLogicalByteHours {@code fileLogicalBytes} integrated over the day, in byte-hours
 */
public record StorageUsageDay(
		LocalDate day,
		int sampleCount,
		long maxDbBytes,
		long maxFileLogicalBytes,
		long dbByteHours,
		long fileLogicalByteHours) {
}
