package dev.bluestep.global.dto.tenant;

import java.util.Optional;
import java.util.OptionalInt;

import org.jspecify.annotations.Nullable;

import dev.bluestep.global.dto.basetable.BaseTableKey;
import dev.bluestep.global.dto.constraints.CodePointSize;

import jakarta.validation.constraints.NotNull;

/**
 * A tenant's root organization: what the monolith kept on the tenant's {@code global.organization}
 * row. Its name is the tenant's ({@link TenantRecordResponse#name()}); the two were always equal.
 *
 * <p>The four object references name objects in the tenant's own schema (or, for a default domain,
 * in {@code global.domainname}); web-global stores them and does not resolve them. Each is both halves
 * of a key or absent.</p>
 *
 * <p>From 6.0.0 it is also written, inside {@link TenantSettings}. The string bounds are the widths of
 * the {@code global.organization} columns behind them, and {@code supportEmail} is required because its
 * column is {@code NOT NULL}; Bean Validation applies them only where a request marks this
 * {@code @Valid}.</p>
 *
 * @param description          {@code description}
 * @param storageSpace         {@code storagespace}
 * @param homePageUrl          {@code homepageurl}
 * @param defaultWebInfo       {@code (classiddefaultwebinfo, seqnumdefaultwebinfo)}
 * @param primaryEntities      {@code (classidprimaryentities, seqnumprimaryentities)}
 * @param defaultDomainName    {@code (classiddefaultdomainname, seqnumdefaultdomainname)}
 * @param emailGroup           {@code (classidemailgroup, seqemailgroup)}
 * @param supportEmail         {@code supportemail}; the column's default is the literal {@code blank}
 * @param emailHost            {@code emailhost}
 * @param externalEmailAllowed {@code externalemailallowed}, non-zero read as on
 * @param contactName          {@code contactname}
 * @param contactAddress       {@code contactaddress}
 * @param contactPhone         {@code contactphone}
 * @param contactEmail         {@code contactemail}
 * @param pharmacyId           {@code pharmacyid}
 * @param pharmacyRepId        {@code pharmacyrepid}
 * @param deniedIps            {@code deniedips}, the monolith's list syntax, verbatim
 * @param allowedIps           {@code allowedips}, likewise
 * @param showManageLink       {@code showmanagelink}, non-zero read as on
 * @param showHqLink           {@code showhqlink}, non-zero read as on
 * @param config               {@code config}, the stored {@code jsonb} as JSON text, verbatim
 */
public record TenantOrganization(
		@CodePointSize(max = 4000) @Nullable String description,
		@Nullable Integer storageSpace,
		@CodePointSize(max = 200) @Nullable String homePageUrl,
		@Nullable BaseTableKey defaultWebInfo,
		@Nullable BaseTableKey primaryEntities,
		@Nullable BaseTableKey defaultDomainName,
		@Nullable BaseTableKey emailGroup,
		@NotNull @CodePointSize(max = 50) String supportEmail,
		@CodePointSize(max = 100) @Nullable String emailHost,
		boolean externalEmailAllowed,
		@CodePointSize(max = 100) @Nullable String contactName,
		@CodePointSize(max = 255) @Nullable String contactAddress,
		@CodePointSize(max = 20) @Nullable String contactPhone,
		@CodePointSize(max = 50) @Nullable String contactEmail,
		@CodePointSize(max = 255) @Nullable String pharmacyId,
		@CodePointSize(max = 255) @Nullable String pharmacyRepId,
		@CodePointSize(max = 2000) @Nullable String deniedIps,
		@CodePointSize(max = 2000) @Nullable String allowedIps,
		boolean showManageLink,
		boolean showHqLink,
		@Nullable String config) {

	/** {@link #description}, as an {@code Optional}. */
	public Optional<String> optDescription() {
		return Optional.ofNullable(description);
	}

	/** {@link #storageSpace}, as an {@code OptionalInt}. */
	public OptionalInt optStorageSpace() {
		return storageSpace == null ? OptionalInt.empty() : OptionalInt.of(storageSpace);
	}

	/** {@link #homePageUrl}, as an {@code Optional}. */
	public Optional<String> optHomePageUrl() {
		return Optional.ofNullable(homePageUrl);
	}

	/** {@link #defaultWebInfo}, as an {@code Optional}. */
	public Optional<BaseTableKey> optDefaultWebInfo() {
		return Optional.ofNullable(defaultWebInfo);
	}

	/** {@link #primaryEntities}, as an {@code Optional}. */
	public Optional<BaseTableKey> optPrimaryEntities() {
		return Optional.ofNullable(primaryEntities);
	}

	/** {@link #defaultDomainName}, as an {@code Optional}. */
	public Optional<BaseTableKey> optDefaultDomainName() {
		return Optional.ofNullable(defaultDomainName);
	}

	/** {@link #emailGroup}, as an {@code Optional}. */
	public Optional<BaseTableKey> optEmailGroup() {
		return Optional.ofNullable(emailGroup);
	}

	/** {@link #emailHost}, as an {@code Optional}. */
	public Optional<String> optEmailHost() {
		return Optional.ofNullable(emailHost);
	}

	/** {@link #contactName}, as an {@code Optional}. */
	public Optional<String> optContactName() {
		return Optional.ofNullable(contactName);
	}

	/** {@link #contactAddress}, as an {@code Optional}. */
	public Optional<String> optContactAddress() {
		return Optional.ofNullable(contactAddress);
	}

	/** {@link #contactPhone}, as an {@code Optional}. */
	public Optional<String> optContactPhone() {
		return Optional.ofNullable(contactPhone);
	}

	/** {@link #contactEmail}, as an {@code Optional}. */
	public Optional<String> optContactEmail() {
		return Optional.ofNullable(contactEmail);
	}

	/** {@link #pharmacyId}, as an {@code Optional}. */
	public Optional<String> optPharmacyId() {
		return Optional.ofNullable(pharmacyId);
	}

	/** {@link #pharmacyRepId}, as an {@code Optional}. */
	public Optional<String> optPharmacyRepId() {
		return Optional.ofNullable(pharmacyRepId);
	}

	/** {@link #deniedIps}, as an {@code Optional}. */
	public Optional<String> optDeniedIps() {
		return Optional.ofNullable(deniedIps);
	}

	/** {@link #allowedIps}, as an {@code Optional}. */
	public Optional<String> optAllowedIps() {
		return Optional.ofNullable(allowedIps);
	}

	/** {@link #config}, as an {@code Optional}. */
	public Optional<String> optConfig() {
		return Optional.ofNullable(config);
	}
}
