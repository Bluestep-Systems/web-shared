package dev.bluestep.global.dto.accesstoken;

import java.time.OffsetDateTime;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

/**
 * Payload for creating an access token. The raw token value is generated server-side and
 * returned (once) in {@link CreateTokenResponse#rawtoken()}; only its hash is persisted.
 * {@code flags} is the raw byte representation of a {@code java.util.BitSet} (symmetric
 * with the {@code bytea} storage); Jackson transports it as base64. {@code expiresAt} may
 * be null to indicate the token never expires. {@code alias} is the user-visible label
 * shown in the admin UI so a token can be identified without exposing its encoded value.
 */
public record CreateTokenRequest(
		@NotNull Long classid,
		@NotNull Long seqnum,
		@NotBlank String alias,
		@NotNull String[] scopes,
		OffsetDateTime expiresAt,
		@NotNull byte[] flags
) {}
