package dev.bluestep.global.dto.selftoken;

import java.util.List;

/**
 * The owner's tokens (6.5.0): {@code GET /self-tokens}. Newest {@code createdAt} first, tokens with no
 * {@code createdAt} last (by alias).
 *
 * @param tokens the tokens; empty when there are none
 */
public record SelfTokensResponse(List<SelfTokenView> tokens) {

	/** Jackson binds an omitted array to null whatever the null-marking says; absent means none. */
	public SelfTokensResponse {
		tokens = tokens == null ? List.of() : List.copyOf(tokens);
	}
}
