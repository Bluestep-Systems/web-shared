package dev.bluestep.global.dto.knowledgebase;

import java.time.Instant;
import java.util.Objects;
import java.util.Optional;

import org.jspecify.annotations.Nullable;

import dev.bluestep.global.dto.tenant.TenantActor;

/**
 * One global knowledge base (from 6.1.0): a row of web-global's {@code global_knowledge_base}.
 *
 * <p>It names the content and does not hold it. The content is the git repo
 * {@link KnowledgeBaseName#globalRepoPath()} derives from {@link #name}, served from
 * {@link #liveBranch}. The row's version travels in the {@code ETag}, and a patch or a delete is
 * pinned to it.</p>
 *
 * @param id         the id
 * @param name       unique among the global knowledge bases, and fixed when the knowledge base is created
 * @param title      what a reader sees as the knowledge base's name: trimmed, never blank, at most
 *                   {@value #MAX_TITLE_LENGTH} characters, with no control character
 * @param liveBranch the branch it is served from
 * @param created    when it was created
 * @param modified   when it was last patched, or when it was created if it never has been
 * @param creator    who created it, absent for no actor
 * @param modifier   who last patched it, absent for no actor or when nobody has
 */
public record GlobalKnowledgeBase(
		GlobalKnowledgeBaseId id,
		KnowledgeBaseName name,
		String title,
		LiveBranch liveBranch,
		Instant created,
		Instant modified,
		@Nullable TenantActor creator,
		@Nullable TenantActor modifier) {

	/**
	 * Holds the parts to their rules: the title must be
	 * one {@link #title} would return unchanged, so a title that is padded, blank, too long or holds a
	 * control character fails here and, when read from JSON, fails binding.
	 */
	public GlobalKnowledgeBase {
		Objects.requireNonNull(title, "title");
		if (!title(title).equals(Optional.of(title))) {
			throw new IllegalArgumentException("Not a valid knowledge base title (trimmed, not blank, at most "
					+ MAX_TITLE_LENGTH + " characters, no control character): " + title);
		}
	}

	/** The longest title, in UTF-16 units, which is what the monolith's tenant rule counts. */
	public static final int MAX_TITLE_LENGTH = 200;

	/**
	 * {@code text} as a title, by the monolith's tenant rule: trimmed, and empty when nothing is left,
	 * when the result is longer than {@value #MAX_TITLE_LENGTH}, or when it holds a control character.
	 * Request components carry it as {@link KnowledgeBaseTitle}, which judges the trimmed value with this
	 * method, and the writer that stores the result calls it too.
	 *
	 * @param text the candidate, or null
	 * @return the title, or empty when {@code text} cannot be one
	 */
	public static Optional<String> title(@Nullable String text) {
		if (text == null) {
			return Optional.empty();
		}
		String trimmed = text.strip();
		if (trimmed.isEmpty() || trimmed.length() > MAX_TITLE_LENGTH
				|| trimmed.chars().anyMatch(Character::isISOControl)) {
			return Optional.empty();
		}
		return Optional.of(trimmed);
	}

	/** {@link #creator}, as an {@code Optional}. */
	public Optional<TenantActor> optCreator() {
		return Optional.ofNullable(creator);
	}

	/** {@link #modifier}, as an {@code Optional}. */
	public Optional<TenantActor> optModifier() {
		return Optional.ofNullable(modifier);
	}
}
