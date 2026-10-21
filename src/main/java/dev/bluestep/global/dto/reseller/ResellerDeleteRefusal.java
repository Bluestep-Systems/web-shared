package dev.bluestep.global.dto.reseller;

import java.util.Set;

/**
 * Why a reseller could not be deleted: the body of the {@code 409} a refused delete answers with.
 *
 * <h2>Why a delete is refused rather than cascaded</h2>
 *
 * <p>Nothing in the global schema has a foreign key to {@code global.reseller}. Tenants, global users
 * and catalog classifications all point at one by a bare key pair, and the monolith dereferences that
 * pair on every page it brands. Deleting a reseller something still names would leave every one of
 * those readers holding a key to nothing, and there is no right reseller to move them to on the
 * caller's behalf. So the delete refuses, says what is in the way, and leaves moving it to an
 * operator.</p>
 *
 * <p>The reasons are kinds, not rows. A refusal reaches a person, and the identity of a tenant or a
 * user is not something to put in front of one; how many there are, and which, is a query the
 * operator can run.</p>
 *
 * @param reasons every reason that applies, never empty
 */
public record ResellerDeleteRefusal(Set<Reason> reasons) {

	/** Copies, so a refusal cannot change after it is built. */
	public ResellerDeleteRefusal {
		reasons = Set.copyOf(reasons);
	}

	/** One thing standing in the way of a delete. */
	public enum Reason {
		/**
		 * The reseller every tenant falls back to. {@code global.association}'s reseller columns default
		 * to it, so it is in use by definition, including by tenants not yet created.
		 */
		DEFAULT_RESELLER,
		/** At least one tenant belongs to it. */
		HAS_TENANTS,
		/** At least one global user is restricted to it. */
		HAS_GLOBAL_USERS,
		/** At least one global user's catalog classification is scoped to it. */
		HAS_CATALOG_SCOPES
	}
}
