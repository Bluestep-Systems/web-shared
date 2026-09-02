package dev.bluestep.global.dto.reseller;

import java.util.Optional;

import dev.bluestep.global.dto.constraints.CodePointSize;

import jakarta.validation.constraints.NotNull;

/**
 * Editing a reseller's branding: the twelve columns of {@code global.reseller} that the monolith's
 * own console can change.
 *
 * <h2>The whole set, not a patch</h2>
 *
 * <p>Every column is named on every call, and an absent {@code Optional} clears it. That is what the
 * monolith's generic update does — the console screen posts the whole form — and it is what makes the
 * call idempotent: sending it twice leaves the same branding, and a caller re-asserting a set it
 * assembled cannot leave a stale value standing because it forgot to mention it.</p>
 *
 * <p>Clearing a URL is a real operation rather than an accident to guard against: the monolith falls
 * back to its own hosted default for every one of these when a reseller supplies none, so an empty
 * value means "use the default" rather than "show nothing".</p>
 *
 * <h2>What is not here</h2>
 *
 * <p>No display name. That lives on the row's {@code global.basetable} entry, which is the monolith's
 * generic object table rather than reseller branding, and nothing on this surface writes it — see
 * {@link ResellerRecordResponse}, which reads it. And no key: the reseller being edited is the path.</p>
 *
 * <p>There is no create and no delete either. The monolith's own remote refuses deletion outright
 * ({@code "Cannot delete a reseller."}), and it has no creation path through this DAO — a reseller
 * arrives with its {@code basetable} row, which this surface does not own. Adding either verb here
 * would be inventing an operation rather than replacing one.</p>
 *
 * <p>The two length limits are written as container-element constraints
 * ({@code Optional<@CodePointSize(max = 256) String>}) rather than on the component, because a constraint
 * placed on an {@code Optional} is checked against the {@code Optional} itself and fails at runtime
 * with "no validator could be found" instead of checking the value inside it. The two columns
 * carrying them are the only ones on this table that are not {@code text}.</p>
 *
 * <p>{@link dev.bluestep.global.dto.constraints.CodePointSize} rather than {@code @Size} because the
 * bound is a column width: {@code @Size} counts UTF-16 code units and {@code varchar(256)} counts
 * characters, so a value of supplementary-plane characters would be refused here and accepted by the
 * database. On a whole-record update that is worse than over-strict — a row already holding one could
 * not be edited at all, since the read hands the value back and the write then refuses it.</p>
 *
 * @param supportEmail   where this reseller's users are told to write
 * @param defaultDomain  the host its tenants front by default. Not a URL — a bare host name.
 * @param privacyPageUrl the privacy policy to render, or empty for the monolith's own
 * @param termsPageUrl   the terms to render, or empty for the monolith's own
 * @param icons          the icon set, in full. Required as an object even when every icon inside it
 *                       is empty, so that "no icons" is something the caller says rather than
 *                       something inferred from a missing field.
 */
public record ResellerUpdateRequest(
		Optional<@CodePointSize(max = 256) String> supportEmail,
		Optional<@CodePointSize(max = 256) String> defaultDomain,
		Optional<String> privacyPageUrl,
		Optional<String> termsPageUrl,
		@NotNull ResellerIcons icons
) {

	/** Jackson binds omitted fields to {@code null} whatever this package's null-marking says. */
	public ResellerUpdateRequest {
		supportEmail = supportEmail == null ? Optional.empty() : supportEmail;
		defaultDomain = defaultDomain == null ? Optional.empty() : defaultDomain;
		privacyPageUrl = privacyPageUrl == null ? Optional.empty() : privacyPageUrl;
		termsPageUrl = termsPageUrl == null ? Optional.empty() : termsPageUrl;
	}
}
