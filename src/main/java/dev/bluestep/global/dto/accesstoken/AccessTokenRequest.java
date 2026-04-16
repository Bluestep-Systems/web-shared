package dev.bluestep.global.dto.accesstoken;

import java.time.OffsetDateTime;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

/**
 * Payload for creating an access token. {@code flags} is the raw byte representation of a
 * {@code java.util.BitSet} (symmetric with the {@code bytea} storage); Jackson transports
 * it as base64. {@code expiresAt} may be null to indicate the token never expires.
 */
public record AccessTokenRequest(
		@NotNull Long classid,
		@NotNull Long seqnum,
		@NotBlank String encodedtoken,
		@NotNull String[] scopes,
		OffsetDateTime expiresAt,
		@NotNull byte[] flags
) {}
