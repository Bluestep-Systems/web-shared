package dev.bluestep.global.dto.fleet;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.List;

import org.junit.jupiter.api.Test;

import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

/**
 * Pins {@link NamespaceName}: the bare string on the wire both ways, and the DNS-label rule it holds a
 * name to, whether built in code or bound from JSON.
 */
class NamespaceNameTest {

	private static final ObjectMapper JSON = JsonMapper.builder().build();

	/** Names that are not DNS labels, one per way of failing the rule. */
	private static final List<String> INVALID = List.of("", " ", "Web-a", "web_a", "-web", "web-", "web.a",
			"web a", "n".repeat(NamespaceName.MAX_LENGTH + 1));

	@Test
	void writesAsTheBareString() {
		assertEquals("\"b6p-07\"", JSON.writeValueAsString(NamespaceName.of("b6p-07")));
	}

	@Test
	void readsFromTheBareString() {
		assertEquals(NamespaceName.of("b6p-07"), JSON.readValue("\"b6p-07\"", NamespaceName.class));
	}

	@Test
	void anExplicitNullBindsAsAbsent() {
		assertNull(JSON.readValue("null", NamespaceName.class));
	}

	@Test
	void acceptsEveryDnsLabelShape() {
		for (String name : List.of("a", "7", "b6p-07", "web--a", "n".repeat(NamespaceName.MAX_LENGTH))) {
			assertEquals(name, NamespaceName.of(name).value(), name);
		}
	}

	@Test
	void refusesANameThatIsNotADnsLabel() {
		for (String name : INVALID) {
			assertThrows(IllegalArgumentException.class, () -> NamespaceName.of(name), name);
		}
	}

	@Test
	void refusesANameThatIsNotADnsLabelWhenBound() {
		for (String name : INVALID) {
			String json = JSON.writeValueAsString(name);
			assertThrows(JacksonException.class, () -> JSON.readValue(json, NamespaceName.class), name);
		}
	}

	@Test
	void aResponseCarryingOneBindsAndRefusesThroughIt() {
		String body = "{\"name\":\"%s\",\"legacy\":false,\"isPrivate\":false,\"modified\":false,\"version\":0}";

		assertEquals(NamespaceName.of("web-a"),
				JSON.readValue(body.formatted("web-a"), FleetNamespaceResponse.class).name());
		assertThrows(JacksonException.class,
				() -> JSON.readValue(body.formatted("Web_A"), FleetNamespaceResponse.class));
	}

	@Test
	void printsAsTheName() {
		assertEquals("b6p-07", NamespaceName.of("b6p-07").toString());
	}
}
