package dev.bluestep.global.dto.reseller;

import java.util.Optional;

import dev.bluestep.global.dto.tenantaccess.ResellerKey;

/**
 * A reseller as the monolith holds it.
 *
 * <p>Read-only. These rows are white-label branding the monolith renders and web-global has no say
 * in; it maps them because {@code global_user_scope} names a reseller and a name that means nothing
 * has to be rejectable — a classification carrying a key that does not exist scopes its user to no
 * tenants at all, which reads to them exactly like having no access.</p>
 *
 * <p>There is no name here, because {@code global.reseller} has no name column — the one the monolith
 * shows and sorts by lives on the row's {@code global.basetable} entry. {@code defaultDomain} is the
 * closest thing to a human label this table offers. {@link ResellerRecordResponse} is the shape that
 * carries the real one; this record is left exactly as 4.0.0 and 4.1.0 promised it, which is why the
 * name arrives beside it rather than inside it.</p>
 *
 * @param reseller     the key, in the spelling a classification uses
 * @param supportEmail where this reseller's users are told to write
 * @param defaultDomain the host its tenants front by default
 * @param privacyPageUrl the privacy policy it renders
 * @param termsPageUrl   the terms it renders
 * @param icons          its icon set
 */
public record ResellerResponse(
		ResellerKey reseller,
		Optional<String> supportEmail,
		Optional<String> defaultDomain,
		Optional<String> privacyPageUrl,
		Optional<String> termsPageUrl,
		ResellerIcons icons
) {
}
