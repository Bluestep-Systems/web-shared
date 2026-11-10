package dev.bluestep.global.dto.selftoken;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import org.jspecify.annotations.Nullable;

import com.fasterxml.jackson.annotation.JsonIgnore;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotNull;

/**
 * Minting a token for the presenting token's owner (6.5.0): {@code POST /self-tokens}.
 *
 * <p>Shape only; the rules are web-global's and answer a {@link SelfTokenRefusal}: the alias is trimmed and must be
 * 1-64 characters with no control characters, unique among the owner's tokens ({@code ALIAS_TAKEN}); the scopes
 * must be non-empty vocabulary spellings ({@code INVALID_REQUEST} naming the first that is not) and each held by
 * the presenting token ({@code SCOPE_ESCALATION}); see {@link SelfTokenExpiry} for the lifetime. {@code @Valid}
 * answers 400 for an absent {@link #expiry} or one that disagrees with {@link #expiresAt}.</p>
 *
 * @param alias     the owner's name for the token
 * @param scopes    the scopes it is to hold, {@code TokenScope} wire spellings
 * @param expiry    how its lifetime is chosen
 * @param expiresAt present exactly when {@link #expiry} is {@link SelfTokenExpiry#AT}
 */
public record SelfTokenMintRequest(String alias, List<String> scopes, @NotNull SelfTokenExpiry expiry,
		@Nullable Instant expiresAt) {

	/** Jackson binds an omitted string or array to null whatever the null-marking says: absent means empty. */
	public SelfTokenMintRequest {
		alias = alias == null ? "" : alias;
		scopes = scopes == null ? List.of() : List.copyOf(scopes);
	}

	/** {@link #expiresAt}, as an {@code Optional}. */
	public Optional<Instant> optExpiresAt() {
		return Optional.ofNullable(expiresAt);
	}

	/**
	 * Bean Validation's half of "{@link #expiresAt} is present exactly when {@link #expiry} is {@code AT}".
	 * {@code true} for an absent expiry, which {@code @NotNull} reports.
	 *
	 * <p>{@code @JsonIgnore}: an {@code @AssertTrue} method is a JavaBeans getter, and is no wire property.</p>
	 *
	 * @return whether the two agree
	 */
	@JsonIgnore
	@AssertTrue(message = "expiresAt is given exactly when expiry is AT")
	public boolean isExpiryCoherent() {
		return expiry == null || (expiry == SelfTokenExpiry.AT) == (expiresAt != null);
	}
}
