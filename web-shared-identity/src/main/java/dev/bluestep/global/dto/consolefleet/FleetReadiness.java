package dev.bluestep.global.dto.consolefleet;

import com.fasterxml.jackson.annotation.JsonCreator;

/** Whether a namespace's web answered its readiness check ({@code GET /readyz}) when web-global asked (6.21.0). */
public enum FleetReadiness {
	/** {@code /readyz} answered 200. */
	READY,
	/** {@code /readyz} answered, but not 200: one of its checks failed ({@link FleetCheck} says which, when known). */
	NOT_READY,
	/**
	 * {@code /readyz} did not answer within its time limit, the connection failed, or web-global would not ask (a
	 * stored name that is not a DNS label).
	 */
	UNREACHABLE,
	/** A readiness this reader's contract does not know. Never sent by web-global. */
	UNKNOWN;

	/**
	 * The readiness a wire value names, by exact constant name; anything else is {@link #UNKNOWN}.
	 *
	 * @param name the wire value
	 * @return the readiness
	 */
	@JsonCreator(mode = JsonCreator.Mode.DELEGATING)
	public static FleetReadiness fromWire(String name) {
		for (FleetReadiness readiness : values()) {
			if (readiness.name().equals(name)) {
				return readiness;
			}
		}
		return UNKNOWN;
	}
}
