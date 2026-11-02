package dev.bluestep.global.dto.domain;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Set;
import java.util.stream.Collectors;

import org.hibernate.validator.messageinterpolation.ParameterMessageInterpolator;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

/**
 * Pins {@link DomainHost}: the bare string on the wire both ways; any stored string builds and binds; the
 * host rule holds only where a request is validated; and its one normalization, which every host
 * comparison goes through.
 */
class DomainHostTest {

	private static final ObjectMapper JSON = JsonMapper.builder().build();

	private static final Validator VALIDATOR = validator();

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
	void writesAndReadsAsTheBareString() {
		assertEquals("\"harbor.example.org\"", JSON.writeValueAsString(DomainHost.of("harbor.example.org")));
		assertEquals(DomainHost.of("harbor.example.org"), JSON.readValue("\"harbor.example.org\"", DomainHost.class));
		assertNull(JSON.readValue("null", DomainHost.class));
	}

	@ParameterizedTest
	@ValueSource(strings = {"harbor.example.org", "a.b", "x-1.example.org", "123.example.org", "a1.example.c0m"})
	void aServableHostPassesTheRule(String host) {
		assertTrue(DomainHost.of(host).isServable(), host);
		assertEquals(Set.of(), violatedPaths(DomainHost.of(host)), host);
	}

	@ParameterizedTest
	@ValueSource(strings = {"Harbor.example.org", " harbor.example.org", "localhost", "10.0.0.1", "-a.example.org",
		"a_b.example.org", "", " "})
	void anUnservableHostBuildsAndBindsAsStored_butFailsTheRule(String host) {
		assertEquals(host, DomainHost.of(host).value());
		assertEquals(DomainHost.of(host), JSON.readValue(JSON.writeValueAsString(host), DomainHost.class));
		assertFalse(DomainHost.of(host).isServable(), host);
		assertEquals(Set.of("value"), violatedPaths(DomainHost.of(host)), host);
	}

	@Test
	void normalizesByStrippingAndLowerCasing() {
		assertEquals(DomainHost.of("harbor.example.org"), DomainHost.of("  Harbor.EXAMPLE.org\t").normalized());
		assertEquals(DomainHost.of("harbor.example.org"), DomainHost.of("harbor.example.org").normalized());
	}

	@Test
	void sameHostAs_comparesNormalizedForms() {
		assertTrue(DomainHost.of(" Harbor.example.org").sameHostAs(DomainHost.of("harbor.example.org")));
		assertTrue(DomainHost.of("harbor.example.org").sameHostAs(DomainHost.of("HARBOR.example.org ")));
		assertFalse(DomainHost.of("harbor.example.org").sameHostAs(DomainHost.of("harbor.example.com")));
	}

	@Test
	void printsAsTheHost() {
		assertEquals(" Harbor.example.org", DomainHost.of(" Harbor.example.org").toString());
	}
}
