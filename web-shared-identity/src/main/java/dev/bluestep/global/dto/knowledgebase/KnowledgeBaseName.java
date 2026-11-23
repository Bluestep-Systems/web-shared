package dev.bluestep.global.dto.knowledgebase;

import java.util.Objects;
import java.util.Optional;
import java.util.regex.Pattern;

import org.jspecify.annotations.Nullable;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

/**
 * A knowledge base's name (from 6.1.0). It is unique among the global knowledge bases, the repo's
 * name is derived from it, and it never changes, because changing it would move the repo and break
 * every clone of it.
 *
 * <p>This is the monolith's tenant rule ({@code myassn.knowledgebase.KnowledgeBaseName}): lower-case
 * letters, digits and {@code -}, starting and ending with a letter or digit, at most
 * {@value #MAX_LENGTH} characters. With no {@code .} in it, a name cannot end in {@code .git}. With no
 * {@code _} in it, a name cannot look like a per-script repo ({@code <digits>___<digits>}).</p>
 *
 * <p>Built only to that rule, from code or from JSON: an invalid name fails JSON binding, so a request
 * carrying one is a {@code 400}. On the wire it is the bare string.</p>
 *
 * @param value the name
 */
public record KnowledgeBaseName(@JsonValue String value) {

	/**
	 * The longest name. A forge repo name holds 100 characters, and {@link #REPO_PREFIX} takes three of
	 * them.
	 */
	public static final int MAX_LENGTH = 64;

	/** The rule, in words, for a refusal to quote. */
	public static final String RULE = "lowercase letters, digits and '-'; must start and end with a letter or digit;"
			+ " at most " + MAX_LENGTH + " characters";

	/** The rule as a regular expression over the whole value; web-global's table checks the same one. */
	public static final String PATTERN = "^[a-z0-9](?:[a-z0-9-]{0," + (MAX_LENGTH - 2) + "}[a-z0-9])?$";

	/**
	 * What every knowledge base repo's name starts with. It tells those repos apart from anything else
	 * the same org holds.
	 */
	public static final String REPO_PREFIX = "kb-";

	/** The forge org that holds every global knowledge base's repo. */
	public static final String GLOBAL_REPO_OWNER = "knowledge-base";

	private static final Pattern NAME = Pattern.compile(PATTERN);

	/** Holds the name to the rule. */
	public KnowledgeBaseName {
		Objects.requireNonNull(value, "value");
		if (!isValid(value)) {
			throw new IllegalArgumentException("Not a knowledge base name (" + RULE + "): " + value);
		}
	}

	/**
	 * The name {@code value} spells. Jackson binds a JSON string through this.
	 *
	 * @param value the name
	 * @return the name
	 * @throws IllegalArgumentException when {@code value} does not follow the rule
	 */
	@JsonCreator(mode = JsonCreator.Mode.DELEGATING)
	public static KnowledgeBaseName of(String value) {
		return new KnowledgeBaseName(value);
	}

	/**
	 * Whether {@code text} is a valid name.
	 *
	 * @param text the candidate, or null
	 * @return whether it follows the rule
	 */
	public static boolean isValid(@Nullable String text) {
		return text != null && NAME.matcher(text).matches();
	}

	/**
	 * The name {@code text} spells, or empty when it does not follow the rule.
	 *
	 * @param text the candidate, or null
	 * @return the name, or empty
	 */
	public static Optional<KnowledgeBaseName> parse(@Nullable String text) {
		return isValid(text) ? Optional.of(new KnowledgeBaseName(Objects.requireNonNull(text))) : Optional.empty();
	}

	/** The repo's name, {@code kb-<name>}. */
	public String repoName() {
		return REPO_PREFIX + value;
	}

	/**
	 * The repo of the global knowledge base with this name, {@code knowledge-base/kb-<name>}, as
	 * {@code <owner>/<repo>}. It is derived from the name, never stored, and nothing here makes it.
	 */
	public String globalRepoPath() {
		return GLOBAL_REPO_OWNER + "/" + repoName();
	}

	/** The name itself, as a log line or a URL wants it. */
	@Override
	public String toString() {
		return value;
	}
}
