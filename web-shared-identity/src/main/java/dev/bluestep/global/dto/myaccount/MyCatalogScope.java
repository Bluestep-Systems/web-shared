package dev.bluestep.global.dto.myaccount;

import java.time.Instant;
import java.util.Objects;
import java.util.Optional;

import org.jspecify.annotations.Nullable;

import dev.bluestep.global.dto.consoleglobaluser.ConsoleReseller;
import dev.bluestep.global.dto.globaluser.CatalogScopeKind;

/**
 * The signed-in account's recorded tenant-catalog scope (from 6.14.0): {@code ConsoleCatalogScope} without the
 * operators' reason.
 *
 * @param kind       fleet, one reseller's tenants, or vouched tenants only
 * @param reseller   the reseller of a {@link CatalogScopeKind#RESELLER} scope, and of no other
 * @param recordedAt when it was last recorded
 */
public record MyCatalogScope(CatalogScopeKind kind, @Nullable ConsoleReseller reseller, Instant recordedAt) {

	/** Requires the kind and stamp, and a reseller exactly for a reseller scope. */
	public MyCatalogScope {
		Objects.requireNonNull(kind, "kind");
		Objects.requireNonNull(recordedAt, "recordedAt");
		if ((kind == CatalogScopeKind.RESELLER) != (reseller != null)) {
			throw new IllegalArgumentException("A reseller belongs to a RESELLER scope and to no other");
		}
	}

	/** {@link #reseller}, as an {@code Optional}. */
	public Optional<ConsoleReseller> optReseller() {
		return Optional.ofNullable(reseller);
	}
}
