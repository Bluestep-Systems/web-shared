package dev.bluestep.global.dto.registry;

import java.util.List;
import java.util.Optional;
import java.util.OptionalInt;

import org.jspecify.annotations.Nullable;

import dev.bluestep.global.dto.tenant.TenantKey;

/**
 * One tenant as the registry serves it (from 6.0.0): {@code GET /registry/tenants},
 * {@code /registry/tenants/by-host/{host}} and {@code /registry/tenants/{tenantId}}.
 *
 * @param tenant                    the key
 * @param tenantId                  the tenant's U-number, {@code U<seqnum>} (the old registry's
 *                                  {@code orgKey})
 * @param name                      the tenant's name, absent where its row has none
 * @param defaultHost               the tenant's default host; absent when it has no domains, which a
 *                                  caller treats as "not reachable through a gateway"
 * @param ingressReadTimeoutSeconds the default host's ingress read timeout in seconds, absent when none
 *                                  is registered
 * @param hosts                     every host the tenant is reachable at, best first: the default host,
 *                                  then its other domains by key. A gateway forwards to the first one
 *                                  its allowlist admits. Empty on the listing, which advertises tenants
 *                                  but forwards nothing.
 */
public record RegistryTenantResponse(
		TenantKey tenant,
		String tenantId,
		@Nullable String name,
		@Nullable String defaultHost,
		@Nullable Integer ingressReadTimeoutSeconds,
		List<RegistryHost> hosts) {

	/**
	 * Jackson binds an omitted array to {@code null} whatever this package's null-marking says, and a
	 * caller that built the list keeps a handle on it, so the list is both defaulted and copied.
	 */
	public RegistryTenantResponse {
		hosts = hosts == null ? List.of() : List.copyOf(hosts);
	}

	/** {@link #name}, as an {@code Optional}. */
	public Optional<String> optName() {
		return Optional.ofNullable(name);
	}

	/** {@link #defaultHost}, as an {@code Optional}. */
	public Optional<String> optDefaultHost() {
		return Optional.ofNullable(defaultHost);
	}

	/** {@link #ingressReadTimeoutSeconds}, as an {@code OptionalInt}. */
	public OptionalInt optIngressReadTimeoutSeconds() {
		return ingressReadTimeoutSeconds == null ? OptionalInt.empty() : OptionalInt.of(ingressReadTimeoutSeconds);
	}
}
