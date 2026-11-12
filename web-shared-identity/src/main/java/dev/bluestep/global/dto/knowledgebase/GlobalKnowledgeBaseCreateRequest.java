package dev.bluestep.global.dto.knowledgebase;

import java.util.Optional;

import org.jspecify.annotations.Nullable;

import dev.bluestep.global.dto.tenant.TenantActor;

import jakarta.validation.constraints.NotNull;

/**
 * Registering a global knowledge base (from 6.1.0): {@code POST /global-knowledge-bases}. It answers
 * {@code 201} with the {@link GlobalKnowledgeBase}, its version as the {@code ETag} and its
 * {@code Location}.
 *
 * <p>This registers a repo and does not create one. The repo
 * ({@link KnowledgeBaseName#globalRepoPath()}) is made by ops, and later by a gateway that does not
 * exist yet. A knowledge base can be registered before its repo exists, and readers then find nothing
 * to serve.</p>
 *
 * <p>The service picks the id. A name another global knowledge base holds is a {@code 409}
 * {@link GlobalKnowledgeBaseWriteRefusal} with {@code NAME_TAKEN}.</p>
 *
 * @param name       the name, which is fixed from now on
 * @param title      the title, or absent to use the name. web-global trims it, and one that is blank
 *                   once trimmed is a {@code 400}.
 * @param liveBranch the branch to serve, or absent for {@link LiveBranch#DEFAULT}
 * @param creator    who is registering it, or absent for no actor
 */
public record GlobalKnowledgeBaseCreateRequest(
		@NotNull KnowledgeBaseName name,
		@KnowledgeBaseTitle @Nullable String title,
		@Nullable LiveBranch liveBranch,
		@Nullable TenantActor creator) {

	/** {@link #title}, as an {@code Optional}. */
	public Optional<String> optTitle() {
		return Optional.ofNullable(title);
	}

	/** {@link #liveBranch}, as an {@code Optional}. */
	public Optional<LiveBranch> optLiveBranch() {
		return Optional.ofNullable(liveBranch);
	}

	/** {@link #creator}, as an {@code Optional}. */
	public Optional<TenantActor> optCreator() {
		return Optional.ofNullable(creator);
	}
}
