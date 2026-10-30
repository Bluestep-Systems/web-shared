package dev.bluestep.global.dto.tenant;

import java.time.LocalDateTime;
import java.util.Optional;
import java.util.OptionalDouble;
import java.util.OptionalInt;

import org.jspecify.annotations.Nullable;

import dev.bluestep.global.dto.tenantaccess.ResellerKey;

/**
 * One tenant: a row of web-global's {@code global.tenant}, which holds what the monolith spread over
 * a tenant's {@code association}, {@code organization}, {@code logintype} and {@code basetable} rows.
 *
 * <p>Every column the monolith's loaders read is here, grouped where a group reads better than a flat
 * list. The version travels in the {@code ETag}, not here, because it is what a conditional request
 * is pinned to. web-global keeps its own version: it advances on every change to the row, whichever
 * side made it, so it is not the monolith's {@code basetable} version.</p>
 *
 * <p>Strings that the monolith stores encrypted ({@code requiredEndorsement}) are served as stored;
 * only the monolith decrypts them.</p>
 *
 * @param tenant         the key
 * @param name           the tenant's name; absent only on a row whose name column was never set
 * @param displayName    the generic object display name the monolith's {@code basetable} carried; no
 *                       production tenant has one
 * @param shortName      {@code shortname}
 * @param mission        {@code mission}
 * @param associationId  {@code associationid}, the monolith's free-text id; the tenant's schema id is
 *                       {@code U<seqnum>} whatever this holds
 * @param reseller       the reseller the tenant belongs to; every tenant has one
 * @param namespace      the Kubernetes namespace whose database holds the tenant's schema
 * @param flags          {@code flags}, the monolith's bit set, verbatim; zero when absent
 * @param features       the on/off switches
 * @param lock           the tenant's own lock
 * @param requiredEndorsement {@code requiredendorsement}, as stored (enciphered)
 * @param assignedServers {@code assignedservers}, the monolith's comma-separated list, verbatim
 * @param formulaServer  {@code formulaserver}
 * @param distributionCount {@code distributioncount}
 * @param defaultEmailValidatedCountdown {@code defaultemailvalidatedcountdown}
 * @param pageLockMillis {@code pagelockmillis}, a {@code double precision} column
 * @param pageLockWarningMillis {@code pagelockwarningmillis}, likewise
 * @param helpTopicTemplate {@code helptopictemplate}
 * @param signIn         how its users sign in
 * @param storage        its recorded storage allocation
 * @param organization   its root organization
 * @param created        when it was created, as its monolith row recorded it
 * @param modified       when it was last edited
 * @param creator        who created it; absent for the system or for a stamp that names nobody who
 *                       still exists
 * @param modifier       who last edited it, on the same terms
 */
public record TenantRecordResponse(
		TenantKey tenant,
		@Nullable String name,
		@Nullable String displayName,
		@Nullable String shortName,
		@Nullable String mission,
		@Nullable String associationId,
		ResellerKey reseller,
		@Nullable String namespace,
		long flags,
		TenantFeatures features,
		TenantLock lock,
		@Nullable String requiredEndorsement,
		@Nullable String assignedServers,
		@Nullable String formulaServer,
		@Nullable Integer distributionCount,
		int defaultEmailValidatedCountdown,
		@Nullable Double pageLockMillis,
		@Nullable Double pageLockWarningMillis,
		@Nullable String helpTopicTemplate,
		TenantSignIn signIn,
		TenantStorage storage,
		TenantOrganization organization,
		@Nullable LocalDateTime created,
		@Nullable LocalDateTime modified,
		@Nullable TenantActor creator,
		@Nullable TenantActor modifier) {

	/** {@link #name}, as an {@code Optional}. */
	public Optional<String> optName() {
		return Optional.ofNullable(name);
	}

	/** {@link #displayName}, as an {@code Optional}. */
	public Optional<String> optDisplayName() {
		return Optional.ofNullable(displayName);
	}

	/** {@link #shortName}, as an {@code Optional}. */
	public Optional<String> optShortName() {
		return Optional.ofNullable(shortName);
	}

	/** {@link #mission}, as an {@code Optional}. */
	public Optional<String> optMission() {
		return Optional.ofNullable(mission);
	}

	/** {@link #associationId}, as an {@code Optional}. */
	public Optional<String> optAssociationId() {
		return Optional.ofNullable(associationId);
	}

	/** {@link #namespace}, as an {@code Optional}. */
	public Optional<String> optNamespace() {
		return Optional.ofNullable(namespace);
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
