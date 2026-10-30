package dev.bluestep.global.dto.domain;

/**
 * A domain, as the monolith keys it: its {@code DomainName} row's {@code (classid, seqnum)}. Every
 * domain's classid is 120100; a path naming another classid names no domain and is a {@code 404}.
 *
 * <p>Its own type rather than a bare {@code dev.bluestep.global.dto.basetable.BaseTableKey}, so a
 * domain cannot be passed where a tenant, an organization or any other object is meant.</p>
 *
 * @param classid the domain's class, 120100
 * @param seqnum  the domain's sequence number, drawn from the monolith's {@code seq_120100}
 */
public record DomainKey(long classid, long seqnum) {

	/** The classid every domain carries: the monolith's {@code myassn.application.DomainName}. */
	public static final long CLASSID = 120_100L;

	/** The canonical rendering used in log lines: {@code 120100:5531}. */
	@Override
	public String toString() {
		return classid + ":" + seqnum;
	}
}
