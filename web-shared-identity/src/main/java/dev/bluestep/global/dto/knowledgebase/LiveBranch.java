package dev.bluestep.global.dto.knowledgebase;

import java.util.Objects;
import java.util.Optional;
import java.util.regex.Pattern;

import org.jspecify.annotations.Nullable;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

/**
 * The branch a knowledge base is served from (from 6.1.0). "Live" is whichever branch the owner names.
 * There is no publish gate, so a push to this branch is what changes what readers see.
 *
 * <p>This is the monolith's tenant rule ({@code myassn.knowledgebase.LiveBranch}), a subset of
 * {@code git check-ref-format --branch}, so a name that git or the forge would refuse is refused here
 * first:</p>
 * <ul>
 *   <li>only letters, digits, {@code .}, {@code _}, {@code -} and {@code /}, at most
 *       {@value #MAX_LENGTH} characters, starting with a letter or digit;</li>
 *   <li>split on {@code /}, no part is empty, starts with {@code .} or ends in {@code .lock};</li>
 *   <li>no {@code ..} anywhere, and the name does not end in {@code .};</li>
 *   <li>not {@code HEAD}.</li>
 * </ul>
 *
 * <p>Built only to that rule, from code or from JSON: an invalid branch fails JSON binding, so a
 * request carrying one is a {@code 400}. On the wire it is the bare string. Whether the branch exists
 * in the repo is not part of the rule. Only a reader of the repo can say, and web-global is not
 * one.</p>
 *
 * @param name the branch name, without {@code refs/heads/}
 */
public record LiveBranch(@JsonValue String name) {

	/** The longest branch name. */
	public static final int MAX_LENGTH = 200;

	/** The rule, in words, for a refusal to quote. */
	public static final String RULE = "letters, digits, '.', '_', '-' and '/'; must start with a letter or digit;"
			+ " at most " + MAX_LENGTH + " characters; no empty '/'-separated part, and no part starting with '.'"
			+ " or ending in '.lock'; no '..'; must not end in '.'; must not be HEAD";

	/**
	 * The characters a branch may hold and its length, as a regular expression over the whole value.
	 * web-global's table checks this part of the rule. The rest takes {@link #isValid}.
	 */
	public static final String CHARACTERS_PATTERN = "^[A-Za-z0-9][A-Za-z0-9._/-]{0," + (MAX_LENGTH - 1) + "}$";

	/** Declared before {@link #DEFAULT}: static initializers run in order, and DEFAULT's constructor reads it. */
	private static final Pattern CHARACTERS = Pattern.compile(CHARACTERS_PATTERN);

	/** The branch a knowledge base is created on when the caller names none. */
	public static final LiveBranch DEFAULT = new LiveBranch("main");

	/** Holds the name to the rule. */
	public LiveBranch {
		Objects.requireNonNull(name, "name");
		if (!isValid(name)) {
			throw new IllegalArgumentException("Not a valid branch name (" + RULE + "): " + name);
		}
	}

	/**
	 * The branch {@code name} names. Jackson binds a JSON string through this.
	 *
	 * @param name the branch name
	 * @return the branch
	 * @throws IllegalArgumentException when {@code name} does not follow the rule
	 */
	@JsonCreator(mode = JsonCreator.Mode.DELEGATING)
	public static LiveBranch of(String name) {
		return new LiveBranch(name);
	}

	/**
	 * The branch {@code text} names, or empty when it does not follow the rule.
	 *
	 * @param text the candidate, or null
	 * @return the branch, or empty
	 */
	public static Optional<LiveBranch> parse(@Nullable String text) {
		return isValid(text) ? Optional.of(new LiveBranch(Objects.requireNonNull(text))) : Optional.empty();
	}

	/**
	 * Whether {@code text} is a valid branch name.
	 *
	 * @param text the candidate, or null
	 * @return whether it follows the rule
	 */
	public static boolean isValid(@Nullable String text) {
		if (text == null || !CHARACTERS.matcher(text).matches() || text.contains("..") || text.endsWith(".")
				|| text.equals("HEAD")) {
			return false;
		}
		for (String component : text.split("/", -1)) {
			if (component.isEmpty() || component.startsWith(".") || component.endsWith(".lock")) {
				return false;
			}
		}
		return true;
	}

	/** The branch name, as a log line or a forge call wants it. */
	@Override
	public String toString() {
		return name;
	}
}
