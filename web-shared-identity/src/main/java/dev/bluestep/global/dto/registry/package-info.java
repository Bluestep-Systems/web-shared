/**
 * Wire shapes for the tenant registry (from 6.0.0): which tenants the fleet serves and at which hosts,
 * for services outside the monolith that route to a tenant (the MCP gateway, the OAuth 2 integrator).
 *
 * <ul>
 *   <li>{@link RegistryTenantResponse} is one tenant as such a service needs it: its key and U-number,
 *       its name and every host it is reachable at, best first.</li>
 *   <li>{@link RegistryHost} is one of those hosts with its own ingress read timeout.</li>
 * </ul>
 *
 * <p>web-global serves these under {@code /api/v6.0.0/registry/tenants}, behind the registry key rather
 * than the service key every other versioned surface uses. Its older, unversioned
 * {@code /registry/orgs} keeps its own shapes, in web-global, until its last consumer has moved.</p>
 *
 * <h2>Null-marked</h2>
 *
 * <p>Every type in this package is non-null unless annotated {@link org.jspecify.annotations.Nullable}.
 * A record component that may be absent is a {@code @Nullable} value, and the record declares an
 * {@code optX()} accessor that wraps it; outside the record, read the accessor. An absent key and an
 * explicit JSON {@code null} both bind as absent.</p>
 */
@NullMarked
package dev.bluestep.global.dto.registry;

import org.jspecify.annotations.NullMarked;
