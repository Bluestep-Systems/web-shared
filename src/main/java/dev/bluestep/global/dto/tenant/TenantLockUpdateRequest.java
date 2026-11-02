package dev.bluestep.global.dto.tenant;

import java.util.Optional;

import org.jspecify.annotations.Nullable;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;

/**
 * Setting a tenant's own lock (from 6.0.0): {@code PUT /tenants/{classid}/{seqnum}/lock}, answered with
 * the {@link TenantLock} and the tenant's new version as the {@code ETag}.
 *
 * <p>{@code If-Match} is optional here: an operator's lock write today is unconditional, and an absent
 * tag keeps it so. A present one is checked like any other.</p>
 *
 * @param lock  the lock, in full
 * @param actor who is setting it, or absent for no actor
 */
public record TenantLockUpdateRequest(
		@NotNull @Valid TenantLock lock,
		@Nullable TenantActor actor) {

	/** {@link #actor}, as an {@code Optional}. */
	public Optional<TenantActor> optActor() {
		return Optional.ofNullable(actor);
	}
}
