package dev.bluestep.global.dto.domain;

import java.util.Optional;
import java.util.OptionalInt;

import org.jspecify.annotations.Nullable;

/**
 * One host of a namespace's ingress (from 6.0.0): a row of {@code GET /domains/ingress?namespace=},
 * the join the monolith's {@code IngressUtil} ran over {@code domainname}, its tenant and
 * {@code wildcarddomain}. Only hosts of tenants in that namespace are listed; grouping them into
 * ingress resources stays the caller's.
 *
 * @param host                      the host name
 * @param ingressReadTimeoutSeconds the ingress read timeout, absent where the column is
 * @param domainPrefix              the site prefix the host was built from, if any
 * @param wildcard                  the wildcard domain the host falls under, absent for none
 */
public record DomainIngressRow(
		String host,
		@Nullable Integer ingressReadTimeoutSeconds,
		@Nullable String domainPrefix,
		@Nullable String wildcard) {

	/** {@link #ingressReadTimeoutSeconds}, as an {@code OptionalInt}. */
	public OptionalInt optIngressReadTimeoutSeconds() {
		return ingressReadTimeoutSeconds == null ? OptionalInt.empty() : OptionalInt.of(ingressReadTimeoutSeconds);
	}

	/** {@link #domainPrefix}, as an {@code Optional}. */
	public Optional<String> optDomainPrefix() {
		return Optional.ofNullable(domainPrefix);
	}

	/** {@link #wildcard}, as an {@code Optional}. */
	public Optional<String> optWildcard() {
		return Optional.ofNullable(wildcard);
	}
}
