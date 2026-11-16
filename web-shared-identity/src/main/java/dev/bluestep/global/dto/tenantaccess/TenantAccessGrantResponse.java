package dev.bluestep.global.dto.tenantaccess;

import java.time.OffsetDateTime;
import java.util.Optional;

import org.jspecify.annotations.Nullable;

/**
 * One tenant-access grant as the API reports it.
 *
 * @param user               the global user the grant is for
 * @param grantedAt          when the grant was written
 * @param expiresAt          when it stops counting, or absent for one that does not — the ordinary case
 * @param live               whether it counts as of this response. Derived by the server rather than left to
 *                           the reader: an expiry in the past and no expiry at all are both common, and
 *                           asking every caller to re-implement the comparison is how two callers come to
 *                           disagree about who has access
 * @param source             who wrote the grant (7.0.0)
 * @param adminProvisionedAt when the monolith made the grant's user an Organization Administrator of the tenant
 *                           because of this grant, or absent when it has not (7.0.0). The marker that lets
 *                           removal take away only the memberships a grant added, never one an administrator gave
 *                           by hand
 */
public record TenantAccessGrantResponse(GlobalUserKey user, OffsetDateTime grantedAt,
		@Nullable OffsetDateTime expiresAt, boolean live, TenantAccessGrantSource source,
		@Nullable OffsetDateTime adminProvisionedAt) {

	/**
	 * Builds a response, settling {@code live} against {@code asOf}.
	 *
	 * <p>Takes the grant's parts rather than a persistent type: this record travels to callers that
	 * have no entity classes, and a DTO that needed one would not be shareable at all.</p>
	 */
	public static TenantAccessGrantResponse of(GlobalUserKey user, OffsetDateTime grantedAt,
			Optional<OffsetDateTime> expiresAt, OffsetDateTime asOf, TenantAccessGrantSource source,
			Optional<OffsetDateTime> adminProvisionedAt) {
		return new TenantAccessGrantResponse(user, grantedAt, expiresAt.orElse(null),
				expiresAt.map(end -> end.isAfter(asOf)).orElse(true), source, adminProvisionedAt.orElse(null));
	}

	/** {@link #expiresAt}, as an {@code Optional}. */
	public Optional<OffsetDateTime> optExpiresAt() {
		return Optional.ofNullable(expiresAt);
	}

	/** {@link #adminProvisionedAt}, as an {@code Optional}. */
	public Optional<OffsetDateTime> optAdminProvisionedAt() {
		return Optional.ofNullable(adminProvisionedAt);
	}
}
