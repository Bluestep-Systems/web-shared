package dev.bluestep.global.dto.reseller;

import java.util.Optional;
import java.util.OptionalLong;

import org.jspecify.annotations.Nullable;

import com.fasterxml.jackson.annotation.JsonIgnore;

import dev.bluestep.global.dto.basetable.BaseTableKey;
import dev.bluestep.global.dto.constraints.CodePointSize;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

/**
 * Creating a reseller: its {@code global.basetable} row and the {@code global.reseller} row beside it,
 * written together.
 *
 * <h2>The same columns as an edit</h2>
 *
 * <p>Every branding component means exactly what it means on {@link ResellerUpdateRequest}, carries
 * the same bounds, and is required or optional for the same reasons — that record's javadoc is the
 * authority on each. A new reseller is the console's edit form saved for the first time, so the two
 * shapes differ only where creating differs from editing.</p>
 *
 * <h2>Where it differs</h2>
 *
 * <p><b>No key.</b> The service allocates the sequence number from the global schema's reseller
 * sequence, the same one the monolith's per-classid allocator draws from, and the new key comes back on
 * the response. A caller that could name its own number could also name one already taken.</p>
 *
 * <p><b>An absent {@code flags} is zero.</b> On an edit, absence leaves the stored word standing,
 * because there is a stored word to leave. Here there is none, so absence is the column's own default:
 * every switch clear, which is what every switch's clear state is defined to mean.</p>
 *
 * <p><b>{@code creator} is required.</b> {@code basetable} records who created each row and declares
 * those columns {@code NOT NULL}. The service has no notion of which signed-in user is asking — its
 * callers authenticate as a service, not as a person — so the caller that does know says so. It is a
 * key rather than a name because the column is a key.</p>
 *
 * @param creator        who is creating the reseller, written to {@code basetable}'s creator columns
 * @param displayName    as on {@link ResellerUpdateRequest}
 * @param supportEmail   as on {@link ResellerUpdateRequest}
 * @param defaultDomain  as on {@link ResellerUpdateRequest}
 * @param privacyPageUrl as on {@link ResellerUpdateRequest}
 * @param termsPageUrl   as on {@link ResellerUpdateRequest}
 * @param icons          as on {@link ResellerUpdateRequest}
 * @param flags          the switch word to start with, or empty for every switch clear
 */
public record ResellerCreateRequest(
		@NotNull BaseTableKey creator,
		@NotBlank @CodePointSize(max = 1000) String displayName,
		@CodePointSize(max = 256) @Nullable String supportEmail,
		@CodePointSize(max = 256) @Nullable String defaultDomain,
		@Nullable String privacyPageUrl,
		@Nullable String termsPageUrl,
		@NotNull ResellerIcons icons,
		@Nullable Long flags
) {

	/**
	 * Refuses a creator that names no row.
	 *
	 * <p>{@link BaseTableKey} carries no constraints of its own, so nothing else looks inside it. Zero
	 * in either half is the monolith's unset key, and {@code basetable}'s {@code NOT NULL} creator
	 * columns would store it without complaint. Only zero is refused: a negative sequence number is a
	 * real creator, the monolith's own stand-in for "no signed-in user".</p>
	 *
	 * <p>A missing creator passes here and is {@code @NotNull}'s to report, so one mistake is one
	 * violation. {@code @JsonIgnore} because a {@code @AssertTrue} method is a JavaBeans getter, and
	 * without it Jackson would publish {@code creatorKeyed} as a property this record cannot bind
	 * back.</p>
	 */
	@JsonIgnore
	@AssertTrue(message = "creator must name a row, not the unset key")
	public boolean isCreatorKeyed() {
		return creator == null || (creator.classid() != 0 && creator.seqnum() != 0);
	}

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
}
