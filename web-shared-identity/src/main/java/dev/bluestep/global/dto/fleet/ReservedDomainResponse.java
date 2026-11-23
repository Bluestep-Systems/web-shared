package dev.bluestep.global.dto.fleet;

import java.util.Optional;

import org.jspecify.annotations.Nullable;

import dev.bluestep.global.dto.domain.DomainPrefix;

/**
 * One reserved domain name: a row of web-global's {@code reserved_domain}, answered by
 * {@code GET /reserved-domains/{name}} (matched case-insensitively; {@code 404} when the name is not
 * reserved). A site prefix that is reserved cannot be used to build a host.
 *
 * <p>{@code domain} is a {@link DomainPrefix} (a label such as {@code admin}, or a dotted one), not a
 * host: it is never served or resolved, and is matched only against a requested prefix. On the wire it
 * is the bare string.</p>
 *
 * @param domain the reserved name
 * @param holder who it is reserved for (the legacy {@code associationname}), absent where none is
 *               recorded
 */
public record ReservedDomainResponse(DomainPrefix domain, @Nullable String holder) {

	/** {@link #holder}, as an {@code Optional}. */
	public Optional<String> optHolder() {
		return Optional.ofNullable(holder);
	}
}
