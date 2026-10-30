package dev.bluestep.global.dto.tenant;

import java.util.Optional;
import java.util.OptionalDouble;
import java.util.OptionalInt;

import org.jspecify.annotations.Nullable;

import dev.bluestep.global.dto.constraints.CodePointSize;
import dev.bluestep.global.dto.fleet.NamespaceName;
import dev.bluestep.global.dto.tenantaccess.ResellerKey;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

/**
 * The writable part of a tenant, shared by {@link TenantCreateRequest} and {@link TenantUpdateRequest}.
 *
 * <p>Each component has the name and type of the matching {@link TenantRecordResponse} component, so a
 * caller maps a read onto a write one way, component by component.</p>
 *
 * <h2>The whole set, not a patch</h2>
 *
 * <p>An update replaces every column here, and an absent value clears its column, as the monolith's
 * own save does: its tenant screen posts the whole form. A caller that read the tenant and changes one
 * field sends the rest back as it read them.</p>
 *
 * <h2>What is not here</h2>
 *
 * <ul>
 *   <li>The key and the {@code associationId}: the key is the path (or, on create, its own component)
 *       and the associationid is fixed at create.</li>
 *   <li>The lock: it has its own operation, {@link TenantLockUpdateRequest}, so a settings save cannot
 *       undo a lock an operator set meanwhile.</li>
 *   <li>Storage: no monolith code writes it; the installers do.</li>
 *   <li>The display name and the timestamps and actors, which web-global keeps itself.</li>
 * </ul>
 *
 * <h2>Constraints</h2>
 *
 * <p>Each string bound is the width of the {@code global.association} column behind it, as production
 * declares it, counted in code points ({@link CodePointSize}) as {@code varchar(n)} counts.
 * {@code namespace} is a {@link NamespaceName}, which refuses a name that is not a Kubernetes DNS label
 * when the body binds, and it must also name a namespace web-global knows; an unknown one is a
 * {@code 400}.</p>
 *
 * @param name                the tenant's name, also its root organization's
 * @param shortName           {@code shortname}
 * @param mission             {@code mission}
 * @param reseller            the reseller the tenant belongs to
 * @param namespace           the Kubernetes namespace whose database holds the tenant's schema
 * @param flags               {@code flags}, the monolith's bit set, verbatim
 * @param features            the on/off switches
 * @param requiredEndorsement {@code requiredendorsement}, as the monolith stores it (enciphered)
 * @param assignedServers     {@code assignedservers}, the monolith's comma-separated list, verbatim
 * @param formulaServer       {@code formulaserver}
 * @param distributionCount   {@code distributioncount}
 * @param defaultEmailValidatedCountdown {@code defaultemailvalidatedcountdown}
 * @param pageLockMillis      {@code pagelockmillis}
 * @param pageLockWarningMillis {@code pagelockwarningmillis}
 * @param helpTopicTemplate   {@code helptopictemplate}
 * @param signIn              how its users sign in
 * @param organization        its root organization
 */
public record TenantSettings(
		@NotBlank @CodePointSize(max = 100) String name,
		@CodePointSize(max = 50) @Nullable String shortName,
		@CodePointSize(max = 4000) @Nullable String mission,
		@NotNull ResellerKey reseller,
		@NotNull NamespaceName namespace,
		long flags,
		@NotNull TenantFeatures features,
		@CodePointSize(max = 100) @Nullable String requiredEndorsement,
		@CodePointSize(max = 2000) @Nullable String assignedServers,
		@CodePointSize(max = 100) @Nullable String formulaServer,
		@Nullable Integer distributionCount,
		int defaultEmailValidatedCountdown,
		@Nullable Double pageLockMillis,
		@Nullable Double pageLockWarningMillis,
		@CodePointSize(max = 50) @Nullable String helpTopicTemplate,
		@NotNull @Valid TenantSignIn signIn,
		@NotNull @Valid TenantOrganization organization) {

	/** {@link #shortName}, as an {@code Optional}. */
	public Optional<String> optShortName() {
		return Optional.ofNullable(shortName);
	}

	/** {@link #mission}, as an {@code Optional}. */
	public Optional<String> optMission() {
		return Optional.ofNullable(mission);
	}

	/** {@link #requiredEndorsement}, as an {@code Optional}. */
	public Optional<String> optRequiredEndorsement() {
		return Optional.ofNullable(requiredEndorsement);
	}

	/** {@link #assignedServers}, as an {@code Optional}. */
	public Optional<String> optAssignedServers() {
		return Optional.ofNullable(assignedServers);
	}

	/** {@link #formulaServer}, as an {@code Optional}. */
	public Optional<String> optFormulaServer() {
		return Optional.ofNullable(formulaServer);
	}

	/** {@link #distributionCount}, as an {@code OptionalInt}. */
	public OptionalInt optDistributionCount() {
		return distributionCount == null ? OptionalInt.empty() : OptionalInt.of(distributionCount);
	}

	/** {@link #pageLockMillis}, as an {@code OptionalDouble}. */
	public OptionalDouble optPageLockMillis() {
		return pageLockMillis == null ? OptionalDouble.empty() : OptionalDouble.of(pageLockMillis);
	}

	/** {@link #pageLockWarningMillis}, as an {@code OptionalDouble}. */
	public OptionalDouble optPageLockWarningMillis() {
		return pageLockWarningMillis == null ? OptionalDouble.empty() : OptionalDouble.of(pageLockWarningMillis);
	}

	/** {@link #helpTopicTemplate}, as an {@code Optional}. */
	public Optional<String> optHelpTopicTemplate() {
		return Optional.ofNullable(helpTopicTemplate);
	}
}
