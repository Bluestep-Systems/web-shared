package dev.bluestep.global.dto.consoledirectory;

import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.OptionalLong;

import org.jspecify.annotations.Nullable;

import dev.bluestep.global.dto.tenant.TenantKey;

/**
 * What web-global holds about one tenant beyond its record, for the Console's directory (from 6.10.0).
 *
 * <p>A level or a last activity that web-global holds nothing for is absent, never zero: {@code null} dbBytes means
 * no sample of the {@code db} meter, which is not an empty database.</p>
 *
 * @param tenant       the tenant
 * @param hostNames    every host name serving it, in order; empty when none
 * @param dbBytes      the newest level of the {@code db} meter (the tenant schema's relation footprint), bytes;
 *                     absent when never sampled
 * @param fileBytes    the newest level of the {@code file_logical} meter (what the tenant uploaded, at face size),
 *                     bytes; absent when never sampled
 * @param lastActivity the start of the newest stored usage window with traffic (a window is a minute, an hour or a
 *                     day, depending on its age); absent when it has none within web-global's lookback (90 days at
 *                     present, which web-global may change without a new tag)
 */
public record ConsoleDirectoryTenant(
		TenantKey tenant,
		List<String> hostNames,
		@Nullable Long dbBytes,
		@Nullable Long fileBytes,
		@Nullable Instant lastActivity) {

	/** Requires the key, copies the host names, and refuses a negative level. */
	public ConsoleDirectoryTenant {
		Objects.requireNonNull(tenant, "tenant");
		hostNames = List.copyOf(hostNames);
		if (dbBytes != null && dbBytes < 0 || fileBytes != null && fileBytes < 0) {
			throw new IllegalArgumentException("A storage level is not negative");
		}
	}

	/** {@link #dbBytes}, as an {@code OptionalLong}. */
	public OptionalLong optDbBytes() {
		return dbBytes == null ? OptionalLong.empty() : OptionalLong.of(dbBytes);
	}

	/** {@link #fileBytes}, as an {@code OptionalLong}. */
	public OptionalLong optFileBytes() {
		return fileBytes == null ? OptionalLong.empty() : OptionalLong.of(fileBytes);
	}

	/** {@link #lastActivity}, as an {@code Optional}. */
	public Optional<Instant> optLastActivity() {
		return Optional.ofNullable(lastActivity);
	}
}
