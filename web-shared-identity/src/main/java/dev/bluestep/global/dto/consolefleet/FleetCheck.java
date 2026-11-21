package dev.bluestep.global.dto.consolefleet;

import com.fasterxml.jackson.annotation.JsonCreator;

/**
 * Which of {@code /readyz}'s checks failed, as far as a namespace's answers tell them apart (6.21.0). web's
 * {@code /readyz} fails with a bare 500 whichever check failed, so web-global asks {@code /healthz} (the in-JVM checks
 * alone) as well: it cannot tell the main database from the global one.
 */
public enum FleetCheck {
	/** {@code /healthz} failed too: an in-JVM check (scheduler, memory, long-running processes). */
	APP,
	/** {@code /healthz} answered 200 and {@code /readyz} did not: the main or the global database could not be reached. */
	DATABASE,
	/** A check this reader's contract does not know. Never sent by web-global. */
	UNKNOWN;

	/**
	 * The check a wire value names, by exact constant name; anything else is {@link #UNKNOWN}.
	 *
	 * @param name the wire value
	 * @return the check
	 */
	@JsonCreator(mode = JsonCreator.Mode.DELEGATING)
	public static FleetCheck fromWire(String name) {
		for (FleetCheck check : values()) {
			if (check.name().equals(name)) {
				return check;
			}
		}
		return UNKNOWN;
	}
}
