package dev.bluestep.global.dto.consoleglobaluser;

import java.util.LinkedHashSet;
import java.util.List;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonIgnore;

import dev.bluestep.global.dto.endorsement.PlatformEndorsement;

/**
 * A global user's platform endorsements as web-global read them out of the account's {@code attribs} cipher (from
 * 6.16.0): the detail's {@code endorsements}.
 *
 * <p><b>{@link State#UNAVAILABLE} is not "none".</b> It says web-global could not read them: it holds no key (the
 * read is off, or the seed is not configured), or the stored value does not decrypt to text under the key it holds (a
 * wrong seed, a value that never went through the cipher). Both lists are then empty, and a reader must show the
 * endorsements as unknown, never as an account holding none: a wrong seed read as "none" looks like a fleet-wide
 * revocation. {@link State#AVAILABLE} with both lists empty is an account that holds none.</p>
 *
 * <p><b>Ids are strings on the wire</b>, so a reader whose catalog is older than the writer's still binds the answer.
 * {@code held} is every stored token that is a {@link PlatformEndorsement} id in the writer's catalog, in catalog
 * order; {@code unrecognized} every other token, in stored order, each once. A reader that finds a held id its own
 * catalog does not know must show the endorsements as unavailable rather than drop it, and may move an unrecognized
 * token its catalog does know into held.</p>
 *
 * @param state        whether the endorsements could be read
 * @param held         the catalog ids held, in catalog order, each once; empty unless available
 * @param unrecognized every other stored token, in stored order, each once; none of them grants anything. Empty
 *                     unless available
 */
public record ConsoleEndorsements(State state, List<String> held, List<String> unrecognized) {

	/** Whether the endorsements could be read. */
	public enum State {
		/** Read: the lists are the account's endorsements. */
		AVAILABLE,
		/** Not read: the lists are empty and say nothing about the account. */
		UNAVAILABLE,
		/** A state this reader's contract does not know. Never sent by web-global; read as {@link #UNAVAILABLE}. */
		UNKNOWN;

		/** The state a wire value names, by exact constant name; anything else is {@link #UNKNOWN}. */
		@JsonCreator(mode = JsonCreator.Mode.DELEGATING)
		public static State fromWire(String name) {
			for (State state : values()) {
				if (state.name().equals(name)) {
					return state;
				}
			}
			return UNKNOWN;
		}
	}

	/**
	 * Holds the lists to their rules: no empty or repeated entry, none with ASCII whitespace in it (the monolith splits
	 * on {@code \s+}, so no stored token can hold one), nothing in them unless available. A token of other whitespace
	 * alone (an em space, NBSP) is a token the monolith keeps, and is kept. An omitted state
	 * binds to null whatever the null-marking says; read as {@link State#UNKNOWN}.
	 */
	public ConsoleEndorsements {
		state = state == null ? State.UNKNOWN : state;
		held = List.copyOf(held == null ? List.of() : held);
		unrecognized = List.copyOf(unrecognized == null ? List.of() : unrecognized);
		if (state != State.AVAILABLE && !(held.isEmpty() && unrecognized.isEmpty())) {
			throw new IllegalArgumentException("Endorsements that could not be read list nothing");
		}
		for (List<String> tokens : List.of(held, unrecognized)) {
			if (new LinkedHashSet<>(tokens).size() != tokens.size()) {
				throw new IllegalArgumentException("An endorsement is listed once");
			}
			if (tokens.stream().anyMatch(ConsoleEndorsements::notAToken)) {
				throw new IllegalArgumentException("An endorsement token is never empty and holds no ASCII whitespace");
			}
		}
	}

	/** Empty, or holding one of the six characters of {@code \s}: what a split on {@code \s+} never yields. */
	private static boolean notAToken(String token) {
		return token.isEmpty() || token.chars().anyMatch(c -> " \t\n\u000B\f\r".indexOf(c) >= 0);
	}

	/** Read: these catalog endorsements, in catalog order, and these other tokens, in stored order. */
	public static ConsoleEndorsements available(List<PlatformEndorsement> held, List<String> unrecognized) {
		return new ConsoleEndorsements(State.AVAILABLE,
				held.stream().sorted().map(PlatformEndorsement::id).toList(), unrecognized);
	}

	/** Not read. */
	public static ConsoleEndorsements unavailable() {
		return new ConsoleEndorsements(State.UNAVAILABLE, List.of(), List.of());
	}

	/** Whether these are the account's endorsements, read; false for {@link State#UNKNOWN} too. */
	@JsonIgnore
	public boolean isAvailable() {
		return state == State.AVAILABLE;
	}
}
