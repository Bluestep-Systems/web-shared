package dev.bluestep.global.dto.consoleglobaluser;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

import org.jspecify.annotations.Nullable;

/**
 * One global user with what the directory's pages leave out (from 6.13.0): the answer of
 * {@code GET /console-global-users/{classid}/{seqnum}}.
 *
 * <p>Bounded (U1 review): the tokens are every unexpired one it owns plus the {@value #RECENT_EXPIRED_TOKENS} that
 * expired most recently, and the grants the first {@value #MAX_TENANT_ACCESS} by tenant id. Each list says whether
 * anything was left out.</p>
 *
 * @param user                  the account, as a page lists it
 * @param accessTokens          its unexpired access tokens and its most recently expired ones, newest first;
 *                              metadata only
 * @param accessTokensTruncated whether older expired tokens were left out
 * @param tenantAccess          its tenant grants, live or lapsed, by tenant id
 * @param tenantAccessTruncated whether grants past the first {@value #MAX_TENANT_ACCESS} were left out
 * @param historyManager        its standing history-manager grant; absent when it holds none
 * @param catalogScope          its recorded catalog scope; absent when none is recorded
 */
public record ConsoleGlobalUserDetail(
		ConsoleGlobalUser user,
		List<ConsoleAccessToken> accessTokens,
		boolean accessTokensTruncated,
		List<ConsoleTenantAccess> tenantAccess,
		boolean tenantAccessTruncated,
		@Nullable ConsoleHistoryManagerGrant historyManager,
		@Nullable ConsoleCatalogScope catalogScope) {

	/** How many expired tokens a detail carries at most: the most recently expired. */
	public static final int RECENT_EXPIRED_TOKENS = 20;
	/** How many tenant grants a detail carries at most: the first by tenant id. */
	public static final int MAX_TENANT_ACCESS = 500;

	/** Requires the account and copies the lists. */
	public ConsoleGlobalUserDetail {
		Objects.requireNonNull(user, "user");
		accessTokens = List.copyOf(accessTokens);
		tenantAccess = List.copyOf(tenantAccess);
	}

	/** {@link #historyManager}, as an {@code Optional}. */
	public Optional<ConsoleHistoryManagerGrant> optHistoryManager() {
		return Optional.ofNullable(historyManager);
	}

	/** {@link #catalogScope}, as an {@code Optional}. */
	public Optional<ConsoleCatalogScope> optCatalogScope() {
		return Optional.ofNullable(catalogScope);
	}
}
