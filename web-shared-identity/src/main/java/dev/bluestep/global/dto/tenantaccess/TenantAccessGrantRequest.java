package dev.bluestep.global.dto.tenantaccess;

import java.time.OffsetDateTime;
import java.util.Optional;

import org.jspecify.annotations.Nullable;

import jakarta.validation.constraints.NotNull;

/**
 * The body of a single-grant write.
 *
 * <p>{@link #source} is required (7.0.0): it decides whether the grant provisions Organization Administrator
 * membership, so a write that does not say who made it is refused (400 under {@code @Valid}) rather than guessed
 * at. {@link #expiresAt} stays optional, so a body naming only the source grants permanent access — the ordinary
 * case, spelled the ordinary way. A grant is an administrative decision that stands until it is withdrawn, and
 * having to say so explicitly would make the exception look like the rule.</p>
 *
 * @param expiresAt when the grant should stop counting, or absent for one that never does. Absent and
 *                  an absent JSON field mean the same thing, which matters because re-granting with
 *                  no expiry is how a temporary grant is made permanent
 * @param source    who is writing the grant
 */
public record TenantAccessGrantRequest(@Nullable OffsetDateTime expiresAt, @NotNull TenantAccessGrantSource source) {

	/** {@link #expiresAt}, as an {@code Optional}. */
	public Optional<OffsetDateTime> optExpiresAt() {
		return Optional.ofNullable(expiresAt);
	}
}
