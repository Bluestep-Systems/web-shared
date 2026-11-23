package dev.bluestep.global.dto.tenant;

import java.util.Optional;

import org.jspecify.annotations.Nullable;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;

/**
 * Editing a tenant (from 6.0.0): {@code PUT /tenants/{classid}/{seqnum}} with a required
 * {@code If-Match}, answered with the {@link TenantRecordResponse} and its new version as the
 * {@code ETag}.
 *
 * <p>A whole-record replace of the {@link TenantSettings}: every column there is written, and an absent
 * value clears its own. The key and the associationid never change; the lock moves only through
 * {@link TenantLockUpdateRequest}. Changing {@code namespace} marks both the old and the new namespace
 * modified, inside the same write.</p>
 *
 * @param settings the tenant's writable columns, in full
 * @param actor    who is making the edit, or absent for no actor
 */
public record TenantUpdateRequest(
		@NotNull @Valid TenantSettings settings,
		@Nullable TenantActor actor) {

	/** {@link #actor}, as an {@code Optional}. */
	public Optional<TenantActor> optActor() {
		return Optional.ofNullable(actor);
	}
}
