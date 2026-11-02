package dev.bluestep.global.dto.fleet;

import java.util.Optional;

import org.jspecify.annotations.Nullable;

/**
 * One reserved domain name: a row of web-global's {@code reserved_domain}, answered by
 * {@code GET /reserved-domains/{name}} (matched case-insensitively; {@code 404} when the name is not
 * reserved). A site prefix that is reserved cannot be used to build a host.
 *
 * <p>{@code domain} is a site prefix (a label such as {@code admin}; a few production rows hold a dotted
 * one), not a host: it is never served or resolved, and is matched only against a requested
 * {@code domainPrefix}, itself a string. It stays a string with them; a prefix type would cover all of
 * them together.</p>
 *
 * @param domain the reserved name
 * @param holder who it is reserved for (the legacy {@code associationname}), absent where none is
 *               recorded
 */
public record ReservedDomainResponse(String domain, @Nullable String holder) {

	/** {@link #holder}, as an {@code Optional}. */
	public Optional<String> optHolder() {
		return Optional.ofNullable(holder);
	}
}
