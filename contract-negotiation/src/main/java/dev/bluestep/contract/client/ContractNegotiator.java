package dev.bluestep.contract.client;

import java.net.URI;

/**
 * Picks the tag a call sends to a peer, from that peer's handshake, and recovers when the peer refuses it.
 *
 * <ul>
 *   <li><b>Pick</b>: the highest tag in (the peer's live tags ∩ the call's {@link ContractNeed#speaks()}) at or above
 *       its floor, and not excluded for that peer. None is {@link Negotiation.NoCommonTag}.</li>
 *   <li><b>Mismatch</b>: a {@link Negotiation.Tagged} call refused {@code unknown-version} or
 *       {@code surface-not-served} reached an older pod than the handshake did. {@link #exchange} then excludes that
 *       tag for that peer, re-handshakes once, re-picks and retries once; a second refusal goes back to the caller.
 *       New pods serve a superset of old pods' tags, so the re-pick is served by both and cannot flap back.</li>
 *   <li><b>A peer that predates negotiation</b> is sent the path style at the call's floor, when the negotiator allows
 *       it, and asked again later.</li>
 * </ul>
 */
public interface ContractNegotiator {

	/**
	 * Decides what a call to {@code peer} sends, from its cached handshake or a fresh one.
	 *
	 * @param peer the peer's base URI
	 * @param need what the call speaks
	 * @return the decision
	 * @throws InterruptedException the calling thread was interrupted, waiting for the peer's handshake
	 */
	Negotiation negotiate(URI peer, ContractNeed need) throws InterruptedException;

	/**
	 * Records that {@code peer} refused {@code refused}'s tag as a mismatch: the tag is excluded for that peer for a
	 * while, and the handshake it was picked from is dropped, so the next negotiation asks again.
	 *
	 * @param peer    the peer
	 * @param refused the negotiation whose call was refused
	 */
	void refused(URI peer, Negotiation.Tagged refused);

	/**
	 * Negotiates, makes the call, and on a mismatch refusal excludes the tag, re-handshakes and retries once.
	 *
	 * @param <T>      the caller's answer type
	 * @param peer     the peer
	 * @param need     what the call speaks
	 * @param exchange the call
	 * @return the last attempt's value
	 * @throws InterruptedException the calling thread was interrupted
	 */
	<T> T exchange(URI peer, ContractNeed need, ContractExchange<T> exchange) throws InterruptedException;
}
