package dev.bluestep.global.dto.domain;

import java.time.LocalDateTime;
import java.util.Optional;
import java.util.OptionalInt;

import org.jspecify.annotations.Nullable;

import dev.bluestep.global.dto.basetable.BaseTableKey;
import dev.bluestep.global.dto.tenant.TenantActor;
import dev.bluestep.global.dto.tenant.TenantKey;

/**
 * One domain: a row of web-global's {@code global.tenant_domain}, which holds what the monolith spread
 * over a domain's {@code domainname}, {@code logintype} and {@code basetable} rows.
 *
 * <p>The version travels in the {@code ETag}, as it does for a tenant: it is what a conditional write is
 * pinned to.</p>
 *
 * @param domain                    the key
 * @param host                      the host name, lower case, unique across the fleet
 * @param tenant                    the tenant it serves (the monolith's root)
 * @param organization              the unit inside the tenant's schema that the host lands on: its root
 *                                  organization (classid 111111) or a unit (111000); absent for the
 *                                  tenant's default. web-global stores it and does not resolve it.
 * @param domainPrefix              {@code domainprefix}: the site prefix the host was built from, if any
 * @param flags                     {@code flags}, the monolith's bit set, verbatim
 * @param ingressReadTimeoutSeconds the ingress read timeout for the host, absent where the column is
 * @param signIn                    the domain's own sign-in type, absent where it has none and the
 *                                  tenant's applies
 * @param created                   when it was created, as its monolith row recorded it
 * @param modified                  when it was last edited
 * @param creator                   who created it, absent for the system or for a stamp that names
 *                                  nobody who still exists
 * @param modifier                  who last edited it, on the same terms
 */
public record DomainRecordResponse(
		DomainKey domain,
		String host,
		TenantKey tenant,
		@Nullable BaseTableKey organization,
		@Nullable String domainPrefix,
		long flags,
		@Nullable Integer ingressReadTimeoutSeconds,
		@Nullable LoginTypeSetting signIn,
		@Nullable LocalDateTime created,
		@Nullable LocalDateTime modified,
		@Nullable TenantActor creator,
		@Nullable TenantActor modifier) {

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

	/** {@link #created}, as an {@code Optional}. */
	public Optional<LocalDateTime> optCreated() {
		return Optional.ofNullable(created);
	}

	/** {@link #modified}, as an {@code Optional}. */
	public Optional<LocalDateTime> optModified() {
		return Optional.ofNullable(modified);
	}

	/** {@link #creator}, as an {@code Optional}. */
	public Optional<TenantActor> optCreator() {
		return Optional.ofNullable(creator);
	}

	/** {@link #modifier}, as an {@code Optional}. */
	public Optional<TenantActor> optModifier() {
		return Optional.ofNullable(modifier);
	}
}
