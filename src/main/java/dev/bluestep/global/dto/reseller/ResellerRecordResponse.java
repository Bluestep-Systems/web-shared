package dev.bluestep.global.dto.reseller;

import java.util.Optional;

import dev.bluestep.global.dto.tenantaccess.ResellerKey;

/**
 * A reseller as the monolith holds it, <b>including the name it is displayed and ordered by</b>.
 *
 * <h2>Why this exists beside {@link ResellerResponse}</h2>
 *
 * <p>{@code ResellerResponse} carries the twelve columns of {@code global.reseller} and nothing else,
 * because that is all that table has: it has no name column at all. The monolith nevertheless renders
 * and sorts resellers by name, and it gets that name from the row's {@code global.basetable} entry —
 * which is why its own listing joins {@code basetable} rather than reading {@code reseller} alone. A
 * response without the display name therefore cannot back a reseller picker, which is what six of the
 * monolith's screens use this data for.</p>
 *
 * <p>A separate record rather than a component added to {@code ResellerResponse}: that record is what
 * the already-released 4.0.0 and 4.1.0 contracts serve, and reshaping it would mean freezing snapshots
 * of it into both of their folders to keep the promise each of them made. The icons and the key are
 * reused as they stand, so what is actually new here is one component.</p>
 *
 * @param reseller       the key, in the spelling a classification uses
 * @param displayName    the name the monolith shows, from the joined {@code basetable} row. Empty
 *                       when that row carries none — the column is nullable, and a reseller without
 *                       one sorts last rather than being hidden.
 * @param supportEmail   where this reseller's users are told to write
 * @param defaultDomain  the host its tenants front by default
 * @param privacyPageUrl the privacy policy it renders
 * @param termsPageUrl   the terms it renders
 * @param icons          its icon set
 */
public record ResellerRecordResponse(
		ResellerKey reseller,
		Optional<String> displayName,
		Optional<String> supportEmail,
		Optional<String> defaultDomain,
		Optional<String> privacyPageUrl,
		Optional<String> termsPageUrl,
		ResellerIcons icons
) {
}
