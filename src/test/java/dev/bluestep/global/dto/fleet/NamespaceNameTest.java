package dev.bluestep.global.dto.fleet;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import org.hibernate.validator.messageinterpolation.ParameterMessageInterpolator;
import org.junit.jupiter.api.Test;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

/**
 * Pins {@link NamespaceName}: the bare string on the wire both ways; any stored string builds and
 * binds, so one bad row cannot fail a read; and the DNS-label rule holds only where a request is
 * validated.
 */
class NamespaceNameTest {

	private static final ObjectMapper JSON = JsonMapper.builder().build();

	private static final Validator VALIDATOR = validator();

	/** Names that are not DNS labels, one per way of failing the rule. */
	private static final List<String> INVALID = List.of("", " ", "Web-a", "web_a", "-web", "web-", "web.a",
			"web a", "n".repeat(NamespaceName.MAX_LENGTH + 1));

	/** DNS labels at each edge of the rule. */
	private static final List<String> VALID = List.of("a", "7", "b6p-07", "web--a",
			"n".repeat(NamespaceName.MAX_LENGTH));

	private static Validator validator() {
		try (ValidatorFactory factory = Validation.byDefaultProvider().configure()
				.messageInterpolator(new ParameterMessageInterpolator())
				.buildValidatorFactory()) {
			return factory.getValidator();
		}
	}

	private static Set<String> violatedPaths(Object bean) {
		return VALIDATOR.validate(bean).stream()
				.map(ConstraintViolation::getPropertyPath)
				.map(Object::toString)
				.collect(Collectors.toUnmodifiableSet());
	}

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
		for (String name : VALID) {
			assertTrue(NamespaceName.of(name).isKubernetesName(), name);
			assertEquals(Set.of(), violatedPaths(NamespaceName.of(name)), name);
		}
	}

	@Test
	void aNameThatIsNotADnsLabel_buildsAndBindsAsStored() {
		for (String name : INVALID) {
			assertEquals(name, NamespaceName.of(name).value(), name);
			assertEquals(NamespaceName.of(name), JSON.readValue(JSON.writeValueAsString(name), NamespaceName.class),
					name);
			assertFalse(NamespaceName.of(name).isKubernetesName(), name);
		}
	}

	@Test
	void aNameThatIsNotADnsLabel_failsValidation() {
		for (String name : INVALID) {
			assertEquals(Set.of("value"), violatedPaths(NamespaceName.of(name)), name);
		}
	}

	@Test
	void aResponseCarryingAStoredNameThatIsNotADnsLabel_roundTrips() {
		FleetNamespaceResponse stored = new FleetNamespaceResponse(NamespaceName.of("Web_A"), null, true, false,
				false, 3L);

		String json = JSON.writeValueAsString(stored);

		assertTrue(json.contains("\"name\":\"Web_A\""), json);
		assertEquals(stored, JSON.readValue(json, FleetNamespaceResponse.class));
	}

	@Test
	void printsAsTheName() {
		assertEquals("b6p-07", NamespaceName.of("b6p-07").toString());
	}
}
