package dev.bluestep.global.dto.myaccount;

import java.time.Instant;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

import org.jspecify.annotations.Nullable;

import dev.bluestep.global.dto.consoleglobaluser.ConsoleAccessToken;
import dev.bluestep.global.dto.consoleglobaluser.ConsoleLinkedAccount;
import dev.bluestep.global.dto.tenantaccess.GlobalUserKey;

/**
 * The signed-in account's own view (from 6.14.0): the answer of {@code GET /my-account}.
 *
 * <p>Bounded as {@code ConsoleGlobalUserDetail} is: every unexpired token plus the
 * {@value dev.bluestep.global.dto.consoleglobaluser.ConsoleGlobalUserDetail#RECENT_EXPIRED_TOKENS} most recently
 * expired, and the first {@value dev.bluestep.global.dto.consoleglobaluser.ConsoleGlobalUserDetail#MAX_TENANT_ACCESS}
 * grants by tenant id; each list says whether anything was left out.</p>
 *
 * @param user                  the account: always the one the acting header named
 * @param firstName             as stored
 * @param lastName              as stored
 * @param username              the login name; the column is nullable
 * @param email                 the account's e-mail address
 * @param emailValidated        whether that address has been confirmed
 * @param passwordExpireStart   when the password-expiry clock started (wall clock of the stamp zone, America/Phoenix);
 *                              absent when none runs. Honoured by the monolith's sign-in only.
 * @param linkedAccounts        the linked identities, each reduced to its issuer, provider and subject hint; absent
 *                              when the stored list cannot be read whole
 * @param accessTokens          its unexpired tokens and its most recently expired ones, newest first; metadata only
 * @param accessTokensTruncated whether older expired tokens were left out
 * @param tenantAccess          the tenants it is vouched for, live or lapsed, by tenant id
 * @param tenantAccessTruncated whether grants past the first were left out
 * @param historyManagerSince   when its standing history-manager grant was given; absent when it holds none
 * @param catalogScope          its recorded catalog scope; absent when none is recorded
 * @param mintableScopes        the scopes a token minted for it now could hold, each on its own (web-global's mint
 *                              policy, the checks every mint runs), in {@code TokenScope}'s order
 */
public record MyAccountResponse(
		GlobalUserKey user,
		@Nullable String firstName,
		@Nullable String lastName,
		@Nullable String username,
		@Nullable String email,
		boolean emailValidated,
		@Nullable LocalDateTime passwordExpireStart,
		@Nullable List<ConsoleLinkedAccount> linkedAccounts,
		List<ConsoleAccessToken> accessTokens,
		boolean accessTokensTruncated,
		List<MyTenantAccess> tenantAccess,
		boolean tenantAccessTruncated,
		@Nullable Instant historyManagerSince,
		@Nullable MyCatalogScope catalogScope,
		List<String> mintableScopes) {

	/** Requires the key, and copies the lists (the linked accounts stay absent when absent). */
	public MyAccountResponse {
		Objects.requireNonNull(user, "user");
		linkedAccounts = linkedAccounts == null ? null : List.copyOf(linkedAccounts);
		accessTokens = accessTokens == null ? List.of() : List.copyOf(accessTokens);
		tenantAccess = tenantAccess == null ? List.of() : List.copyOf(tenantAccess);
		mintableScopes = mintableScopes == null ? List.of() : List.copyOf(mintableScopes);
	}

	/** {@link #firstName}, as an {@code Optional}. */
	public Optional<String> optFirstName() {
		return Optional.ofNullable(firstName);
	}

	/** {@link #lastName}, as an {@code Optional}. */
	public Optional<String> optLastName() {
		return Optional.ofNullable(lastName);
	}

	/** {@link #username}, as an {@code Optional}. */
	public Optional<String> optUsername() {
		return Optional.ofNullable(username);
	}

	/** {@link #email}, as an {@code Optional}. */
	public Optional<String> optEmail() {
		return Optional.ofNullable(email);
	}

	/** {@link #passwordExpireStart}, as an {@code Optional}. */
	public Optional<LocalDateTime> optPasswordExpireStart() {
		return Optional.ofNullable(passwordExpireStart);
	}

	/** {@link #linkedAccounts}, as an {@code Optional}: empty when the stored list could not be read whole. */
	public Optional<List<ConsoleLinkedAccount>> optLinkedAccounts() {
		return Optional.ofNullable(linkedAccounts);
	}

	/** {@link #historyManagerSince}, as an {@code Optional}. */
	public Optional<Instant> optHistoryManagerSince() {
		return Optional.ofNullable(historyManagerSince);
	}

	/** {@link #catalogScope}, as an {@code Optional}. */
	public Optional<MyCatalogScope> optCatalogScope() {
		return Optional.ofNullable(catalogScope);
	}
}
