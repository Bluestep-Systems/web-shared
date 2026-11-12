/**
 * The server side of contract negotiation: a {@link dev.bluestep.contract.server.ContractRegistry} says what a service
 * serves, and {@link dev.bluestep.contract.server.NegotiatedContractFilter} answers the handshake and enforces
 * {@code B6P-Contract} before any handler is matched.
 *
 * <p>Null-marked: every type in this package is non-null unless explicitly annotated
 * {@link org.jspecify.annotations.Nullable}.</p>
 */
@NullMarked
package dev.bluestep.contract.server;

import org.jspecify.annotations.NullMarked;
