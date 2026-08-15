package dev.bluestep.global.dto.accesstoken;

import java.time.OffsetDateTime;
import java.util.Optional;

/**
 * Read model for an access token. {@code expiresAt} is empty when the token never
 * expires; {@code lastAccessed} is empty until the token has been used at least once.
 */
public record AccessTokenResponse(
		Long classid,
		Long seqnum,
		String encodedtoken,
		String alias,
		String[] scopes,
		Optional<OffsetDateTime> expiresAt,
		Optional<OffsetDateTime> lastAccessed,
		byte[] flags
) {}
