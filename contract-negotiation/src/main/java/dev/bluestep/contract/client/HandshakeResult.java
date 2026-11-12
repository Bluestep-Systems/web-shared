package dev.bluestep.contract.client;

import java.util.Objects;

import dev.bluestep.contract.ContractHandshake;

/** What asking a peer for its handshake came to: a {@link HandshakeSource}'s answer. */
public sealed interface HandshakeResult {

	/**
	 * The peer answered {@code 200} with a handshake its body bound to.
	 *
	 * @param handshake the handshake
	 */
	record Answered(ContractHandshake handshake) implements HandshakeResult {

		/** Requires the handshake. */
		public Answered {
			Objects.requireNonNull(handshake, "handshake");
		}
	}

	/**
	 * The peer predates negotiation: its handshake path answered {@code 404}, or a {@code 401} that only the handshake
	 * gets (a web-global older than the protocol denies unlisted routes with {@code 401}).
	 */
	record PredatesNegotiation() implements HandshakeResult {
	}

	/**
	 * Anything else: unreachable, past its deadline, a {@code 5xx}, a body that did not bind.
	 *
	 * @param reason what happened, for a log line; never a credential or a body
	 */
	record Failed(String reason) implements HandshakeResult {

		/** Requires the reason. */
		public Failed {
			Objects.requireNonNull(reason, "reason");
		}
	}
}
