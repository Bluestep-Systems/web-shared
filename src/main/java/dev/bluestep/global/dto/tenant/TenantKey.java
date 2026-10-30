package dev.bluestep.global.dto.tenant;

/**
 * A tenant, as the monolith keys it: its {@code Association} row's {@code (classid, seqnum)}. Every
 * tenant's classid is 111111; the seqnum names its schema, {@code U<seqnum>}.
 *
 * <p>Its own type rather than a bare {@code dev.bluestep.global.dto.basetable.BaseTableKey}, so a
 * tenant cannot be passed where a reseller, a user or any other object is meant.</p>
 *
 * @param classid the tenant's class, 111111
 * @param seqnum  the tenant's sequence number, drawn from the monolith's {@code seq_111111}
 */
public record TenantKey(long classid, long seqnum) {

	/** The classid every tenant carries: the monolith's {@code myassn.association.Association}. */
	public static final long CLASSID = 111_111L;

	/** The canonical rendering used in log lines: {@code 111111:137803}. */
	@Override
	public String toString() {
		return classid + ":" + seqnum;
	}
}
