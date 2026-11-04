package dev.bluestep.global.dto.knowledgebase;

import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.regex.Pattern;

import org.jspecify.annotations.Nullable;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

/**
 * A global knowledge base's id (from 6.1.0): the primary key of its row in web-global. It is assigned
 * when the knowledge base is created and never changes.
 *
 * <p>On the wire it is the uuid's text, as a bare string, in the canonical lowercase form only.</p>
 *
 * @param value the uuid
 */
public record GlobalKnowledgeBaseId(@JsonValue UUID value) {

	private static final Pattern CANONICAL =
			Pattern.compile("[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}");

	/** Requires the uuid. */
	public GlobalKnowledgeBaseId {
		Objects.requireNonNull(value, "value");
	}

	/**
	 * The id {@code value} names.
	 *
	 * @param value the uuid
	 * @return the id
	 */
	public static GlobalKnowledgeBaseId of(UUID value) {
		return new GlobalKnowledgeBaseId(value);
	}

	/**
	 * The id {@code text} spells. Jackson binds a JSON string through this, so it takes the same strict
	 * form as {@link #parse}, and a lenient spelling fails binding.
	 *
	 * @param text the canonical uuid text
	 * @return the id
	 * @throws IllegalArgumentException when {@code text} is not the canonical form
	 */
	@JsonCreator(mode = JsonCreator.Mode.DELEGATING)
	public static GlobalKnowledgeBaseId of(String text) {
		return parse(text).orElseThrow(() -> new IllegalArgumentException(
				"Not a canonical lowercase uuid (8-4-4-4-12 hex): " + text));
	}

	/**
	 * The id {@code text} spells, or empty when {@code text} is not the canonical form: 36 characters,
	 * lowercase hex in the 8-4-4-4-12 shape, which is what {@link UUID#toString()} writes and what
	 * web-global emits. {@link UUID#fromString} is not used on its own because it is lenient: it reads
	 * {@code 1-1-1-1-1} as a different uuid. Uppercase hex, spaces and unhyphenated hex name no knowledge
	 * base either. A path segment that is not canonical names no knowledge base.
	 *
	 * @param text the candidate, or null
	 * @return the id, or empty
	 */
	public static Optional<GlobalKnowledgeBaseId> parse(@Nullable String text) {
		if (text == null || !CANONICAL.matcher(text).matches()) {
			return Optional.empty();
		}
		return Optional.of(new GlobalKnowledgeBaseId(UUID.fromString(text)));
	}

	/** The uuid's text, which is the id's wire form. */
	@Override
	public String toString() {
		return value.toString();
	}
}
