/**
 * Wire shapes for what the whole fleet shares (from 6.0.0): its Kubernetes namespaces, the fleet-wide
 * lockout and the reserved domain names, as web-global's {@code fleet_namespace}, {@code fleet_lockout}
 * and {@code reserved_domain} tables hold them.
 *
 * <ul>
 *   <li>{@link FleetNamespaceResponse} is one namespace; {@link NamespaceModifiedRequest} clears (or
 *       sets) its modified mark, conditional on the version it was listed with.</li>
 *   <li>{@link FleetLockout} is the fleet-wide lockout, read and written whole through
 *       {@link FleetLockoutUpdateRequest}.</li>
 *   <li>{@link ReservedDomainResponse} is one reserved domain name.</li>
 * </ul>
 *
 * <p>web-global serves these under {@code /api/v6.0.0/namespaces}, {@code /fleet-lockout} and
 * {@code /reserved-domains}, and keeps the monolith's legacy {@code namespace}, {@code associationglobal}
 * and {@code reserveddomain} rows in step by trigger, so a monolith build still reading or writing them
 * sees the same values.</p>
 *
 * <h2>Null-marked</h2>
 *
 * <p>Every type in this package is non-null unless annotated {@link org.jspecify.annotations.Nullable}.
 * A record component that may be absent is a {@code @Nullable} value, and the record declares an
 * {@code optX()} accessor that wraps it; outside the record, read the accessor. An absent key and an
 * explicit JSON {@code null} both bind as absent.</p>
 */
@NullMarked
package dev.bluestep.global.dto.fleet;

import org.jspecify.annotations.NullMarked;
