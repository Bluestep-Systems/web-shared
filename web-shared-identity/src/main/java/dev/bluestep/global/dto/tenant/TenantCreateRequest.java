package dev.bluestep.global.dto.tenant;

import java.util.Optional;

import org.jspecify.annotations.Nullable;

import dev.bluestep.global.dto.constraints.CodePointSize;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;

/**
 * Creating a tenant (from 6.0.0): {@code POST /tenants}, answered {@code 201} with the
 * {@link TenantRecordResponse}, its version as the {@code ETag} and its {@code Location}.
 *
 * <h2>The key comes first, from a reservation</h2>
 *
 * <p>The monolith names a tenant's schema {@code U<seqnum>} and clones it before the tenant exists in
 * the global database, so the key is drawn first, by {@code POST /tenants/reservations}
 * ({@link TenantReservationResponse}), and named here. Its classid must be {@link TenantKey#CLASSID}.
 * A key already held is a {@code 409} {@link TenantWriteRefusal.Reason#KEY_TAKEN}.</p>
 *
 * @param tenant        the reserved key
 * @param associationId the monolith's free-text id, or absent for web-global to derive the usual
 *                      {@code U<seqnum>}. Held by another tenant: a {@code 409}
 *                      {@link TenantWriteRefusal.Reason#TENANT_ID_TAKEN}. Fixed once created.
 * @param settings      everything else that is written
 * @param lock          the tenant's lock, or absent for unlocked
 * @param creator       who is creating it, or absent for no actor
 */
public record TenantCreateRequest(
		@NotNull TenantKey tenant,
		@CodePointSize(max = 8) @Nullable String associationId,
		@NotNull @Valid TenantSettings settings,
		@Valid @Nullable TenantLock lock,
		@Nullable TenantActor creator) {

	/** {@link #associationId}, as an {@code Optional}. */
	public Optional<String> optAssociationId() {
		return Optional.ofNullable(associationId);
	}

	/** {@link #lock}, as an {@code Optional}. */
	public Optional<TenantLock> optLock() {
		return Optional.ofNullable(lock);
	}

	/** {@link #creator}, as an {@code Optional}. */
	public Optional<TenantActor> optCreator() {
		return Optional.ofNullable(creator);
	}
}
