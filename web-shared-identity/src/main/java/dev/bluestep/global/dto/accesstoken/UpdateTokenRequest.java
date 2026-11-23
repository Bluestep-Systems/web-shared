package dev.bluestep.global.dto.accesstoken;

import java.time.OffsetDateTime;
import java.util.Optional;

import org.jspecify.annotations.Nullable;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

/**
 * Full-replacement payload for PUT /api/v1/access-tokens/by-token. All mutable fields
 * are replaced with the values in this body; an absent {@code expiresAt} means the token
 * never expires.
 */
public record UpdateTokenRequest(
		@NotBlank String alias,
		@NotNull String[] scopes,
		@Nullable OffsetDateTime expiresAt,
		@NotNull byte[] flags
) {

	/** {@link #expiresAt}, as an {@code Optional}: empty when the token never expires. */
	public Optional<OffsetDateTime> optExpiresAt() {
		return Optional.ofNullable(expiresAt);
	}
}
