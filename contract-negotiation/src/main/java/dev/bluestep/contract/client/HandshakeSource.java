package dev.bluestep.contract.client;

import java.net.URI;

/**
 * The transport a {@link ContractNegotiator} asks a peer's handshake through: the consumer's own HTTP client, with its
 * credential and its deadline (web-global and the gateway use {@code dev.bluestep:deadline-http}). Implementations map
 * the answer onto a {@link HandshakeResult}: {@code 200} with a body that binds to
 * {@link dev.bluestep.contract.ContractHandshake} is {@code Answered}; {@code 404} (or a {@code 401} on the handshake
 * only) is {@code PredatesNegotiation}; everything else is {@code Failed}.
 *
 * <p>A fetch must end within its own deadline: the negotiator holds the peer's lock while it runs, which is what makes
 * concurrent callers share one fetch.</p>
 */
@FunctionalInterface
public interface HandshakeSource {

	/**
	 * Asks {@code peer} for its handshake.
	 *
	 * @param peer the peer's base URI, the cache key ({@code http://web.b6p-07.svc.cluster.local:8082})
	 * @return what came of it; never thrown as an exception
	 * @throws InterruptedException the calling thread was interrupted
	 */
	HandshakeResult fetch(URI peer) throws InterruptedException;
}
