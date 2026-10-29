package dev.bluestep.global.dto.accesstoken;

import java.time.OffsetDateTime;
import java.util.Optional;

import org.jspecify.annotations.Nullable;

/**
 * Response to a successful access-token creation. {@code rawtoken} is the plaintext value
 * and is the only time it will be returned — callers must surface it to the end user
 * immediately. {@code encodedtoken} is the stored hash; subsequent operations
 * ({@code /by-token} lookups, updates, revocations) identify the token by this value.
 * {@code expiresAt} is absent when the token never expires; {@code lastAccessed} is absent
 * on a freshly created token.
 */
public record CreateTokenResponse(
		String rawtoken,
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

	/** {@link #lastAccessed}, as an {@code Optional}: empty on a freshly created token. */
	public Optional<OffsetDateTime> optLastAccessed() {
		return Optional.ofNullable(lastAccessed);
	}
}
