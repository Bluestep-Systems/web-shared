package dev.bluestep.global.dto.consoleglobaluser;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import dev.bluestep.global.dto.endorsement.PlatformEndorsement;
import dev.bluestep.global.dto.tenantaccess.GlobalUserKey;
import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

/** The 6.16.0 endorsements read, and the detail it rides on. Every fixture is fictional. */
@DisplayName("6.16.0 console endorsements")
class ConsoleEndorsementsWireTest {

	private static final ObjectMapper JSON = JsonMapper.builder()
			.enable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
			.build();

	private static final GlobalUserKey ALICE = new GlobalUserKey(222_222L, 9_001L);

	private static ConsoleGlobalUserDetail bare() {
		return new ConsoleGlobalUserDetail(new ConsoleGlobalUser(ALICE, null, null, null, null, false, false, null,
				false, null, null, null, null, null, "0", List.of()), List.of(), false, List.of(), false, null, null);
	}

	@Test
	@DisplayName("available endorsements have exactly the documented wire, held in catalog order, and round trip")
	void available() {
		ConsoleEndorsements read = ConsoleEndorsements.available(
				List.of(PlatformEndorsement.TECH, PlatformEndorsement.ENGINEER), List.of("engineer", "CUSTOM_X"));
		ConsoleGlobalUserDetail detail = bare().withEndorsements(read);

		String json = JSON.writeValueAsString(detail);

		assertTrue(json.endsWith(",\"endorsements\":{\"state\":\"AVAILABLE\",\"held\":[\"ENGINEER\",\"TECH\"],"
				+ "\"unrecognized\":[\"engineer\",\"CUSTOM_X\"]}}"), json);
		assertEquals(detail, JSON.readValue(json, ConsoleGlobalUserDetail.class));
		assertTrue(read.isAvailable());
	}

	@Test
	@DisplayName("a detail without endorsements is the 6.13.0 wire byte for byte: the member is not there at all")
	void absentIsNotOnTheWire() {
		String json = JSON.writeValueAsString(bare());

		assertFalse(json.contains("endorsements"), json);
		ConsoleGlobalUserDetail read = JSON.readValue(json, ConsoleGlobalUserDetail.class);
		assertTrue(read.optEndorsements().isEmpty(), "an older web-global's answer reads as absent");
		assertEquals(json, JSON.writeValueAsString(bare().withEndorsements(null)));
	}

	@Test
	@DisplayName("unavailable lists nothing, and a state a newer app adds binds UNKNOWN, never available")
	void unavailableAndUnknown() {
		assertEquals("{\"state\":\"UNAVAILABLE\",\"held\":[],\"unrecognized\":[]}",
				JSON.writeValueAsString(ConsoleEndorsements.unavailable()));
		ConsoleEndorsements future = JSON.readValue("{\"state\":\"PARTIAL\",\"held\":[],\"unrecognized\":[]}",
				ConsoleEndorsements.class);
		assertEquals(ConsoleEndorsements.State.UNKNOWN, future.state());
		assertFalse(future.isAvailable());
		assertEquals(ConsoleEndorsements.State.UNKNOWN,
				JSON.readValue("{\"held\":[],\"unrecognized\":[]}", ConsoleEndorsements.class).state());
		assertThrows(IllegalArgumentException.class,
				() -> new ConsoleEndorsements(ConsoleEndorsements.State.UNAVAILABLE, List.of("TECH"), List.of()));
		assertThrows(RuntimeException.class, () -> JSON.readValue(
				"{\"state\":\"PARTIAL\",\"held\":[\"TECH\"],\"unrecognized\":[]}", ConsoleEndorsements.class));
	}

	@Test
	@DisplayName("an id a newer catalog holds still binds: held ids are strings on the wire")
	void heldIdsAreStrings() {
		ConsoleEndorsements read = JSON.readValue(
				"{\"state\":\"AVAILABLE\",\"held\":[\"SOME_LATER_ID\"],\"unrecognized\":[]}", ConsoleEndorsements.class);
		assertEquals(List.of("SOME_LATER_ID"), read.held());
	}

	@Test
	@DisplayName("a repeated or blank token is refused, and the lists are copies")
	void refusals() {
		assertThrows(IllegalArgumentException.class, () -> ConsoleEndorsements.available(List.of(),
				List.of("x", "x")));
		assertThrows(IllegalArgumentException.class, () -> ConsoleEndorsements.available(
				List.of(PlatformEndorsement.TECH, PlatformEndorsement.TECH), List.of()));
		assertThrows(IllegalArgumentException.class, () -> ConsoleEndorsements.available(List.of(), List.of(" ")));
		assertThrows(UnsupportedOperationException.class,
				() -> ConsoleEndorsements.available(List.of(), List.of("x")).unrecognized().add("y"));
	}
}
