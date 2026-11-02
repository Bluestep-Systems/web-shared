package dev.bluestep.global.dto.knowledgebase;

import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

/**
 * A global knowledge base's id (from 6.1.0): the primary key of its row in web-global. It is assigned
 * when the knowledge base is created and never changes.
 *
 * <p>On the wire it is the uuid's text, as a bare string.</p>
 *
 * @param value the uuid
 */
public record GlobalKnowledgeBaseId(@JsonValue UUID value) {

	/** Requires the uuid. */
	public GlobalKnowledgeBaseId {
		Objects.requireNonNull(value, "value");
	}

	/**
	 * The id {@code value} names. Jackson binds a JSON string through this.
	 *
	 * @param value the uuid
	 * @return the id
	 */
	@JsonCreator(mode = JsonCreator.Mode.DELEGATING)
	public static GlobalKnowledgeBaseId of(UUID value) {
		return new GlobalKnowledgeBaseId(value);
	}

	/**
	 * The id {@code text} spells, or empty when {@code text} is not a uuid. A path segment that is not
	 * one names no knowledge base.
	 *
	 * @param text the candidate
	 * @return the id, or empty
	 */
	public static Optional<GlobalKnowledgeBaseId> parse(String text) {
		try {
			return Optional.of(new GlobalKnowledgeBaseId(UUID.fromString(text)));
		} catch (IllegalArgumentException e) {
			return Optional.empty();
		}
	}

	/** The uuid's text, which is the id's wire form. */
	@Override
	public String toString() {
		return value.toString();
	}
}
