package dev.bluestep.global.dto.usage;

import java.time.LocalDate;

/**
 * One tenant's amortized physical file storage over one UTC day — the internal-only companion to
 * a {@link StorageUsageDay} with the same {@code day}.
 *
 * @param day                   the UTC day
 * @param maxFilePhysicalBytes  the day's largest {@code filePhysicalBytes}
 * @param filePhysicalByteHours {@code filePhysicalBytes} integrated over the day, in byte-hours
 */
public record StoragePhysicalDay(LocalDate day, long maxFilePhysicalBytes, long filePhysicalByteHours) {
}
