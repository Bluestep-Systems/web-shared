package dev.bluestep.global.dto.domain;

import java.util.Optional;
import java.util.OptionalInt;

import org.jspecify.annotations.Nullable;

import dev.bluestep.global.dto.basetable.BaseTableKey;
import dev.bluestep.global.dto.constraints.CodePointSize;
import dev.bluestep.global.dto.tenant.TenantActor;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;

/**
 * Editing a domain (from 6.0.0): {@code PUT /domains/{classid}/{seqnum}} with a required
 * {@code If-Match}, answered with the {@link DomainRecordResponse} and its new version as the
 * {@code ETag}.
 *
 * <p>A whole-record replace: every component is written, and an absent value clears its column, as the
 * monolith's own save does. An absent {@code ingressReadTimeoutSeconds} sets the default, 120 seconds,
 * as on a create. The tenant is not here: a domain never moves to another tenant. The edit marks the
 * tenant's namespace modified.</p>
 *
 * <p>The components and their constraints are {@link DomainCreateRequest}'s, the host checked against
 * {@link DomainCreateRequest#HOST_PATTERN}; refusals are a {@code 409} {@link DomainWriteRefusal}.</p>
 *
 * @param host                      the host name
 * @param organization              the unit inside the tenant's schema the host lands on, or absent for
 *                                  the tenant's default
 * @param domainPrefix              the site prefix the host was built from, or absent
 * @param flags                     the monolith's bit set, verbatim
 * @param ingressReadTimeoutSeconds the ingress read timeout, or absent for the default, 120 seconds
 * @param signIn                    the domain's own sign-in type, or absent for the tenant's
 * @param actor                     who is making the edit, or absent for no actor
 */
public record DomainUpdateRequest(
		@NotBlank @CodePointSize(max = 255) @Pattern(regexp = DomainCreateRequest.HOST_PATTERN) String host,
		@Nullable BaseTableKey organization,
		@CodePointSize(max = 256) @Nullable String domainPrefix,
		long flags,
		@Positive @Nullable Integer ingressReadTimeoutSeconds,
		@Valid @Nullable LoginTypeSetting signIn,
		@Nullable TenantActor actor) {

	/** {@link #organization}, as an {@code Optional}. */
	public Optional<BaseTableKey> optOrganization() {
		return Optional.ofNullable(organization);
	}

	/** {@link #domainPrefix}, as an {@code Optional}. */
	public Optional<String> optDomainPrefix() {
		return Optional.ofNullable(domainPrefix);
	}

	/** {@link #ingressReadTimeoutSeconds}, as an {@code OptionalInt}. */
	public OptionalInt optIngressReadTimeoutSeconds() {
		return ingressReadTimeoutSeconds == null ? OptionalInt.empty() : OptionalInt.of(ingressReadTimeoutSeconds);
	}

	/** {@link #signIn}, as an {@code Optional}. */
	public Optional<LoginTypeSetting> optSignIn() {
		return Optional.ofNullable(signIn);
	}

	/** {@link #actor}, as an {@code Optional}. */
	public Optional<TenantActor> optActor() {
		return Optional.ofNullable(actor);
	}
}
