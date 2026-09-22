package dev.bluestep.global.dto.usage;

import java.time.LocalDate;
import java.util.Optional;

/**
 * One tenant's storage over one UTC day, as web-global's daily storage rollup holds it — the row
 * pricing reads, so a report built from these cannot disagree with an invoice.
 *
 * <p>Each byte-hours figure sums, over the day's samples, the sample's bytes times the hours until
 * the next sample (or the end of the day). At a daily cadence that is one sample times 24; it is
 * the figure that stays correct if the cadence moves to hourly.</p>
 *
 * @param day                   the UTC day
 * @param maxDbBytes            the day's largest {@code dbBytes}
 * @param maxFileLogicalBytes   the day's largest {@code fileLogicalBytes}
 * @param dbByteHours           {@code dbBytes} integrated over the day, in byte-hours
 * @param fileLogicalByteHours  {@code fileLogicalBytes} integrated over the day, in byte-hours
 * @param maxFilePhysicalBytes  the day's largest {@code filePhysicalBytes}; internal-admin only,
 *                              as on {@link StorageLevel#filePhysicalBytes()}
 * @param filePhysicalByteHours {@code filePhysicalBytes} integrated over the day; internal-admin
 *                              only
 */
public record StorageUsageDay(
		LocalDate day,
		long maxDbBytes,
		long maxFileLogicalBytes,
		long dbByteHours,
		long fileLogicalByteHours,
		Optional<Long> maxFilePhysicalBytes,
		Optional<Long> filePhysicalByteHours) {
}
