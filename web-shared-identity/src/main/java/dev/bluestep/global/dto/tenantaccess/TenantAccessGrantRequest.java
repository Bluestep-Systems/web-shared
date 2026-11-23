package dev.bluestep.global.dto.tenantaccess;

import java.time.OffsetDateTime;
import java.util.Optional;

import org.jspecify.annotations.Nullable;

/**
 * The body of a single-grant write.
 *
 * <p>Both fields are optional, so an empty body grants permanent access — the ordinary case, spelled the ordinary
 * way. That is deliberate: a grant is an administrative decision that stands until it is withdrawn, and having to
 * say so explicitly would make the exception look like the rule.</p>
 *
 * <p>An absent {@link #source} (6.7.0) is read as {@link TenantAccessGrantSource#TENANT}: the source that
 * provisions nothing. A caller built before 6.7.0 cannot name one, so its grants stay catalog-only, which is what
 * every grant was before provenance existed; only a caller that says {@code SUPER} or {@code OPS} gets a grant
 * that makes its user an Organization Administrator.</p>
 *
 * @param expiresAt when the grant should stop counting, or absent for one that never does. Absent and
 *                  an absent JSON field mean the same thing, which matters because re-granting with
 *                  no expiry is how a temporary grant is made permanent
 * @param source    who is writing the grant, or absent for a caller that does not say, read as
 *                  {@link TenantAccessGrantSource#TENANT} (see {@link #effectiveSource()})
 */
public record TenantAccessGrantRequest(@Nullable OffsetDateTime expiresAt, @Nullable TenantAccessGrantSource source) {

	/**
	 * A grant that names no source, as every caller before 6.7.0 wrote one: read as
	 * {@link TenantAccessGrantSource#TENANT}.
	 *
	 * @param expiresAt when the grant should stop counting, or null for one that never does
	 */
	public TenantAccessGrantRequest(@Nullable OffsetDateTime expiresAt) {
		this(expiresAt, null);
	}

	/** {@link #expiresAt}, as an {@code Optional}. */
	public Optional<OffsetDateTime> optExpiresAt() {
		return Optional.ofNullable(expiresAt);
	}

	/** {@link #source}, as an {@code Optional}: empty when the caller did not say. */
	public Optional<TenantAccessGrantSource> optSource() {
		return Optional.ofNullable(source);
	}

	/**
	 * The source the grant is recorded under: {@link #source}, or {@link TenantAccessGrantSource#TENANT} when absent,
	 * so a grant whose writer is unknown never provisions Organization Administrator membership.
	 */
	public TenantAccessGrantSource effectiveSource() {
		return optSource().orElse(TenantAccessGrantSource.TENANT);
	}
}
