package dev.bluestep.global.dto.globaluser;

import java.util.Optional;

import dev.bluestep.global.dto.tenantaccess.ResellerKey;
import jakarta.validation.Valid;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * Declaring how wide one global user's tenant catalog is.
 *
 * <h2>The whole classification, not a flag on it</h2>
 *
 * <p>A global user's standing moves: a super becomes a reseller super becomes a global non-super and
 * back again. Modelled as separate switches — a restriction here, a reseller assignment there — a
 * transition is two writes that can half-land, leaving a user holding both at once. So this states
 * the resolved answer, and one call is one transition.</p>
 *
 * @param scope    which of the three this user gets
 * @param reseller the reseller they are restricted to. Required for {@link CatalogScopeKind#RESELLER}
 *                 and rejected for anything else — see {@link #isResellerConsistentWithScope()}.
 * @param reason   why. Free text, because the reasons are not a closed set, and required because a
 *                 classification nobody can explain is one nobody can review.
 * @param actor    who is deciding. A person where there is one: this is the whole of the
 *                 attribution, and a system name in every row tells a reviewer nothing they could
 *                 not infer from the route it arrived on.
 */
public record GlobalUserScopeRequest(
		@NotNull CatalogScopeKind scope,
		@Valid Optional<ResellerKey> reseller,
		@NotBlank @Size(max = 1000) String reason,
		@NotBlank @Size(max = 100) String actor
) {

	public GlobalUserScopeRequest {
		// Jackson binds an omitted field to null whatever this package's null-marking says.
		reseller = reseller == null ? Optional.empty() : reseller;
	}

	/**
	 * The reseller and the scope are one fact, so a payload asserting one without the other is
	 * rejected at the edge rather than at the database.
	 *
	 * <p>A {@code RESELLER} with no reseller would scope the user to nothing and show them an empty
	 * catalog — indistinguishable, to them, from having no access at all. A {@code FLEET} carrying one
	 * reads as a restriction that is not applied. Neither is a state worth storing.</p>
	 */
	@AssertTrue(message = "a reseller is required for a RESELLER scope and permitted for no other")
	public boolean isResellerConsistentWithScope() {
		return (scope == CatalogScopeKind.RESELLER) == reseller.isPresent();
	}
}
