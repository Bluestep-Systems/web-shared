package dev.bluestep.global.dto.reseller;

import java.util.Optional;
import java.util.OptionalLong;

import org.jspecify.annotations.Nullable;

import dev.bluestep.global.dto.constraints.CodePointSize;
import dev.bluestep.global.dto.tenant.TenantActor;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

/**
 * Editing a reseller: the twelve branding columns of {@code global.reseller} that the monolith's own
 * console can change, plus the name its {@code global.basetable} row is displayed and ordered by.
 *
 * <h2>The whole set, not a patch</h2>
 *
 * <p>Every column is named on every call, and an absent value clears it. That is what the
 * monolith's generic update does — the console screen posts the whole form — and it is what makes the
 * call idempotent: sending it twice leaves the same branding, and a caller re-asserting a set it
 * assembled cannot leave a stale value standing because it forgot to mention it.</p>
 *
 * <p>Clearing a URL is a real operation rather than an accident to guard against: the monolith falls
 * back to its own hosted default for every one of these when a reseller supplies none, so an empty
 * value means "use the default" rather than "show nothing".</p>
 *
 * <h2>{@code flags} is the one exception, and why it has to be</h2>
 *
 * <p>An absent {@code flags} leaves the stored word <b>standing</b>, where an absent value anywhere
 * else on this record clears its column. That is a deliberate break in the rule above, and it is
 * forced by how this shape is served: the shared records are bound by <em>every</em> live version's
 * controllers, not by frozen per-version copies, so a component added here reaches tags that shipped
 * before it existed. A caller on such a tag cannot name a field it has never heard of, so under the
 * whole-set rule its ordinary save would post no {@code flags} and silently clear every bit —
 * turning a switch a reseller deliberately set back off, at the next unrelated edit to its branding.
 * Widening what an older tag accepts is safe; letting it destroy a value it cannot see is not.</p>
 *
 * <p>The asymmetry is narrower than it looks. Clearing a URL is a real request because a cleared URL
 * has a defined meaning — fall back to the hosted default. Clearing the switch word has no such
 * meaning: zero is not "no preference", it is a specific configuration, so "say nothing" and "set
 * every switch off" are genuinely different requests and a shape that cannot tell them apart is the
 * defect. A caller that means the second sends {@code 0}, which is still one call and still
 * idempotent.</p>
 *
 * <h2>Why the display name is required, where every URL is optional</h2>
 *
 * <p>{@code displayName} is the one field here that is not branding. It lives on {@code basetable}
 * rather than on {@code reseller}, and the console screen marks it required because a reseller with no
 * name cannot be picked out of the list that six screens render. Declaring it {@code @NotBlank} rather
 * than {@code @Nullable} is what keeps the whole-set rule from being dangerous for it: on this record an
 * omitted value clears its column, so a nullable display name would let a caller that simply
 * did not know about the field erase the name of every reseller it saved. A caller that omits it is
 * refused instead.</p>
 *
 * <h2>{@code actor}: who is making the edit (from 5.5.0)</h2>
 *
 * <p>The edit's actor, in the actor model's three cases: a global user, a user of one tenant, or
 * none (absent). The monolith's {@code ResellerGatewayDAO} sends the acting global super, so the
 * reseller row records who changed it. Absent is no actor rather than "leave the stored one": a caller
 * on a tag that predates the component (the shared records are bound by every live version's
 * controllers) is recorded as no actor, which is what the row recorded for it before. Nothing reads it
 * before web-global's reseller-actor changeset consumes it.</p>
 *
 * <h2>What is not here</h2>
 *
 * <p>No key: the reseller being edited is the path.</p>
 *
 * <p>Creating one is {@link ResellerCreateRequest}, from 5.1.0; a refused delete answers with a
 * {@link ResellerDeleteRefusal}.</p>
 *
 * <h2>Constraints</h2>
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
 * @param privacyPageUrl the privacy policy to render, or absent for the monolith's own
 * @param termsPageUrl   the terms to render, or absent for the monolith's own
 * @param icons          the icon set, in full. Required as an object even when every icon inside it
 *                       is empty, so that "no icons" is something the caller says rather than
 *                       something inferred from a missing field.
 * @param flags          the switch word to store, or absent to leave the stored one alone. The one
 *                       component here that an absent value does not clear — see above.
 * @param actor          who is making the edit, or absent for no actor
 */
public record ResellerUpdateRequest(
		@NotBlank @CodePointSize(max = 1000) String displayName,
		@CodePointSize(max = 256) @Nullable String supportEmail,
		@CodePointSize(max = 256) @Nullable String defaultDomain,
		@Nullable String privacyPageUrl,
		@Nullable String termsPageUrl,
		@NotNull ResellerIcons icons,
		@Nullable Long flags,
		@Nullable TenantActor actor
) {

	/** {@link #supportEmail}, as an {@code Optional}. */
	public Optional<String> optSupportEmail() {
		return Optional.ofNullable(supportEmail);
	}

	/** {@link #defaultDomain}, as an {@code Optional}. */
	public Optional<String> optDefaultDomain() {
		return Optional.ofNullable(defaultDomain);
	}

	/** {@link #privacyPageUrl}, as an {@code Optional}. */
	public Optional<String> optPrivacyPageUrl() {
		return Optional.ofNullable(privacyPageUrl);
	}

	/** {@link #termsPageUrl}, as an {@code Optional}. */
	public Optional<String> optTermsPageUrl() {
		return Optional.ofNullable(termsPageUrl);
	}

	/** {@link #flags}, as an {@code OptionalLong}. */
	public OptionalLong optFlags() {
		return flags == null ? OptionalLong.empty() : OptionalLong.of(flags);
	}

	/** {@link #actor}, as an {@code Optional}. */
	public Optional<TenantActor> optActor() {
		return Optional.ofNullable(actor);
	}
}
