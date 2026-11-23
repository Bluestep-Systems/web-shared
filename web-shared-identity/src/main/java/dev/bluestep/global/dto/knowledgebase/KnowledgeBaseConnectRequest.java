package dev.bluestep.global.dto.knowledgebase;

import java.util.Optional;

import org.jspecify.annotations.Nullable;

import dev.bluestep.global.dto.tenant.TenantActor;

/**
 * Connecting a tenant to a global knowledge base (from 6.1.0):
 * {@code PUT /global-knowledge-bases/{id}/tenants/{classid}/{seqnum}}. The body is optional, and an
 * absent body records no actor.
 *
 * <p>Idempotent. It answers {@code 201} with the new {@link KnowledgeBaseTenantConnection}, or
 * {@code 200} with the existing one when the tenant is already connected, which keeps its original
 * time and creator. An unknown knowledge base is a {@code 404}. A tenant that does not exist is a
 * {@code 409} {@link GlobalKnowledgeBaseWriteRefusal} with {@code TENANT_ABSENT}.</p>
 *
 * @param actor who is making the connection, or absent for no actor
 */
public record KnowledgeBaseConnectRequest(@Nullable TenantActor actor) {

	/** {@link #actor}, as an {@code Optional}. */
	public Optional<TenantActor> optActor() {
		return Optional.ofNullable(actor);
	}
}
