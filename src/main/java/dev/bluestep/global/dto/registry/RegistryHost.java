package dev.bluestep.global.dto.registry;

import java.util.OptionalInt;

import org.jspecify.annotations.Nullable;

/**
 * One host a tenant is reachable at, paired with the ingress read timeout registered against
 * <em>that</em> host.
 *
 * <p>The timeout belongs to the host, not the tenant, so it travels with it: a service that forwards to
 * a host other than the tenant's default must use that host's timeout, and pairing them in one record
 * makes picking the wrong one hard.</p>
 *
 * @param host                      the host name, exactly as registered
 * @param ingressReadTimeoutSeconds the host's ingress read timeout in seconds, absent when none is
 *                                  registered
 */
public record RegistryHost(String host, @Nullable Integer ingressReadTimeoutSeconds) {

	/** {@link #ingressReadTimeoutSeconds}, as an {@code OptionalInt}. */
	public OptionalInt optIngressReadTimeoutSeconds() {
		return ingressReadTimeoutSeconds == null ? OptionalInt.empty() : OptionalInt.of(ingressReadTimeoutSeconds);
	}
}
