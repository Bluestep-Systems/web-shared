package dev.bluestep.global.dto.consoleglobaluser;

import java.util.EnumSet;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

import org.jspecify.annotations.Nullable;

import dev.bluestep.global.dto.globaluser.CatalogScopeKind;
import dev.bluestep.global.dto.tenantaccess.ResellerKey;

/**
 * What the Console's global-user directory is narrowed by (from 6.17.0), beside {@code search} and
 * {@code includeDisabled}: the query parameters of {@code GET /console-global-users}, in one shape the gateway sends
 * and web-global reads.
 *
 * <p>Every member narrows, and they AND together (and with the search and the disabled switch); the values of
 * {@link #kind} are OR'd with each other. A {@code noX} switch set to {@code true} asks for the accounts that hold
 * no X; set to {@code false} it asks for nothing. Asking for X and for no X at once is a contradiction and refused
 * here ({@link IllegalArgumentException}), as is an empty {@link #kind}, so neither side can build one.</p>
 *
 * @param kind            the kinds listed (any of them); empty is every kind
 * @param reseller        only accounts whose reseller key is this one, named or not
 * @param noReseller      only accounts with no reseller key
 * @param linkedProvider  only accounts holding at least one linked identity of this provider
 * @param noLinkedAccount only accounts holding no linked identity
 * @param catalogScope    only accounts whose recorded catalog scope is this kind
 * @param noCatalogScope  only accounts with no recorded catalog scope
 */
public record GlobalUserFilter(Set<GlobalUserKind> kind, @Nullable ResellerKey reseller, boolean noReseller,
		@Nullable LinkedAccountProvider linkedProvider, boolean noLinkedAccount, @Nullable CatalogScopeKind catalogScope,
		boolean noCatalogScope) {

	/** The filter that narrows nothing. */
	public static final GlobalUserFilter NONE = new GlobalUserFilter(Set.of(), null, false, null, false, null, false);

	/** The query parameter names, one spelling for both sides. */
	public static final String KIND = "kind";
	public static final String RESELLER = "reseller";
	public static final String NO_RESELLER = "noReseller";
	public static final String LINKED_PROVIDER = "linkedProvider";
	public static final String NO_LINKED_ACCOUNT = "noLinkedAccount";
	public static final String CATALOG_SCOPE = "catalogScope";
	public static final String NO_CATALOG_SCOPE = "noCatalogScope";

	/** Copies the kinds, requires every member, and refuses a contradiction. */
	public GlobalUserFilter {
		Objects.requireNonNull(kind, "kind");
		kind = kind.isEmpty() ? Set.of() : Set.copyOf(EnumSet.copyOf(kind));
		if (reseller != null && noReseller) {
			throw new IllegalArgumentException("reseller and noReseller contradict each other");
		}
		if (linkedProvider != null && noLinkedAccount) {
			throw new IllegalArgumentException("linkedProvider and noLinkedAccount contradict each other");
		}
		if (catalogScope != null && noCatalogScope) {
			throw new IllegalArgumentException("catalogScope and noCatalogScope contradict each other");
		}
	}

	/** {@link #reseller}, as an {@code Optional}. */
	public Optional<ResellerKey> optReseller() {
		return Optional.ofNullable(reseller);
	}

	/** {@link #linkedProvider}, as an {@code Optional}. */
	public Optional<LinkedAccountProvider> optLinkedProvider() {
		return Optional.ofNullable(linkedProvider);
	}

	/** {@link #catalogScope}, as an {@code Optional}. */
	public Optional<CatalogScopeKind> optCatalogScope() {
		return Optional.ofNullable(catalogScope);
	}

	/** Whether any member narrows the directory. */
	public boolean constraining() {
		return !kind.isEmpty() || reseller != null || noReseller || linkedProvider != null || noLinkedAccount
				|| catalogScope != null || noCatalogScope;
	}

	/** {@code key} as the one id spelling of a reseller, {@code classid:seqnum}. */
	public static String spell(ResellerKey key) {
		return key.classid() + ":" + key.seqnum();
	}

	/** A reseller id read back; empty for anything that is not {@code classid:seqnum}. */
	public static Optional<ResellerKey> parseReseller(String id) {
		int colon = id.indexOf(':');
		if (colon < 1 || colon == id.length() - 1) {
			return Optional.empty();
		}
		try {
			return Optional.of(new ResellerKey(Long.parseLong(id.substring(0, colon)),
					Long.parseLong(id.substring(colon + 1))));
		}
		catch (NumberFormatException _) {
			return Optional.empty();
		}
	}
}
