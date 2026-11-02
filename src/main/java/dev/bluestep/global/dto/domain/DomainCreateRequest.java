package dev.bluestep.global.dto.domain;

import java.util.Optional;
import java.util.OptionalInt;

import org.jspecify.annotations.Nullable;

import dev.bluestep.global.dto.basetable.BaseTableKey;
import dev.bluestep.global.dto.constraints.CodePointSize;
import dev.bluestep.global.dto.tenant.TenantActor;
import dev.bluestep.global.dto.tenant.TenantKey;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

/**
 * Creating a domain (from 6.0.0): {@code POST /domains}, answered {@code 201} with the
 * {@link DomainRecordResponse}, its version as the {@code ETag} and its {@code Location}. web-global
 * draws the key from {@code seq_120100}, the sequence the monolith's own JDBC draws from.
 *
 * <p>A host another domain holds, a host whose prefix is a reserved domain, or a tenant that does not
 * exist is a {@code 409} {@link DomainWriteRefusal}. The create marks the tenant's namespace modified,
 * so the ingress picks the host up.</p>
 *
 * <h2>The host</h2>
 *
 * <p>A {@link DomainHost}, validated ({@code @Valid}) against {@link DomainHost#HOST_PATTERN}, the
 * monolith's {@code DomainName.isValidDomain}: a request naming a host the fleet cannot serve is a
 * {@code 400}. Every production host satisfies it.</p>
 *
 * @param tenant                    the tenant the host serves
 * @param host                      the host name
 * @param organization              the unit inside the tenant's schema the host lands on, or absent for
 *                                  the tenant's default
 * @param domainPrefix              the site prefix the host was built from, or absent
 * @param flags                     the monolith's bit set, verbatim
 * @param ingressReadTimeoutSeconds the ingress read timeout, or absent for the default, 120 seconds
 * @param signIn                    the domain's own sign-in type, or absent for the tenant's
 * @param creator                   who is creating it, or absent for no actor
 */
public record DomainCreateRequest(
		@NotNull TenantKey tenant,
		@NotNull @Valid DomainHost host,
		@Nullable BaseTableKey organization,
		@CodePointSize(max = 256) @Nullable String domainPrefix,
		long flags,
		@Positive @Nullable Integer ingressReadTimeoutSeconds,
		@Valid @Nullable LoginTypeSetting signIn,
		@Nullable TenantActor creator) {

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

	/** {@link #creator}, as an {@code Optional}. */
	public Optional<TenantActor> optCreator() {
		return Optional.ofNullable(creator);
	}
}
