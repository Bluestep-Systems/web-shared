package dev.bluestep.global.dto.consoleglobaluser;

import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

import org.jspecify.annotations.Nullable;

/**
 * One access token's metadata, as the Console lists an account's tokens (from 6.13.0). Named by its row's {@code id}
 * only: <b>never its secret and never its hash</b>, which is the token's identity to web-global and so as good as the
 * token on any surface that looks one up by it.
 *
 * @param id         the token row's id
 * @param alias      the owner's label for it
 * @param scopes     the scopes as stored, in order
 * @param createdAt  when it was minted; absent for a row older than the column
 * @param expiresAt  when it stops working; absent for a token that never expires
 * @param lastUsedAt when it last authenticated a request; absent until it first does
 * @param live       whether it would authenticate now: unexpired and its owner enabled
 */
public record ConsoleAccessToken(
		UUID id,
		String alias,
		List<String> scopes,
		@Nullable Instant createdAt,
		@Nullable Instant expiresAt,
		@Nullable Instant lastUsedAt,
		boolean live) {

	/** Requires the id and alias, and copies the scopes. */
	public ConsoleAccessToken {
		Objects.requireNonNull(id, "id");
		Objects.requireNonNull(alias, "alias");
		scopes = scopes == null ? List.of() : List.copyOf(scopes);
	}

	/** {@link #createdAt}, as an {@code Optional}. */
	public Optional<Instant> optCreatedAt() {
		return Optional.ofNullable(createdAt);
	}

	/** {@link #expiresAt}, as an {@code Optional}. */
	public Optional<Instant> optExpiresAt() {
		return Optional.ofNullable(expiresAt);
	}

	/** {@link #lastUsedAt}, as an {@code Optional}. */
	public Optional<Instant> optLastUsedAt() {
		return Optional.ofNullable(lastUsedAt);
	}
}
