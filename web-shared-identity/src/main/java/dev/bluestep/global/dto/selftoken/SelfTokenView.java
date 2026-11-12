package dev.bluestep.global.dto.selftoken;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.jspecify.annotations.Nullable;

/**
 * One of the owner's tokens, as {@code GET /self-tokens} lists it (6.5.0). The secret and its hash are never
 * part of it.
 *
 * @param id         the token's stable id, what {@code DELETE} and {@code /rotations} address
 * @param alias      the owner's name for it, unique per owner
 * @param scopes     the EFFECTIVE scopes, each a {@code TokenScope} wire spelling, sorted (web-global's legacy rule
 *                   applied; values outside the vocabulary are not sent). Strings, as in {@code TokenCheckResponse},
 *                   so a reader older than the app ignores a scope it does not know
 * @param createdAt  when it was minted; absent for a token minted before 6.5.0 (web-global did not record it)
 * @param expiresAt  when it stops working, possibly in the past (expired tokens are listed so they can be
 *                   revoked); absent when it never expires
 * @param lastUsedAt when it last authenticated, approximately (stamped at most every 12 hours); absent if never
 * @param current    whether this is the token presenting the request
 */
public record SelfTokenView(UUID id, String alias, List<String> scopes, @Nullable Instant createdAt,
		@Nullable Instant expiresAt, @Nullable Instant lastUsedAt, boolean current) {

	/** Jackson binds an omitted array to null whatever the null-marking says; absent means no scopes. */
	public SelfTokenView {
		scopes = scopes == null ? List.of() : List.copyOf(scopes);
	}

	/** {@link #createdAt}, as an {@code Optional}. */
	public Optional<Instant> optCreatedAt() {
		return Optional.ofNullable(createdAt);
	}

	/** {@link #expiresAt}, as an {@code Optional}: empty when the token never expires. */
	public Optional<Instant> optExpiresAt() {
		return Optional.ofNullable(expiresAt);
	}

	/** {@link #lastUsedAt}, as an {@code Optional}: empty when it was never used. */
	public Optional<Instant> optLastUsedAt() {
		return Optional.ofNullable(lastUsedAt);
	}
}
