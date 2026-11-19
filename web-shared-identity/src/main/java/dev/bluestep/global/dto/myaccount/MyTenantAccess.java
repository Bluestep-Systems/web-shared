package dev.bluestep.global.dto.myaccount;

import java.time.Instant;
import java.util.Objects;
import java.util.Optional;

import org.jspecify.annotations.Nullable;

import dev.bluestep.global.dto.tenantaccess.TenantAccessGrantSource;

/**
 * One tenant the signed-in account is vouched for, live or lapsed (from 6.14.0), with the tenant's name. Who wrote
 * it is a kind ({@link #source}), never a person.
 *
 * @param tenantId   the tenant's {@code U<seqnum>}
 * @param tenantName its name (else its display name, else its {@code U<seqnum>}); absent when web-global holds no
 *                   such tenant any more
 * @param grantedAt  when the grant was written
 * @param expiresAt  when it lapses; absent for a grant with no end
 * @param source     who wrote it: a super, the tenant's own administrators, or operations
 */
public record MyTenantAccess(String tenantId, @Nullable String tenantName, Instant grantedAt,
		@Nullable Instant expiresAt, TenantAccessGrantSource source) {

	/** Requires every member but the name and the expiry. */
	public MyTenantAccess {
		Objects.requireNonNull(tenantId, "tenantId");
		Objects.requireNonNull(grantedAt, "grantedAt");
		Objects.requireNonNull(source, "source");
	}

	/** {@link #tenantName}, as an {@code Optional}. */
	public Optional<String> optTenantName() {
		return Optional.ofNullable(tenantName);
	}

	/** {@link #expiresAt}, as an {@code Optional}. */
	public Optional<Instant> optExpiresAt() {
		return Optional.ofNullable(expiresAt);
	}
}
