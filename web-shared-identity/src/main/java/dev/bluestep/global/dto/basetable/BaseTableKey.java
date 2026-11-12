package dev.bluestep.global.dto.basetable;

/**
 * A reference to any row in {@code global.basetable}: the {@code (classid, seqnum)} pair the monolith
 * uses to name an object of any type.
 *
 * <p>Deliberately untyped, where {@code ResellerKey} and {@code GlobalUserKey} are not. Those two name
 * one kind of thing each, and keeping them apart is what stops a reseller being compared against a
 * user. This one names whatever the referring column happens to point at — the row's creator, whoever
 * last wrote it, the lock held over it — and the {@code classid} <em>is</em> the type, so a record per
 * target would be a family of structurally identical types that no call site could tell apart.</p>
 *
 * <p>Both halves, for the same reason the typed keys need both: {@code seqnum} is unique only within
 * a {@code classid}.</p>
 */
public record BaseTableKey(long classid, long seqnum) {

	/** The canonical rendering used in log lines: {@code 111222:1}. */
	@Override
	public String toString() {
		return classid + ":" + seqnum;
	}
}
