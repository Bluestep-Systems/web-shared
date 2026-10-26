package dev.bluestep.global.dto.tenantaccess;

/**
 * A user of one tenant, as the monolith keys it: the {@code (classid, seqnum)} pair of the user row
 * in that tenant's schema. The tenant itself is not part of the key; the shape that carries one
 * names the tenant beside it.
 *
 * <p>Its own type rather than {@link GlobalUserKey}: a tenant user is not a global user, and keeping
 * the two apart stops one being compared against the other. A usage notice names its recipients this
 * way ({@code dev.bluestep.global.dto.usage.threshold}), and web resolves the key to an address or an
 * alert inbox when it sends: only keys, never free email addresses, so every notice reaches someone
 * the tenant's own user list vouches for.</p>
 *
 * <p>Both halves, because {@code seqnum} is unique only within a {@code classid}.</p>
 *
 * @param classid the user row's class
 * @param seqnum  the user row's sequence number within the class
 */
public record TenantUserKey(long classid, long seqnum) {

	/** The canonical rendering used in log lines: {@code 222222:42}. */
	@Override
	public String toString() {
		return classid + ":" + seqnum;
	}
}
