/**
 * The contract-negotiation protocol (version {@value dev.bluestep.contract.ContractHandshake#PROTOCOL}), shared by its
 * servers ({@code server}) and clients ({@code client}).
 *
 * <h2>The protocol</h2>
 * <ul>
 *   <li><b>Handshake.</b> {@code GET <handshake path>} ({@code /api/contract} on web-global, {@code /harness/contract}
 *       on web's harness port), with the same credential as the data calls, answers {@code 200},
 *       {@code Cache-Control: no-store} and a {@link dev.bluestep.contract.ContractHandshake}: the protocol, the
 *       service, and every live tag, newest first. Any other verb is {@code 405 Allow: GET}.</li>
 *   <li><b>Data.</b> A stable path ({@code /api/contract/<segment>/…}, {@code /harness/<segment>/…}) carrying
 *       {@value dev.bluestep.contract.ContractHeaders#CONTRACT}{@code : <tag>}. The answer is the one the tagged form
 *       gives, plus the same header naming the tag that served it.</li>
 *   <li><b>Refusals</b>, before any handler is matched, each labelled with
 *       {@value dev.bluestep.contract.ContractHeaders#REFUSAL}: a missing, repeated or malformed header is {@code 400}
 *       {@code contract-header-invalid}; a tag that is not live is {@code 404 unknown-version}; a live tag that serves
 *       nothing on the segment is {@code 404 surface-not-served}
 *       ({@link dev.bluestep.contract.ContractRefusalToken}).</li>
 *   <li><b>Order.</b> Authentication runs first: an unauthenticated request is {@code 401} before any tag is
 *       evaluated, so an anonymous caller learns nothing about which tags exist.</li>
 * </ul>
 *
 * <p>The handshake enumerates live tags, which a refusal deliberately does not: acting on that list is what a
 * negotiating client does, and only an authenticated caller can ask for it. A refusal still names only the tag that
 * was asked for.</p>
 *
 * <p>Null-marked: every type in this package is non-null unless explicitly annotated
 * {@link org.jspecify.annotations.Nullable}.</p>
 */
@NullMarked
package dev.bluestep.contract;

import org.jspecify.annotations.NullMarked;
