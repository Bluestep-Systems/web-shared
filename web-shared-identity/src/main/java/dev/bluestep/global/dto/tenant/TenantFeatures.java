package dev.bluestep.global.dto.tenant;

/**
 * A tenant's on/off switches, each a {@code smallint} column read as the monolith reads it: non-zero
 * is on, zero or absent is off.
 *
 * @param allowNewMembers              {@code allownewmembers}
 * @param memberData                   {@code memberdata}
 * @param allowMembershipRequest       {@code allowmembershiprequest}
 * @param allowLookup                  {@code allowlookup}
 * @param require16DigitId             {@code require16digitid}
 * @param enableRelateAdvancedFeatures {@code enablerelateadvancedfeatures}
 * @param enableChangeHistory          {@code enablechangehistory}: objects in this tenant keep history
 * @param enableObjectStats            {@code enableobjectstats}: objects in this tenant keep statistics
 * @param enableAlerts                 {@code enablealerts}
 * @param showIpFilter                 {@code showipfilter}
 * @param enableTextSearch             {@code enabletextsearch}
 * @param global                       {@code isglobal}: the tenant's objects may be shared fleet-wide
 * @param supportChat                  {@code supportchat}
 */
public record TenantFeatures(
		boolean allowNewMembers,
		boolean memberData,
		boolean allowMembershipRequest,
		boolean allowLookup,
		boolean require16DigitId,
		boolean enableRelateAdvancedFeatures,
		boolean enableChangeHistory,
		boolean enableObjectStats,
		boolean enableAlerts,
		boolean showIpFilter,
		boolean enableTextSearch,
		boolean global,
		boolean supportChat) {
}
