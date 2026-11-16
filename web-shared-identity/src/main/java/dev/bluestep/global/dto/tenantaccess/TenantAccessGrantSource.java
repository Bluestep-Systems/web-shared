package dev.bluestep.global.dto.tenantaccess;

import com.fasterxml.jackson.annotation.JsonCreator;

/**
 * Who wrote a tenant-access grant (7.0.0). The monolith decides from it whether a grant makes its global user one of
 * the tenant's Organization Administrators: a grant from {@link #SUPER} or {@link #OPS} does, one from {@link #TENANT}
 * never does.
 *
 * <p>No {@code UNKNOWN} default, on purpose: a source a reader does not know must fail to bind rather than be read
 * as one that provisions, or as one that does not. A later source arrives under a later tag.</p>
 *
 * <p><b>Names only.</b> Jackson otherwise binds an enum from its ordinal, both {@code 0} and {@code "0"}, which
 * would read a malformed grant as {@link #SUPER}: an administrator. {@link #fromWire} accepts exactly the constant
 * names, case-sensitive, and refuses everything else.</p>
 */
public enum TenantAccessGrantSource {
	/** A global administrator's grant, written from the super panel. Provisions Organization Administrator membership. */
	SUPER,
	/** A tenant's own administrators vouching from inside the tenant. Catalog-only: never provisions membership. */
	TENANT,
	/** Written outside the application (SQL, a backfill). Treated like {@link #SUPER}. */
	OPS;

	/**
	 * The source a wire value names, by exact constant name. A number, a quoted number, another case or an unknown
	 * name is refused, so it fails to bind.
	 *
	 * @throws IllegalArgumentException for anything but a constant's name
	 */
	@JsonCreator(mode = JsonCreator.Mode.DELEGATING)
	public static TenantAccessGrantSource fromWire(String name) {
		for (TenantAccessGrantSource source : values()) {
			if (source.name().equals(name)) {
				return source;
			}
		}
		throw new IllegalArgumentException("not a grant source: " + name);
	}
}
