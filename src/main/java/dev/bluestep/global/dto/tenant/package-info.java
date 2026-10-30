/**
 * Wire shapes for a tenant: the monolith's {@code Association} (classid 111111), its root
 * organization and its sign-in type, as one record of web-global's {@code global.tenant} table.
 *
 * <ul>
 *   <li>{@link TenantKey} names a tenant by its {@code (classid, seqnum)}; {@code U<seqnum>} is its
 *       schema.</li>
 *   <li>{@link TenantRecordResponse} is one tenant, every column the monolith reads, grouped:
 *       {@link TenantFeatures}, {@link TenantLock}, {@link TenantSignIn}, {@link TenantStorage},
 *       {@link TenantOrganization}; and who created and last edited it, a {@link TenantActor}.</li>
 *   <li>{@link TenantSnapshotResponse} is every tenant reduced to what a monolith pod keeps in memory
 *       for its hot paths (the per-tenant switches, the namespace map, the lock status): one read
 *       instead of a traversal.</li>
 * </ul>
 *
 * <h2>Reads, from 5.5.0</h2>
 *
 * <p>web-global serves these under {@code /api/v5.5.0/tenants}: a paged listing, one tenant by key
 * with its version as the {@code ETag}, the snapshot, and one tenant's lock. Until 6.0.0 every write
 * went through the monolith's own rows, which web-global keeps in step with {@code global.tenant} by
 * trigger (its changeset 027); a monolith build that has not moved still writes them that way.</p>
 *
 * <h2>Writes, from 6.0.0</h2>
 *
 * <p>{@link TenantReservationResponse} draws a key; {@link TenantCreateRequest} creates the tenant under
 * it; {@link TenantUpdateRequest} replaces its {@link TenantSettings}; {@link TenantLockUpdateRequest}
 * sets its lock; a delete takes no body. A write that something stands in the way of answers
 * {@code 409} with a {@link TenantWriteRefusal} or {@link TenantDeleteRefusal}, whose reasons are kinds,
 * never ids. Every write names its actor ({@code creator} on a create), absent for none. web-global keeps
 * the monolith's legacy rows in step by trigger, so a monolith build still reading or writing them
 * sees the same tenant.</p>
 *
 * <h2>Values are served as the monolith reads them</h2>
 *
 * <p>A {@code smallint} switch is {@code true} when non-zero and {@code false} when zero or absent; an
 * absent {@code flags} word or lock status is zero. That is what the monolith's own loader made of
 * those columns, so a reader switching from it to this contract sees the same tenant. Everything
 * else the column leaves absent is absent here.</p>
 *
 * <h2>Null-marked</h2>
 *
 * <p>Every type in this package is non-null unless annotated {@link org.jspecify.annotations.Nullable}.
 * A record component that may be absent is a {@code @Nullable} value, and the record declares an
 * {@code optX()} accessor that wraps it; outside the record, read the accessor. An absent key and an
 * explicit JSON {@code null} both bind as absent.</p>
 */
@NullMarked
package dev.bluestep.global.dto.tenant;

import org.jspecify.annotations.NullMarked;
