package dev.bluestep.global.dto.accesstoken;

import java.time.OffsetDateTime;

import jakarta.validation.constraints.NotNull;

/**
 * Full-replacement payload for PUT /api/v1/access-tokens/by-token. All mutable fields
 * are replaced with the values in this body; {@code expiresAt} may be null (never expires).
 */
public record UpdateTokenRequest(
		@NotNull String[] scopes,
		OffsetDateTime expiresAt,
		@NotNull byte[] flags
) {}
