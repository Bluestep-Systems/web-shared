package dev.bluestep.global.dto.accesstoken;

import java.util.Optional;

/**
 * What an access token may be used for. Stored in {@code global.access_token.scopes} (text[]) and carried on the
 * wire by {@link #wire()}. A value outside this set is ignored by every reader: it grants nothing.
 */
public enum TokenScope {
	/** MCP through the gateway's /mcp, and Bearer b6pt_ authentication at tenant hosts (the monolith). Every
	 *  token minted before 6.4.0 holds exactly this. */
	MCP("mcp"),
	/** Fetch/clone knowledge-base repos (global and vouched tenant) and read the KB REST surface. */
	KB_READ("kb:read"),
	/** Push to a KB's live branch (on top of the per-KB manager/vouch check). */
	KB_PUSH("kb:push"),
	/** Rewrite a KB's history: a force push or a ref deletion on a KB repo. One of three conditions, all required:
	 *  the token's owner is a super, holds an unrevoked history-manager grant from another super (both read
	 *  together as {@code GlobalUserAccessResponse.historyManager}), and the token carries this scope. Never granted
	 *  by a migration or by the legacy rule; minted only for an owner who is a history manager. */
	KB_REWRITE("kb:rewrite"),
	/** Fetch/clone marketplace repos and read the registry REST surface. */
	REGISTRY_READ("registry:read"),
	/** Reserved: marketplace push is refused at the proxy in release 1 whatever the scope. */
	REGISTRY_PUSH("registry:push"),
	/** Self-service token management, /api/v1/tokens (TK1). */
	TOKENS_SELF("tokens:self");

	private final String wire;

	TokenScope(String wire) {
		this.wire = wire;
	}

	/** The stored and transmitted spelling, e.g. {@code kb:read}. */
	public String wire() {
		return wire;
	}

	/** The scope this spelling names, exactly (case-sensitive); empty for anything else. */
	public static Optional<TokenScope> fromWire(String value) {
		for (TokenScope scope : values()) {
			if (scope.wire.equals(value)) {
				return Optional.of(scope);
			}
		}
		return Optional.empty();
	}
}
