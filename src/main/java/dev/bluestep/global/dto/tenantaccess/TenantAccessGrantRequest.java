package dev.bluestep.global.dto.tenantaccess;

import java.time.OffsetDateTime;
import java.util.Optional;

/**
 * The body of a single-grant write.
 *
 * <p>The only field is optional, so an empty body grants permanent access — the ordinary case,
 * spelled the ordinary way. That is deliberate: a grant is an administrative decision that stands
 * until it is withdrawn, and having to say so explicitly would make the exception look like the
 * rule.</p>
 *
 * @param expiresAt when the grant should stop counting, or empty for one that never does. Empty and
 *                  an absent JSON field mean the same thing, which matters because re-granting with
 *                  no expiry is how a temporary grant is made permanent
 */
public record TenantAccessGrantRequest(Optional<OffsetDateTime> expiresAt) {

	/** An empty body is a permanent grant, so the record has to survive one. */
	public TenantAccessGrantRequest() {
		this(Optional.empty());
	}
}
