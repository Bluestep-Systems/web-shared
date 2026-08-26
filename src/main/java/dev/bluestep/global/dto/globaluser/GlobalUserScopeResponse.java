package dev.bluestep.global.dto.globaluser;

import java.util.Optional;

import dev.bluestep.global.dto.tenantaccess.GlobalUserKey;
import dev.bluestep.global.dto.tenantaccess.ResellerKey;

/**
 * How wide one global user's tenant catalog is, and what put them there.
 *
 * @param user     the user asked about
 * @param scope    the answer
 * @param reseller present exactly when {@code scope} is {@code RESELLER}
 * @param decision the stored classification, absent when nobody has classified this user — in which
 *                 case {@code scope} is {@code FLEET} by default rather than by decision, and saying
 *                 so is the point of separating the two
 */
public record GlobalUserScopeResponse(
		GlobalUserKey user,
		CatalogScopeKind scope,
		Optional<ResellerKey> reseller,
		Optional<GlobalUserScopeDecision> decision
) {
}
