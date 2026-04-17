package dev.bluestep.global.dto.accesstoken;

import java.time.OffsetDateTime;

public record AccessTokenResponse(
		Long classid,
		Long seqnum,
		String encodedtoken,
		String displayName,
		String[] scopes,
		OffsetDateTime expiresAt,
		byte[] flags
) {}
