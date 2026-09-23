package dev.bluestep.global.dto.usage;

import java.time.OffsetDateTime;

/**
 * One meter's most recent level for a tenant.
 *
 * @param meter     the meter key (see {@link StorageSample})
 * @param sampledAt the sampling period the level belongs to — per meter, since a meter can stop
 *                  being reported while others continue
 * @param bytes     the level
 */
public record StorageLevel(String meter, OffsetDateTime sampledAt, long bytes) {
}
