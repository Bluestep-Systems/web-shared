package dev.bluestep.global.dto.consoleglobaluser;

import java.time.Instant;
import java.util.Objects;
import java.util.Optional;

import org.jspecify.annotations.Nullable;

import dev.bluestep.global.dto.globaluser.CatalogScopeKind;

/**
 * An account's recorded tenant-catalog scope, as {@code global_user_scope} holds it (from 6.13.0): how wide the
 * tenant catalog it reaches is.
 *
 * @param kind       fleet, one reseller's tenants, or vouched tenants only
 * @param reseller   the reseller of a {@link CatalogScopeKind#RESELLER} scope, and of no other
 * @param reason     why it was recorded, as written
 * @param recordedAt when it was last recorded
 */
public record ConsoleCatalogScope(CatalogScopeKind kind, @Nullable ConsoleReseller reseller, String reason,
		Instant recordedAt) {

	/** Requires every member but the reseller, which a reseller scope alone carries. */
	public ConsoleCatalogScope {
		Objects.requireNonNull(kind, "kind");
		Objects.requireNonNull(reason, "reason");
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
