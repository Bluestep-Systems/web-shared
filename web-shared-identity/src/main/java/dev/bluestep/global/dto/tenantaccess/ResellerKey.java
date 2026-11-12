package dev.bluestep.global.dto.tenantaccess;

/**
 * A reseller's identity, as the monolith spells it: the {@code (classid, seqnum)} pair carried by
 * {@code global.globaluser.classidreseller/seqreseller} and {@code global.association}'s matching
 * columns.
 *
 * <p>Its own type rather than a reused {@link GlobalUserKey}, though the shape is identical. The two
 * name different things, and the one place they meet is a comparison that decides which tenants a
 * caller is shown — exactly where mistaking one for the other would be least visible and most
 * costly.</p>
 *
 * <p>Both halves, for the same reason a user key needs both: {@code seqnum} is unique only within a
 * {@code classid}.</p>
 */
public record ResellerKey(long classid, long seqnum) {

	/** The canonical rendering used in log lines: {@code 111222:1}. */
	@Override
	public String toString() {
		return classid + ":" + seqnum;
	}
}
