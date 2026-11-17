package dev.bluestep.global.dto.knowledgebase;

import java.util.Optional;

import org.jspecify.annotations.Nullable;

import dev.bluestep.global.dto.tenant.TenantActor;

/**
 * Who is deleting a global knowledge base or disconnecting a tenant from one (from 6.8.0): the optional
 * body of {@code DELETE /global-knowledge-bases/{id}} and of
 * {@code DELETE /global-knowledge-bases/{id}/tenants/{classid}/{seqnum}}.
 *
 * <p>The body is optional, and an absent body records no actor. The gateway's key must name a global
 * user, as it must for every other write (a {@code 400} otherwise). There is no row left to stamp after
 * either delete, so web-global logs the actor rather than storing it.</p>
 *
 * <p>The body only widens what the two deletes accept, so web-global accepts it on every contract tag,
 * not only from 6.8.0.</p>
 *
 * @param actor who is making the delete, or absent for no actor
 */
public record KnowledgeBaseActorRequest(@Nullable TenantActor actor) {

	/** {@link #actor}, as an {@code Optional}. */
	public Optional<TenantActor> optActor() {
		return Optional.ofNullable(actor);
	}
}
