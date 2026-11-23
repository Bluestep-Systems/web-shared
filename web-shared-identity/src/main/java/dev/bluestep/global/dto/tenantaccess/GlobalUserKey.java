package dev.bluestep.global.dto.tenantaccess;

/**
 * A global user's identity, as the monolith spells it: the {@code (classid, seqnum)} pair that keys
 * {@code global.globaluser} and that {@code <tenant_schema>.securityuser} points back at through its
 * {@code classiduser} / {@code sequser} columns.
 *
 * <p>Both halves are required. {@code seqnum} alone is not an identity — it is only unique within a
 * classid — so a report that carried the sequence number by itself would grant access to whichever
 * user happened to share the number under another class.</p>
 */
public record GlobalUserKey(long classid, long seqnum) {

	/** The canonical rendering used in log lines and cache keys: {@code 7000:42}. */
	@Override
	public String toString() {
		return classid + ":" + seqnum;
	}
}
