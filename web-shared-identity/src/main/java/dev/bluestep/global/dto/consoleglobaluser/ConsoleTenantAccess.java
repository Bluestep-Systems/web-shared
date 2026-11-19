package dev.bluestep.global.dto.consoleglobaluser;

import java.time.Instant;
import java.util.Objects;
import java.util.Optional;

import org.jspecify.annotations.Nullable;

import dev.bluestep.global.dto.tenantaccess.TenantAccessGrantSource;

/**
 * One tenant a global user is granted access to, as {@code tenant_user_access} holds it (from 6.13.0). The table
 * records who wrote a grant by kind ({@link #source}) and not by person, so no granter is named.
 *
 * @param tenantId  the tenant's {@code U<seqnum>}
 * @param grantedAt when the grant was written
 * @param expiresAt when it lapses; absent for a grant with no end
 * @param source    who wrote it: a super, the tenant's own administrators, or operations
 */
public record ConsoleTenantAccess(String tenantId, Instant grantedAt, @Nullable Instant expiresAt,
		TenantAccessGrantSource source) {

	/** Requires every member but the expiry. */
	public ConsoleTenantAccess {
		Objects.requireNonNull(tenantId, "tenantId");
		Objects.requireNonNull(grantedAt, "grantedAt");
		Objects.requireNonNull(source, "source");
	}

	/** {@link #expiresAt}, as an {@code Optional}. */
	public Optional<Instant> optExpiresAt() {
		return Optional.ofNullable(expiresAt);
	}
}
