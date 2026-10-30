package dev.bluestep.global.dto.tenant;

/**
 * A tenant key drawn for a tenant not yet created (from 6.0.0): {@code POST /tenants/reservations},
 * answered {@code 201}.
 *
 * <p>The seqnum comes from {@code seq_111111}, the sequence the monolith's own JDBC draws from, so a key
 * reserved here and one drawn by a monolith build that has not moved yet never collide. Nothing is
 * written: a reservation that is never used is a harmless gap in the sequence.</p>
 *
 * @param tenant the key, to name in {@link TenantCreateRequest#tenant()}
 */
public record TenantReservationResponse(TenantKey tenant) {}
