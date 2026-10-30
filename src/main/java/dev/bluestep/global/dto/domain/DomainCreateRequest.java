package dev.bluestep.global.dto.domain;

import java.util.Optional;
import java.util.OptionalInt;

import org.jspecify.annotations.Nullable;

import dev.bluestep.global.dto.basetable.BaseTableKey;
import dev.bluestep.global.dto.constraints.CodePointSize;
import dev.bluestep.global.dto.tenant.TenantActor;
import dev.bluestep.global.dto.tenant.TenantKey;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
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
 * <p>{@link #HOST_PATTERN} is the monolith's {@code DomainName.isValidDomain}: lower-case labels of
 * letters, digits and inner hyphens, at most 63 each and 253 in all, at least one dot, and a last label
 * that is not all digits (so never an IP address). Every host can become an ingress host with a publicly
 * issued certificate. Every production host satisfies it.</p>
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
		@NotBlank @CodePointSize(max = 255) @Pattern(regexp = HOST_PATTERN) String host,
		@Nullable BaseTableKey organization,
		@CodePointSize(max = 256) @Nullable String domainPrefix,
		long flags,
		@Positive @Nullable Integer ingressReadTimeoutSeconds,
		@Valid @Nullable LoginTypeSetting signIn,
		@Nullable TenantActor creator) {

	/**
	 * A host name the fleet can serve: the monolith's {@code DomainName.isValidDomain} as one expression.
	 * The look-aheads hold the 253 total and the last label's letter; each label is at most 63.
	 */
	public static final String HOST_PATTERN = "(?=.{1,253}$)(?=.*\\.[-a-z0-9]*[a-z][-a-z0-9]*$)"
			+ "[a-z0-9]([-a-z0-9]{0,61}[a-z0-9])?(\\.[a-z0-9]([-a-z0-9]{0,61}[a-z0-9])?)+";

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
