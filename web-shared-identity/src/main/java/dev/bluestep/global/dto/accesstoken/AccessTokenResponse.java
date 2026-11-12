package dev.bluestep.global.dto.accesstoken;

import java.time.OffsetDateTime;
import java.util.Optional;

import org.jspecify.annotations.Nullable;

/**
 * Read model for an access token. {@code expiresAt} is absent when the token never
 * expires; {@code lastAccessed} is absent until the token has been used at least once.
 */
public record AccessTokenResponse(
		Long classid,
		Long seqnum,
		String encodedtoken,
		String alias,
		String[] scopes,
		@Nullable OffsetDateTime expiresAt,
		@Nullable OffsetDateTime lastAccessed,
		byte[] flags
) {

	/** {@link #expiresAt}, as an {@code Optional}: empty when the token never expires. */
	public Optional<OffsetDateTime> optExpiresAt() {
		return Optional.ofNullable(expiresAt);
	}

	/** {@link #lastAccessed}, as an {@code Optional}: empty until the token is first used. */
	public Optional<OffsetDateTime> optLastAccessed() {
		return Optional.ofNullable(lastAccessed);
	}
}
