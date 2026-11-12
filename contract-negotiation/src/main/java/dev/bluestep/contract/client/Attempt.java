package dev.bluestep.contract.client;

import java.util.Objects;

import dev.bluestep.contract.ContractRefusalToken;

/**
 * What one attempt of a {@link ContractExchange} came to: the caller's value, and whether the peer refused the tag.
 *
 * @param <T> the caller's answer type
 */
public sealed interface Attempt<T> {

	/** The caller's value, whichever way the attempt went. */
	T value();

	/**
	 * The attempt was not refused by the contract machinery (an answer, an ordinary error, an unreachable peer).
	 *
	 * @param <T>   the caller's answer type
	 * @param value the caller's value
	 */
	record Done<T>(T value) implements Attempt<T> {

		/** Requires the value. */
		public Done {
			Objects.requireNonNull(value, "value");
		}
	}

	/**
	 * The peer refused the tag, its {@code X-Contract-Refusal} naming {@code token}.
	 *
	 * @param <T>   the caller's answer type
	 * @param token the refusal
	 * @param value what the caller answers if this refusal stands
	 */
	record Refused<T>(ContractRefusalToken token, T value) implements Attempt<T> {

		/** Requires both. */
		public Refused {
			Objects.requireNonNull(token, "token");
			Objects.requireNonNull(value, "value");
		}
	}

	/**
	 * An attempt that was not refused.
	 *
	 * @param <T>   the caller's answer type
	 * @param value the caller's value
	 * @return the attempt
	 */
	static <T> Attempt<T> done(T value) {
		return new Done<>(value);
	}

	/**
	 * An attempt the peer refused.
	 *
	 * @param <T>   the caller's answer type
	 * @param token the refusal
	 * @param value what the caller answers if it stands
	 * @return the attempt
	 */
	static <T> Attempt<T> refused(ContractRefusalToken token, T value) {
		return new Refused<>(token, value);
	}
}
