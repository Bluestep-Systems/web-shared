package dev.bluestep.global.dto.knowledgebase;

import java.util.AbstractMap;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import org.jspecify.annotations.Nullable;

import com.fasterxml.jackson.annotation.JsonAnySetter;
import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonValue;

/**
 * Who may read a global knowledge base (from 6.11.0): {@link Public}, anyone, signed out included, may read its live
 * branch; {@link Private}, only the signed-in readers its access rules admit, as before 6.11.0; {@link Unknown}, a kind
 * this release does not know (a newer peer's), held as it was read.
 *
 * <p>On the wire the kind is the {@code "kind"} member: {@code {"kind":"public"}} or {@code {"kind":"private"}}. A
 * member carrying permissions will be added later, as another kind with members of its own.</p>
 *
 * <h2>Read by hand, never by the mapper's settings</h2>
 *
 * <p>This type binds through its own creator ({@link #of(Wire)}) and writes itself ({@link #wire()}), so no mapper
 * setting changes what it means: not {@code ACCEPT_CASE_INSENSITIVE_VALUES}, not polymorphic-typing defaults, not
 * {@code FAIL_ON_UNKNOWN_PROPERTIES}. It uses only web-shared's one published Jackson artifact, the annotations: the
 * reading is this file's code over the members as the parser met them.</p>
 * <ul>
 *   <li>{@link Public} only for exactly {@code {"kind":"public"}}, and {@link Private} only for exactly
 *       {@code {"kind":"private"}}: the string compared as it is, case and spaces included, and no other member.</li>
 *   <li>Any other object with one string {@code kind} is {@link Unknown}: never public, and written back as it was
 *       read, so a reader on this release that writes a record back cannot rewrite a newer peer's access.</li>
 *   <li>A second {@code kind} member, a {@code kind} that is not a string, an object with no {@code kind}, and anything
 *       that is not an object fail to bind: one payload never means two things to two readers.</li>
 * </ul>
 *
 * <p><b>Never written by a request.</b> {@link GlobalKnowledgeBaseCreateRequest} and
 * {@link GlobalKnowledgeBasePatchRequest} refuse an {@link Unknown} in their constructors, so a binding of one fails
 * and no caller can build one: a writer sets only a kind it knows. A record holding an absent {@code access} (an older
 * peer's, which never sends it) reads it as {@link Private}.</p>
 */
public sealed interface Access permits Access.Public, Access.Private, Access.Unknown {

	/** The discriminating member's name. */
	String KIND = "kind";

	/** {@link Public}'s one value. */
	Access PUBLIC = new Public();

	/** {@link Private}'s one value: the default. */
	Access PRIVATE = new Private();

	/** Whether anyone may read the live branch without signing in: only {@link Public}, never {@link Unknown}. */
	@JsonIgnore
	default boolean isPublic() {
		return this instanceof Public;
	}

	/** The wire form: {@code {"kind":...}}, or an {@link Unknown}'s members as read. */
	@JsonValue
	default Map<String, @Nullable Object> wire() {
		return switch (this) {
			case Public p -> Map.of(KIND, "public");
			case Private p -> Map.of(KIND, "private");
			case Unknown u -> u.raw();
		};
	}

	/**
	 * Binds {@code wire}'s members as the class comment says.
	 *
	 * @param wire the members as the parser met them
	 * @return the access
	 * @throws IllegalArgumentException a second {@code kind}, a {@code kind} that is not a string, or none
	 */
	@JsonCreator(mode = JsonCreator.Mode.DELEGATING)
	static Access of(Wire wire) {
		int kinds = 0;
		@Nullable Object kind = null;
		Map<String, @Nullable Object> raw = new LinkedHashMap<>();
		for (Map.Entry<String, @Nullable Object> member : wire.members) {
			if (KIND.equals(member.getKey())) {
				kinds++;
				kind = member.getValue();
			}
			raw.put(member.getKey(), member.getValue());
		}
		if (kinds != 1) {
			throw new IllegalArgumentException(kinds == 0 ? "access has no kind"
				: "access names its kind " + kinds + " times");
		}
		if (!(kind instanceof String named)) {
			throw new IllegalArgumentException("access's kind is not a string");
		}
		if (raw.size() == 1 && named.equals("public")) {
			return PUBLIC;
		}
		if (raw.size() == 1 && named.equals("private")) {
			return PRIVATE;
		}
		return new Unknown(named, raw);
	}

	/** Anyone may read the live branch without signing in. */
	record Public() implements Access {}

	/** Only the signed-in readers the knowledge base's access rules admit. */
	record Private() implements Access {}

	/**
	 * A kind this release does not know, or a known kind with members this release does not know: never public, and
	 * written back as read (strings, integers, booleans, nulls, arrays and objects as they were; a fraction as the
	 * JDK's {@code double} spells it). Refused by every create and patch request.
	 *
	 * <p>A final class rather than a record: its members may be JSON nulls, and web-shared's records never hold a
	 * {@code @Nullable} inside a type argument. Equal by value, as a record would be.</p>
	 */
	final class Unknown implements Access {

		private final String kind;
		private final Map<String, @Nullable Object> raw;

		/**
		 * @param kind its {@code kind}, as read
		 * @param raw  every member as read, {@code kind} included, in order
		 * @throws IllegalArgumentException {@code raw} does not carry {@code kind} as its kind
		 */
		public Unknown(String kind, Map<String, ? extends @Nullable Object> raw) {
			this.kind = Objects.requireNonNull(kind, KIND);
			this.raw = Collections.unmodifiableMap(new LinkedHashMap<>(raw));
			if (!kind.equals(this.raw.get(KIND))) {
				throw new IllegalArgumentException("raw must carry the kind " + kind);
			}
		}

		/** @return its {@code kind}, as read */
		public String kind() {
			return kind;
		}

		/** @return every member as read, {@code kind} included, in order; unmodifiable */
		public Map<String, @Nullable Object> raw() {
			return raw;
		}

		@Override
		public boolean equals(@Nullable Object other) {
			return other instanceof Unknown that && kind.equals(that.kind) && raw.equals(that.raw);
		}

		@Override
		public int hashCode() {
			return Objects.hash(kind, raw);
		}

		@Override
		public String toString() {
			return "Unknown[kind=" + kind + "]";
		}
	}

	/**
	 * Binding only: the members of an {@code access} object in the order the parser met them, every one kept (a
	 * repeated name included, which a map would fold). Not part of the wire.
	 */
	final class Wire {

		private final List<Map.Entry<String, @Nullable Object>> members = new ArrayList<>();

		/** Each member, as the parser meets it. */
		@JsonAnySetter
		public void member(String name, @Nullable Object value) {
			members.add(new AbstractMap.SimpleEntry<>(name, value));
		}
	}
}
