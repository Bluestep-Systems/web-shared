package dev.bluestep.global.dto;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

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
 *
 * <p>Jackson 3 only since 5.0.0: the Jackson 2 half and the check that both families agreed were
 * retired with web-global's Jackson 2 msgpack converter, the last mapper of that family to bind
 * these shapes.</p>
 */
@DisplayName("global user scope wire shapes")
class GlobalUserScopeWireTest {

	/**
	 * Jackson 3 with {@code FAIL_ON_UNKNOWN_PROPERTIES} switched on. Jackson 3 ships it off, and with it
	 * off a leaked constraint getter reads back without complaint, so the round-trip assertions would
	 * pass with the {@code @JsonIgnore} removed. Strict is what makes them about the leak.
	 */
	private static final ObjectMapper JACKSON3 = JsonMapper.builder()
			.enable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
			.build();

	@Test
	@DisplayName("no constraint method appears as a JSON property")
	void constraintMethodsAreNotSerialized() throws Exception {
		List<String> payloads = List.of(
				JACKSON3.writeValueAsString(fleetRequest()),
				JACKSON3.writeValueAsString(resellerRequest()),
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
			assertEquals(fleetRequest(), JACKSON3.readValue(
					JACKSON3.writeValueAsString(fleetRequest()), GlobalUserScopeRequest.class));
			assertEquals(resellerRequest(), JACKSON3.readValue(
					JACKSON3.writeValueAsString(resellerRequest()), GlobalUserScopeRequest.class));
			assertEquals(entry(), JACKSON3.readValue(
					JACKSON3.writeValueAsString(entry()), GlobalUserScopeEntry.class));
		});
	}

	/** An omitted reseller still binds to empty rather than to null — the compact constructor's job. */
	@Test
	@DisplayName("an omitted reseller binds to empty")
	void omittedResellerBindsToEmpty() throws Exception {
		String json = """
				{"scope":"FLEET","reason":"onboarding","actor":"platform-admin"}""";

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
