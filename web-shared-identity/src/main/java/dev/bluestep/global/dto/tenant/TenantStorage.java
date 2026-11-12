package dev.bluestep.global.dto.tenant;

import java.util.OptionalLong;

import org.jspecify.annotations.Nullable;

/**
 * A tenant's storage allocation as last recorded on its row by operator tooling, in bytes. Each is
 * absent where it was never recorded.
 *
 * @param dataAllocatedBytes {@code dataallocatedbytes}
 * @param dataFreeBytes      {@code datafreebytes}
 * @param bulkAllocatedBytes {@code bulkallocatedbytes}
 * @param bulkFreeBytes      {@code bulkfreebytes}
 */
public record TenantStorage(
		@Nullable Long dataAllocatedBytes,
		@Nullable Long dataFreeBytes,
		@Nullable Long bulkAllocatedBytes,
		@Nullable Long bulkFreeBytes) {

	/** {@link #dataAllocatedBytes}, as an {@code OptionalLong}. */
	public OptionalLong optDataAllocatedBytes() {
		return dataAllocatedBytes == null ? OptionalLong.empty() : OptionalLong.of(dataAllocatedBytes);
	}

	/** {@link #dataFreeBytes}, as an {@code OptionalLong}. */
	public OptionalLong optDataFreeBytes() {
		return dataFreeBytes == null ? OptionalLong.empty() : OptionalLong.of(dataFreeBytes);
	}

	/** {@link #bulkAllocatedBytes}, as an {@code OptionalLong}. */
	public OptionalLong optBulkAllocatedBytes() {
		return bulkAllocatedBytes == null ? OptionalLong.empty() : OptionalLong.of(bulkAllocatedBytes);
	}

	/** {@link #bulkFreeBytes}, as an {@code OptionalLong}. */
	public OptionalLong optBulkFreeBytes() {
		return bulkFreeBytes == null ? OptionalLong.empty() : OptionalLong.of(bulkFreeBytes);
	}
}
