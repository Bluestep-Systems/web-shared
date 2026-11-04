package dev.bluestep.global.dto.knowledgebase;

import java.util.Optional;

import org.jspecify.annotations.Nullable;

import dev.bluestep.global.dto.tenant.TenantActor;


/**
 * Changing a global knowledge base's title or live branch (from 6.1.0):
 * {@code PATCH /global-knowledge-bases/{id}} with a required {@code If-Match}. It answers with the
 * {@link GlobalKnowledgeBase} and its new version as the {@code ETag}. A stale {@code If-Match} is a
 * {@code 412}.
 *
 * <p>A patch, not a replace: an absent component leaves its column alone. Every patch records the
 * edit (its time and {@link #actor}), even one that names nothing to change.</p>
 *
 * <p><b>No rename.</b> The repo's name and every clone URL derive from the name, so it is fixed when the
 * knowledge base is created. {@link #name} exists so that a caller sending the whole record does not
 * have the name dropped without a word. It must be absent or the current name. Any other name is a
 * {@code 400}.</p>
 *
 * <p>web-global cannot read the repo, so it does not check that the branch exists. The caller does,
 * before it patches. The monolith's tenant surface refuses a branch the repo lacks, and its global
 * gateway should too.</p>
 *
 * @param name       absent, or the current name
 * @param title      the new title, or absent to keep it. web-global trims it, and one that is blank once
 *                   trimmed is a {@code 400}.
 * @param liveBranch the new live branch, or absent to keep it
 * @param actor      who is making the edit, or absent for no actor
 */
public record GlobalKnowledgeBasePatchRequest(
		@Nullable KnowledgeBaseName name,
		@KnowledgeBaseTitle @Nullable String title,
		@Nullable LiveBranch liveBranch,
		@Nullable TenantActor actor) {

	/** {@link #name}, as an {@code Optional}. */
	public Optional<KnowledgeBaseName> optName() {
		return Optional.ofNullable(name);
	}

	/** {@link #title}, as an {@code Optional}. */
	public Optional<String> optTitle() {
		return Optional.ofNullable(title);
	}

	/** {@link #liveBranch}, as an {@code Optional}. */
	public Optional<LiveBranch> optLiveBranch() {
		return Optional.ofNullable(liveBranch);
	}

	/** {@link #actor}, as an {@code Optional}. */
	public Optional<TenantActor> optActor() {
		return Optional.ofNullable(actor);
	}
}
