/**
 * The client side of contract negotiation: a call states a {@link dev.bluestep.contract.client.ContractNeed}, and a
 * {@link dev.bluestep.contract.client.ContractNegotiator} picks the tag to send to a peer from that peer's handshake,
 * caching it per peer and recovering from a peer mid-rollout. The transport is the consumer's
 * ({@link dev.bluestep.contract.client.HandshakeSource}), so this package depends on no HTTP client or JSON library.
 *
 * <p>Null-marked: every type in this package is non-null unless explicitly annotated
 * {@link org.jspecify.annotations.Nullable}.</p>
 */
@NullMarked
package dev.bluestep.contract.client;

import org.jspecify.annotations.NullMarked;
