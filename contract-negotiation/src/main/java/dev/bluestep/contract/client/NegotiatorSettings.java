package dev.bluestep.contract.client;

import java.time.Duration;
import java.util.Objects;

/**
 * How long a {@link CachingContractNegotiator} trusts what it learned about a peer. A configuration holder.
 *
 * @param answeredFor       an answered handshake ({@value #ANSWERED_MINUTES} min)
 * @param failedFor         a failed handshake, before asking again ({@value #FAILED_SECONDS} s)
 * @param predatesFor       a peer that predates negotiation, before asking again ({@value #FAILED_SECONDS} s)
 * @param excludedFor       a tag a peer refused as a mismatch ({@value #EXCLUDED_MINUTES} min, ruling N2)
 * @param pathStyleFallback whether a peer that predates negotiation is sent the path style at the call's floor (N3);
 *                          false for the harness, where a web without the protocol has no harness at all
 */
public record NegotiatorSettings(Duration answeredFor, Duration failedFor, Duration predatesFor, Duration excludedFor,
		boolean pathStyleFallback) {

	/** The default for an answered handshake, in minutes. */
	public static final int ANSWERED_MINUTES = 5;
	/** The default for a failed handshake or a peer that predates negotiation, in seconds. */
	public static final int FAILED_SECONDS = 30;
	/** The default exclusion of a refused tag, in minutes. */
	public static final int EXCLUDED_MINUTES = 5;

	/** Holds every duration to being positive. */
	public NegotiatorSettings {
		requirePositive(answeredFor, "answeredFor");
		requirePositive(failedFor, "failedFor");
		requirePositive(predatesFor, "predatesFor");
		requirePositive(excludedFor, "excludedFor");
	}

	private static void requirePositive(Duration duration, String name) {
		Objects.requireNonNull(duration, name);
		if (duration.isNegative() || duration.isZero()) {
			throw new IllegalArgumentException(name + " must be positive: " + duration);
		}
	}

	/**
	 * The defaults, with the path-style fallback: a client of web-global, which still serves the old paths.
	 *
	 * @return the settings
	 */
	public static NegotiatorSettings withPathStyleFallback() {
		return defaults(true);
	}

	/**
	 * The defaults, without the fallback: a client of web's harness, which only ever negotiated.
	 *
	 * @return the settings
	 */
	public static NegotiatorSettings withoutPathStyleFallback() {
		return defaults(false);
	}

	private static NegotiatorSettings defaults(boolean fallback) {
		return new NegotiatorSettings(Duration.ofMinutes(ANSWERED_MINUTES), Duration.ofSeconds(FAILED_SECONDS),
			Duration.ofSeconds(FAILED_SECONDS), Duration.ofMinutes(EXCLUDED_MINUTES), fallback);
	}
}
