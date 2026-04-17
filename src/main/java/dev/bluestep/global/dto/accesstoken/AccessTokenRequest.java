package dev.bluestep.global.dto.accesstoken;

import java.time.OffsetDateTime;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

/**
 * Payload for creating an access token. {@code flags} is the raw byte representation of a
 * {@code java.util.BitSet} (symmetric with the {@code bytea} storage); Jackson transports
 * it as base64. {@code expiresAt} may be null to indicate the token never expires.
 * {@code displayName} is the user-visible label shown in the admin UI so a token can be
 * identified without exposing its encoded value.
 */
public record AccessTokenRequest(
		@NotNull Long classid,
		@NotNull Long seqnum,
		@NotBlank String encodedtoken,
		@NotBlank String displayName,
		@NotNull String[] scopes,
		OffsetDateTime expiresAt,
		@NotNull byte[] flags
) {}
