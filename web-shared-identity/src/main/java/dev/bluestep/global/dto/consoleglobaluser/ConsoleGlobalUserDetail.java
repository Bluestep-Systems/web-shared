package dev.bluestep.global.dto.consoleglobaluser;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

import org.jspecify.annotations.Nullable;

import com.fasterxml.jackson.annotation.JsonInclude;

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
 * @param endorsements          its platform endorsements as web-global read them (from 6.16.0); absent, and then not
 *                              on the wire at all, from a web-global answering a tag before 6.16.0, so the 6.13.0
 *                              shape is unchanged for the tags that shipped it. Present, it may still say they
 *                              could not be read ({@link ConsoleEndorsements.State#UNAVAILABLE})
 */
public record ConsoleGlobalUserDetail(
		ConsoleGlobalUser user,
		List<ConsoleAccessToken> accessTokens,
		boolean accessTokensTruncated,
		List<ConsoleTenantAccess> tenantAccess,
		boolean tenantAccessTruncated,
		@Nullable ConsoleHistoryManagerGrant historyManager,
		@Nullable ConsoleCatalogScope catalogScope,
		@JsonInclude(JsonInclude.Include.NON_NULL) @Nullable ConsoleEndorsements endorsements) {

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

	/** A detail as 6.13.0 to 6.15.0 shape it: without endorsements. */
	public ConsoleGlobalUserDetail(ConsoleGlobalUser user, List<ConsoleAccessToken> accessTokens,
			boolean accessTokensTruncated, List<ConsoleTenantAccess> tenantAccess, boolean tenantAccessTruncated,
			@Nullable ConsoleHistoryManagerGrant historyManager, @Nullable ConsoleCatalogScope catalogScope) {
		this(user, accessTokens, accessTokensTruncated, tenantAccess, tenantAccessTruncated, historyManager,
				catalogScope, null);
	}

	/** This detail with {@code endorsements}; null for none, as a tag before 6.16.0 answers it. */
	public ConsoleGlobalUserDetail withEndorsements(@Nullable ConsoleEndorsements endorsements) {
		return new ConsoleGlobalUserDetail(user, accessTokens, accessTokensTruncated, tenantAccess,
				tenantAccessTruncated, historyManager, catalogScope, endorsements);
	}

	/** {@link #endorsements}, as an {@code Optional}. */
	public Optional<ConsoleEndorsements> optEndorsements() {
		return Optional.ofNullable(endorsements);
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
