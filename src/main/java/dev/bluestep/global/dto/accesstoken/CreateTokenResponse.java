package dev.bluestep.global.dto.accesstoken;

import java.time.OffsetDateTime;
import java.util.Optional;

/**
 * Response to a successful access-token creation. {@code rawtoken} is the plaintext value
 * and is the only time it will be returned — callers must surface it to the end user
 * immediately. {@code encodedtoken} is the stored hash; subsequent operations
 * ({@code /by-token} lookups, updates, revocations) identify the token by this value.
 * {@code expiresAt} is empty when the token never expires; {@code lastAccessed} is empty
 * on a freshly created token.
 */
public record CreateTokenResponse(
		String rawtoken,
		Long classid,
		Long seqnum,
		String encodedtoken,
		String alias,
		String[] scopes,
		Optional<OffsetDateTime> expiresAt,
		Optional<OffsetDateTime> lastAccessed,
		byte[] flags
) {}
