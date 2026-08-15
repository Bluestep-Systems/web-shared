package dev.bluestep.global.dto.accesstoken;

import java.time.OffsetDateTime;
import java.util.Optional;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

/**
 * Full-replacement payload for PUT /api/v1/access-tokens/by-token. All mutable fields
 * are replaced with the values in this body; an empty {@code expiresAt} means the token
 * never expires.
 */
public record UpdateTokenRequest(
		@NotBlank String alias,
		@NotNull String[] scopes,
		Optional<OffsetDateTime> expiresAt,
		@NotNull byte[] flags
) {}
