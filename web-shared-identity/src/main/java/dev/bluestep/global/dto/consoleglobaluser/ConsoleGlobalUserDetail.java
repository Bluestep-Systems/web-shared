package dev.bluestep.global.dto.consoleglobaluser;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

import org.jspecify.annotations.Nullable;

/**
 * One global user with what the directory's pages leave out (from 6.13.0): the answer of
 * {@code GET /console-global-users/{classid}/{seqnum}}.
 *
 * @param user           the account, as a page lists it
 * @param accessTokens   every access token it owns, live or not, newest first; metadata only
 * @param tenantAccess   every tenant grant it holds, live or lapsed, by tenant id
 * @param historyManager its standing history-manager grant; absent when it holds none
 * @param catalogScope   its recorded catalog scope; absent when none is recorded
 */
public record ConsoleGlobalUserDetail(
		ConsoleGlobalUser user,
		List<ConsoleAccessToken> accessTokens,
		List<ConsoleTenantAccess> tenantAccess,
		@Nullable ConsoleHistoryManagerGrant historyManager,
		@Nullable ConsoleCatalogScope catalogScope) {

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
