package dev.bluestep.global.dto.knowledgebase;

import java.time.Instant;
import java.util.Optional;

import org.jspecify.annotations.Nullable;

import dev.bluestep.global.dto.tenant.TenantActor;
import dev.bluestep.global.dto.tenant.TenantKey;

/**
 * One tenant connected to one global knowledge base (from 6.1.0): a row of web-global's
 * {@code knowledge_base_tenant_connection}. A tenant's users can read a global knowledge base only
 * while this row exists. Access lives entirely on the host side. The knowledge base itself knows
 * nothing of tenants.
 *
 * <p>A connection is made or removed and never edited, so it has no version and no modifier. It
 * disappears with its knowledge base, and with its tenant.</p>
 *
 * @param knowledgeBase the knowledge base
 * @param tenant        the tenant whose users may read it
 * @param created       when the connection was made
 * @param creator       who made it, absent for no actor
 */
public record KnowledgeBaseTenantConnection(
		GlobalKnowledgeBaseId knowledgeBase,
		TenantKey tenant,
		Instant created,
		@Nullable TenantActor creator) {

	/** {@link #creator}, as an {@code Optional}. */
	public Optional<TenantActor> optCreator() {
		return Optional.ofNullable(creator);
	}
}
