package dev.bluestep.global.dto.tenantaccess;

import java.util.List;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * One tenant telling web-global which global users can currently reach it.
 *
 * <h2>Why the tenant has to be the one to say</h2>
 *
 * <p>Whether a global user may act inside a tenant is decided by {@code <tenant_schema>.securityuser},
 * which lives in that tenant's own database in its own namespace. web-global holds the {@code global}
 * database and nothing else, so it cannot read that table, cannot join against it, and must be told.
 * This payload is the telling.</p>
 *
 * <h2>It is a snapshot, not a delta</h2>
 *
 * <p>{@code users} is the <em>complete</em> set as of this report, and web-global replaces everything
 * it held for {@code tenantId} with it. A revocation therefore propagates by absence, which is the
 * property that matters: a delta protocol loses a revocation permanently the one time a message is
 * dropped, whereas a snapshot protocol repairs itself on the next push. An empty list is a legitimate
 * report meaning "nobody" — it is not the same as never reporting, and web-global distinguishes the
 * two.</p>
 *
 * <h2>What each side asserts</h2>
 *
 * <p>The tenant asserts the half only it can see: the user materializes there ({@code securityuser})
 * <em>and</em> holds the AI-tools endorsement in that tenant. web-global keeps the half it owns — the
 * org-level AI-tools flag on {@code global.association} — and intersects the two when it answers the
 * catalog. Neither side restates the other's half, so neither can silently overrule it.</p>
 *
 * @param tenantId the reporting tenant's schema, i.e. {@code global.association.associationid}
 * @param users    every global user who passes that tenant's gate right now; empty means nobody
 */
public record TenantAccessReportRequest(
		@NotBlank @Size(max = 50) String tenantId,
		// Bounded because this arrives over the wire and is written to a table. Global users are
		// BlueStep staff rather than customer accounts, so a real tenant reports tens; a report of
		// ten thousand is a bug or an attack, and either way is better refused than persisted.
		@Size(max = 10_000) List<@Valid GlobalUserKey> users
) {

	public TenantAccessReportRequest {
		// The list is the whole meaning of the message, so it is defended rather than trusted:
		// callers keep no handle on it after construction, and a caller that mutates its own copy
		// cannot change what was reported.
		users = List.copyOf(users);
	}
}
