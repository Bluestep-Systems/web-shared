package dev.bluestep.global.dto.reseller;

import java.util.Optional;

import dev.bluestep.global.dto.constraints.CodePointSize;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

/**
 * Editing a reseller: the twelve branding columns of {@code global.reseller} that the monolith's own
 * console can change, plus the name its {@code global.basetable} row is displayed and ordered by.
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
 * <h2>Why the display name is required, where every URL is optional</h2>
 *
 * <p>{@code displayName} is the one field here that is not branding. It lives on {@code basetable}
 * rather than on {@code reseller}, and the console screen marks it required because a reseller with no
 * name cannot be picked out of the list that six screens render. Declaring it {@code @NotBlank} rather
 * than {@code Optional} is what keeps the whole-set rule from being dangerous for it: on this record an
 * omitted value clears its column, so an {@code Optional} display name would let a caller that simply
 * did not know about the field erase the name of every reseller it saved. A caller that omits it is
 * refused instead.</p>
 *
 * <h2>What is not here</h2>
 *
 * <p>No key: the reseller being edited is the path.</p>
 *
 * <p>There is no create and no delete either. The monolith's own remote refuses deletion outright
 * ({@code "Cannot delete a reseller."}). Creation is absent for a different reason: a reseller arrives
 * as two rows, a {@code basetable} entry whose sequence number comes from the monolith's generic
 * per-classid allocator and a {@code reseller} entry beside it, and this surface owns neither the
 * allocator nor that table. Adding either verb here would be inventing an operation rather than
 * replacing one.</p>
 *
 * <h2>Constraints</h2>
 *
 * <p>The length limits are written as container-element constraints
 * ({@code Optional<@CodePointSize(max = 256) String>}) wherever the component is an {@code Optional},
 * because a constraint placed on an {@code Optional} is checked against the {@code Optional} itself and
 * fails at runtime with "no validator could be found" instead of checking the value inside it.</p>
 *
 * <p>{@link dev.bluestep.global.dto.constraints.CodePointSize} rather than {@code @Size} because the
 * bound is a column width: {@code @Size} counts UTF-16 code units and {@code varchar(n)} counts
 * characters, so a value of supplementary-plane characters would be refused here and accepted by the
 * database. On a whole-record update that is worse than over-strict — a row already holding one could
 * not be edited at all, since the read hands the value back and the write then refuses it.</p>
 *
 * @param displayName    the name the reseller is shown and ordered by. Required, and bounded at the
 *                       width of {@code basetable.displayname}.
 * @param supportEmail   where this reseller's users are told to write
 * @param defaultDomain  the host its tenants front by default. Not a URL — a bare host name.
 * @param privacyPageUrl the privacy policy to render, or empty for the monolith's own
 * @param termsPageUrl   the terms to render, or empty for the monolith's own
 * @param icons          the icon set, in full. Required as an object even when every icon inside it
 *                       is empty, so that "no icons" is something the caller says rather than
 *                       something inferred from a missing field.
 */
public record ResellerUpdateRequest(
		@NotBlank @CodePointSize(max = 1000) String displayName,
		Optional<@CodePointSize(max = 256) String> supportEmail,
		Optional<@CodePointSize(max = 256) String> defaultDomain,
		Optional<String> privacyPageUrl,
		Optional<String> termsPageUrl,
		@NotNull ResellerIcons icons
) {

	/**
	 * Guards direct construction, not the wire.
	 *
	 * <p>{@code OptionalWireContractTest} and its Jackson 3 counterpart pin that an {@code Optional}
	 * component binds to {@link Optional#empty()} for an absent key <em>and</em> an explicit JSON null,
	 * under both Jackson families, with no compact constructor at all — {@code AiTenantConfigRequest}
	 * has none and is the record those tests measure. So nothing here fires for a deserialized request.
	 * What it does catch is Java code passing {@code null} into an {@code @NullMarked} record, which is
	 * a caller bug this normalizes rather than propagates.</p>
	 */
	public ResellerUpdateRequest {
		supportEmail = supportEmail == null ? Optional.empty() : supportEmail;
		defaultDomain = defaultDomain == null ? Optional.empty() : defaultDomain;
		privacyPageUrl = privacyPageUrl == null ? Optional.empty() : privacyPageUrl;
		termsPageUrl = termsPageUrl == null ? Optional.empty() : termsPageUrl;
	}
}
