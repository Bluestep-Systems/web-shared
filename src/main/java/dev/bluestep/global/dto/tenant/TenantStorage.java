package dev.bluestep.global.dto.tenant;

import java.util.Optional;

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

	/** {@link #dataAllocatedBytes}, as an {@code Optional}. */
	public Optional<Long> optDataAllocatedBytes() {
		return Optional.ofNullable(dataAllocatedBytes);
	}

	/** {@link #dataFreeBytes}, as an {@code Optional}. */
	public Optional<Long> optDataFreeBytes() {
		return Optional.ofNullable(dataFreeBytes);
	}

	/** {@link #bulkAllocatedBytes}, as an {@code Optional}. */
	public Optional<Long> optBulkAllocatedBytes() {
		return Optional.ofNullable(bulkAllocatedBytes);
	}

	/** {@link #bulkFreeBytes}, as an {@code Optional}. */
	public Optional<Long> optBulkFreeBytes() {
		return Optional.ofNullable(bulkFreeBytes);
	}
}
