package dev.bluestep.global.dto.aitenantblock;

import java.time.OffsetDateTime;

public record AiTenantBlockResponse(
	Long id,
	String schemaName,
	String organizationId,
	String reason,
	OffsetDateTime blockedAt) {
}
