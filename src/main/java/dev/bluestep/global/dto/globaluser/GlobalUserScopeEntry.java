package dev.bluestep.global.dto.globaluser;

import java.util.Optional;

import dev.bluestep.global.dto.tenantaccess.GlobalUserKey;
import dev.bluestep.global.dto.tenantaccess.ResellerKey;
import jakarta.validation.Valid;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * One classified user inside a snapshot.
 *
 * <p>Carries no actor of its own — the snapshot names one for the whole push, because a single call
 * asserting a whole set is a single decision by a single person.</p>
 */
public record GlobalUserScopeEntry(
		@NotNull @Valid GlobalUserKey user,
		@NotNull CatalogScopeKind scope,
		@Valid Optional<ResellerKey> reseller,
		@NotBlank @Size(max = 1000) String reason
) {

	public GlobalUserScopeEntry {
		reseller = reseller == null ? Optional.empty() : reseller;
	}

	/** Same invariant the per-user request states, for the same reasons. */
	@AssertTrue(message = "a reseller is required for a RESELLER scope and permitted for no other")
	public boolean isResellerConsistentWithScope() {
		return (scope == CatalogScopeKind.RESELLER) == reseller.isPresent();
	}
}
