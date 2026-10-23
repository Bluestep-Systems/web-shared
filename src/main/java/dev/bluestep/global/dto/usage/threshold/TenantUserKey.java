package dev.bluestep.global.dto.usage.threshold;

/**
 * A user of the threshold's own tenant, as the monolith keys it: the {@code (classid, seqnum)} pair
 * of the tenant's user row. The recipient of an {@link NotifyChannel#EMAIL} or
 * {@link NotifyChannel#IN_APP} notice; web resolves it to an address or an alert inbox when it
 * sends.
 *
 * <p>Its own type rather than {@code GlobalUserKey}: a tenant user is not a global user, and a notice
 * goes to the user the tenant names, in that tenant. Only keys, never free email addresses, so every
 * notice reaches someone the tenant's own user list vouches for.</p>
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
