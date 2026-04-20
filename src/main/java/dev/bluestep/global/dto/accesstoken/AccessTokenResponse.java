package dev.bluestep.global.dto.accesstoken;

import java.time.OffsetDateTime;

public record AccessTokenResponse(
		Long classid,
		Long seqnum,
		String encodedtoken,
		String alias,
		String[] scopes,
		OffsetDateTime expiresAt,
		OffsetDateTime lastAccessed,
		byte[] flags
) {}
