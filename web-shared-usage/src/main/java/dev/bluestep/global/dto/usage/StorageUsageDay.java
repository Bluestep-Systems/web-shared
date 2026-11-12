package dev.bluestep.global.dto.usage;

import java.time.LocalDate;

/**
 * One tenant's storage on one meter over one UTC day, as web-global's daily storage rollup holds it
 * — the row pricing reads, so a report built from these cannot disagree with an invoice.
 *
 * @param day         the UTC day
 * @param meter       the meter key (see {@link StorageSample})
 * @param sampleCount how many samples of this meter fell in the day. Zero never appears (a day with
 *                    no sample has no row); a count below the cadence's expectation means the
 *                    sampler missed runs, and the day's figures rest on fewer measurements than
 *                    usual
 * @param maxBytes    the day's largest level
 * @param byteHours   the level over time, in byte-hours, as web-global's daily rollup integrates it
 *                    — that rollup, not this record, defines how samples are credited to days
 */
public record StorageUsageDay(LocalDate day, String meter, int sampleCount, long maxBytes,
		long byteHours) {
}
