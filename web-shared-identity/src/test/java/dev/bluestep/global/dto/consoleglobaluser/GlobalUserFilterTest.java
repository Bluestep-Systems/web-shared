package dev.bluestep.global.dto.consoleglobaluser;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Optional;
import java.util.Set;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import dev.bluestep.global.dto.globaluser.CatalogScopeKind;
import dev.bluestep.global.dto.tenantaccess.ResellerKey;

/** The 6.17.0 directory filter. Every fixture is fictional. */
@DisplayName("6.17.0 global-user directory filter")
class GlobalUserFilterTest {

	private static final ResellerKey RESELLER = new ResellerKey(333_333L, 7L);

	private static GlobalUserFilter with(ResellerKey reseller, boolean noReseller,
			LinkedAccountProvider provider, boolean noLinked, CatalogScopeKind scope,
			boolean noScope) {
		return new GlobalUserFilter(Set.of(), reseller, noReseller, provider, noLinked, scope, noScope);
	}

	@Test
	@DisplayName("the empty filter narrows nothing, and every member alone does")
	void constraining() {
		assertFalse(GlobalUserFilter.NONE.constraining());
		assertTrue(new GlobalUserFilter(Set.of(GlobalUserKind.USER), null, false, null, false,
				null, false).constraining());
		assertTrue(with(RESELLER, false, null, false, null, false).constraining());
		assertTrue(with(null, true, null, false, null, false).constraining());
		assertTrue(with(null, false, LinkedAccountProvider.GOOGLE, false, null,
				false).constraining());
		assertTrue(with(null, false, null, true, null, false).constraining());
		assertTrue(with(null, false, null, false, CatalogScopeKind.FLEET, false)
				.constraining());
		assertTrue(with(null, false, null, false, null, true).constraining());
	}

	@Test
	@DisplayName("asking for X and for no X is refused, for each pair")
	void contradictions() {
		assertThrows(IllegalArgumentException.class,
				() -> with(RESELLER, true, null, false, null, false));
		assertThrows(IllegalArgumentException.class, () -> with(null, false,
				LinkedAccountProvider.MICROSOFT, true, null, false));
		assertThrows(IllegalArgumentException.class, () -> with(null, false, null, false,
				CatalogScopeKind.VOUCHED, true));
	}

	@Test
	@DisplayName("a super with no reseller key is a fleet super, with one a reseller super, and a non-super a user")
	void kind() {
		assertEquals(GlobalUserKind.FLEET_SUPER, GlobalUserKind.of(true, false));
		assertEquals(GlobalUserKind.RESELLER_SUPER, GlobalUserKind.of(true, true));
		assertEquals(GlobalUserKind.USER, GlobalUserKind.of(false, false));
		assertEquals(GlobalUserKind.USER, GlobalUserKind.of(false, true));
	}

	@Test
	@DisplayName("a reseller id is classid:seqnum and nothing else")
	void resellerId() {
		assertEquals("333333:7", GlobalUserFilter.spell(RESELLER));
		assertEquals(Optional.of(RESELLER), GlobalUserFilter.parseReseller("333333:7"));
		for (String bad : new String[] {"", "7", ":7", "7:", "a:b", "1:2:3", "1: 2", "99999999999999999999:1"}) {
			assertEquals(Optional.empty(), GlobalUserFilter.parseReseller(bad), bad);
		}
	}

	@Test
	@DisplayName("the kinds are copied, so a later change to the caller's set cannot change the filter")
	void copies() {
		Set<GlobalUserKind> kinds = new java.util.HashSet<>(Set.of(GlobalUserKind.USER));
		GlobalUserFilter filter = new GlobalUserFilter(kinds, null, false, null, false,
				null, false);
		kinds.add(GlobalUserKind.FLEET_SUPER);
		assertEquals(Set.of(GlobalUserKind.USER), filter.kind());
	}
}
