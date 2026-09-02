package dev.bluestep.global.dto;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jdk8.Jdk8Module;

import dev.bluestep.global.dto.globaluser.CatalogScopeKind;
import dev.bluestep.global.dto.globaluser.GlobalUserScopeEntry;
import dev.bluestep.global.dto.globaluser.GlobalUserScopeRequest;
import dev.bluestep.global.dto.tenantaccess.GlobalUserKey;
import dev.bluestep.global.dto.tenantaccess.ResellerKey;

/**
 * The catalog-scope shapes' wire form — the same property {@link GlobalUserRecordWireTest} pins next
 * door, for the two records that carried the same defect longer.
 *
 * <p>A {@code @AssertTrue} constraint is a JavaBeans getter, so Jackson published
 * {@code resellerConsistentWithScope} as a property of both of these records. It has been on the wire
 * since 4.0.0. Nothing read it and nothing broke visibly, which is exactly why it survived: the
 * constraint kept working, every validation test kept passing, and the extra key only ever showed up
 * in a payload nobody diffed.</p>
 *
 * <p>The consequence that matters is that a record emitting a field it cannot bind back does not
 * round-trip through its own mapper — so this suite asserts the round trip rather than only the
 * absence of the key, because the round trip is the property and the key is just how it broke.</p>
 */
@DisplayName("global user scope wire shapes")
class GlobalUserScopeWireTest {

	private static final ObjectMapper JACKSON2 = new ObjectMapper().registerModule(new Jdk8Module());
	private static final tools.jackson.databind.ObjectMapper JACKSON3 =
			new tools.jackson.databind.json.JsonMapper();

	@Test
	@DisplayName("no constraint method appears as a JSON property, under either Jackson")
	void constraintMethodsAreNotSerialized() throws Exception {
		List<String> payloads = List.of(
				JACKSON2.writeValueAsString(fleetRequest()),
				JACKSON3.writeValueAsString(fleetRequest()),
				JACKSON2.writeValueAsString(resellerRequest()),
				JACKSON2.writeValueAsString(entry()),
				JACKSON3.writeValueAsString(entry()));

		for (String json : payloads) {
			assertFalse(json.contains("resellerConsistentWithScope"),
					"a @AssertTrue getter leaked onto the wire: " + json);
			assertFalse(json.contains("ConsistentWith"), "unexpected derived property: " + json);
		}
	}

	@Test
	@DisplayName("both scope shapes round-trip through their own mapper")
	void scopeShapesRoundTrip() {
		assertDoesNotThrow(() -> {
			assertEquals(fleetRequest(), JACKSON2.readValue(
					JACKSON2.writeValueAsString(fleetRequest()), GlobalUserScopeRequest.class));
			assertEquals(resellerRequest(), JACKSON2.readValue(
					JACKSON2.writeValueAsString(resellerRequest()), GlobalUserScopeRequest.class));
			assertEquals(entry(), JACKSON2.readValue(
					JACKSON2.writeValueAsString(entry()), GlobalUserScopeEntry.class));
		});
	}

	/**
	 * The mappers must agree, because both bind these shapes in production: web-global runs Jackson 3
	 * as its primary and keeps a hand-built Jackson 2 island for msgpack.
	 */
	@Test
	@DisplayName("both Jackson families produce the same scope payload")
	void mappersAgree() throws Exception {
		assertEquals(JACKSON2.writeValueAsString(fleetRequest()),
				JACKSON3.writeValueAsString(fleetRequest()));
		assertEquals(JACKSON2.writeValueAsString(entry()), JACKSON3.writeValueAsString(entry()));
	}

	/** An omitted reseller still binds to empty rather than to null — the compact constructor's job. */
	@Test
	@DisplayName("an omitted reseller binds to empty, under either Jackson")
	void omittedResellerBindsToEmpty() throws Exception {
		String json = """
				{"scope":"FLEET","reason":"onboarding","actor":"platform-admin"}""";

		assertEquals(Optional.empty(),
				JACKSON2.readValue(json, GlobalUserScopeRequest.class).reseller());
		assertEquals(Optional.empty(),
				JACKSON3.readValue(json, GlobalUserScopeRequest.class).reseller());
	}

	private static GlobalUserScopeRequest fleetRequest() {
		return new GlobalUserScopeRequest(CatalogScopeKind.FLEET, Optional.empty(), "onboarding",
				"platform-admin");
	}

	private static GlobalUserScopeRequest resellerRequest() {
		return new GlobalUserScopeRequest(CatalogScopeKind.RESELLER,
				Optional.of(new ResellerKey(111_222L, 1L)), "contractor", "platform-admin");
	}

	private static GlobalUserScopeEntry entry() {
		return new GlobalUserScopeEntry(new GlobalUserKey(222_222L, -50_001L),
				CatalogScopeKind.FLEET, Optional.empty(), "onboarding");
	}
}
