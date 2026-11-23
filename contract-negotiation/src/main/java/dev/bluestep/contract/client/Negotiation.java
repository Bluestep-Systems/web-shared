package dev.bluestep.contract.client;

import java.util.List;
import java.util.Objects;

import dev.bluestep.contract.ContractTag;

/** What a {@link ContractNegotiator} decided a call should send to a peer. */
public sealed interface Negotiation {

	/**
	 * Send the stable path with {@code B6P-Contract: <tag>}.
	 *
	 * @param tag        the highest tag both sides speak, at or above the call's floor, not excluded for the peer
	 * @param generation which handshake the tag was picked from; opaque, handed back with
	 *                   {@link ContractNegotiator#refused}
	 */
	record Tagged(ContractTag tag, long generation) implements Negotiation {

		/** Requires the tag. */
		public Tagged {
			Objects.requireNonNull(tag, "tag");
		}
	}

	/**
	 * The peer predates negotiation: send the old path style at {@code tag}, the call's floor
	 * ({@code /api/v6.4.0/…}). Only for a negotiator that allows the fallback (web-global's clients; never the harness,
	 * since a web without the protocol has no harness at all).
	 *
	 * @param tag the call's floor
	 */
	record PathStyle(ContractTag tag) implements Negotiation {

		/** Requires the tag. */
		public PathStyle {
			Objects.requireNonNull(tag, "tag");
		}
	}

	/**
	 * The peer answered, but serves no tag the call speaks at or above its floor (after exclusions). Handled the way a
	 * contract refusal is.
	 *
	 * @param offered the tags the peer offered, newest first
	 */
	record NoCommonTag(List<ContractTag> offered) implements Negotiation {

		/** Copies the tags. */
		public NoCommonTag {
			offered = List.copyOf(offered);
		}
	}

	/**
	 * No handshake to pick from: the peer could not be asked, or predates negotiation and the fallback is not allowed.
	 *
	 * @param reason why
	 */
	record Unavailable(Reason reason) implements Negotiation {

		/** Requires the reason. */
		public Unavailable {
			Objects.requireNonNull(reason, "reason");
		}

		/** Why there is no handshake. */
		public enum Reason {
			/** The handshake failed: unreachable, past its deadline, a {@code 5xx}, a body that did not bind. */
			HANDSHAKE_FAILED,
			/** The peer predates negotiation, and this negotiator does not fall back to the path style. */
			PREDATES_NEGOTIATION
		}
	}
}
