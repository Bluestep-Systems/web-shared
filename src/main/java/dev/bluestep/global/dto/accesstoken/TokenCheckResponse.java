package dev.bluestep.global.dto.accesstoken;

import java.util.List;

import dev.bluestep.global.dto.tenantaccess.GlobalUserKey;

/**
 * A live token, as {@code GET /api/v<tag>/registry/token-check} answers it from 6.4.0 (200). A token that is not
 * live is a plain 404, as before.
 *
 * @param user   the global user the token belongs to
 * @param scopes what the token may do, each {@link TokenScope#wire()} spelling, sorted, no duplicates. The
 *               EFFECTIVE grant, after web-global's legacy rule (a token that stores no vocabulary scope holds
 *               {@code mcp}); values outside the vocabulary are never sent. Strings rather than
 *               {@code TokenScope} so a reader older than the app ignores a scope it does not know instead of
 *               failing to bind the whole answer.
 */
public record TokenCheckResponse(GlobalUserKey user, List<String> scopes) {

	/** Jackson binds an omitted array to null whatever the null-marking says; absent means no scopes. */
	public TokenCheckResponse {
		scopes = scopes == null ? List.of() : List.copyOf(scopes);
	}
}
