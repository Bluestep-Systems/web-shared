package dev.bluestep.global.dto.tenantaccess;

import java.time.OffsetDateTime;
import java.util.Optional;

/**
 * One tenant-access grant as the API reports it.
 *
 * @param user      the global user the grant is for
 * @param grantedAt when the grant was written
 * @param expiresAt when it stops counting, or empty for one that does not — the ordinary case
 * @param live      whether it counts as of this response. Derived by the server rather than left to
 *                  the reader: an expiry in the past and no expiry at all are both common, and
 *                  asking every caller to re-implement the comparison is how two callers come to
 *                  disagree about who has access
 */
public record TenantAccessGrantResponse(GlobalUserKey user, OffsetDateTime grantedAt,
		Optional<OffsetDateTime> expiresAt, boolean live) {

	/**
	 * Builds a response, settling {@code live} against {@code asOf}.
	 *
	 * <p>Takes the grant's parts rather than a persistent type: this record travels to callers that
	 * have no entity classes, and a DTO that needed one would not be shareable at all.</p>
	 */
	public static TenantAccessGrantResponse of(GlobalUserKey user, OffsetDateTime grantedAt,
			Optional<OffsetDateTime> expiresAt, OffsetDateTime asOf) {
		return new TenantAccessGrantResponse(user, grantedAt, expiresAt,
				expiresAt.map(end -> end.isAfter(asOf)).orElse(true));
	}
}
