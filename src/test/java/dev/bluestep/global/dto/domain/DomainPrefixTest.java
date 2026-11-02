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
 * Pins {@link DomainPrefix} and {@link DomainSuffix}: the bare string on the wire both ways; any stored
 * string builds and binds; and each one's request rule, which holds only where a request is validated.
 */
class DomainPrefixTest {

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
	void prefix_writesAndReadsAsTheBareString() {
		assertEquals("\"harbor\"", JSON.writeValueAsString(DomainPrefix.of("harbor")));
		assertEquals(DomainPrefix.of("harbor"), JSON.readValue("\"harbor\"", DomainPrefix.class));
		assertNull(JSON.readValue("null", DomainPrefix.class));
	}

	/** The monolith's {@code SiteDomainPrefix}: labels of {@code [a-z0-9-]}, dots between, 64 in all. */
	@ParameterizedTest
	@ValueSource(strings = {"harbor", "a", "0", "x-1", "a.b", "north.harbor", "b6p-07"})
	void aSitePrefixPassesTheRule(String prefix) {
		assertTrue(DomainPrefix.of(prefix).isValid(), prefix);
		assertEquals(Set.of(), violatedPaths(DomainPrefix.of(prefix)), prefix);
	}

	@Test
	void thePrefixLengthLimits() {
		String label63 = "p".repeat(63);
		assertTrue(DomainPrefix.of(label63).isValid());
		assertTrue(DomainPrefix.of("a." + "p".repeat(62)).isValid(), "64 in all");
		assertFalse(DomainPrefix.of("p".repeat(64)).isValid(), "a label over 63");
		assertFalse(DomainPrefix.of("a.b" + "p".repeat(62)).isValid(), "65 in all");
	}

	@ParameterizedTest
	@ValueSource(strings = {"", " ", "Harbor", " harbor", "harbor ", "-harbor", "harbor-", "a..b", ".a", "a.",
		"a_b", "harbor.example.org/x", "ä"})
	void anyOtherPrefixBuildsAndBindsAsStored_butFailsTheRule(String prefix) {
		assertEquals(prefix, DomainPrefix.of(prefix).value());
		assertEquals(DomainPrefix.of(prefix), JSON.readValue(JSON.writeValueAsString(prefix), DomainPrefix.class));
		assertFalse(DomainPrefix.of(prefix).isValid(), prefix);
		assertEquals(Set.of("value"), violatedPaths(DomainPrefix.of(prefix)), prefix);
	}

	@Test
	void prefix_printsAsStored() {
		assertEquals(" Harbor", DomainPrefix.of(" Harbor").toString());
	}

	@Test
	void suffix_writesAndReadsAsTheBareString_blankIncluded() {
		assertEquals("\"sites.example.test\"", JSON.writeValueAsString(DomainSuffix.of("sites.example.test")));
		assertEquals("\"\"", JSON.writeValueAsString(DomainSuffix.of("")));
		assertEquals(DomainSuffix.of(""), JSON.readValue("\"\"", DomainSuffix.class));
		assertNull(JSON.readValue("null", DomainSuffix.class));
	}

	@ParameterizedTest
	@ValueSource(strings = {"", "example.test", "sites.example.test", "a.b"})
	void aBlankOrHostSuffixPassesTheRule(String suffix) {
		assertTrue(DomainSuffix.of(suffix).isValid(), suffix);
		assertEquals(Set.of(), violatedPaths(DomainSuffix.of(suffix)), suffix);
	}

	@ParameterizedTest
	@ValueSource(strings = {" ", "localhost", "Example.test", " example.test", "10.0.0.1", "https://example.test",
		"-a.example.test"})
	void anyOtherSuffixBuildsAndBindsAsStored_failsIsValid_butNoBeanConstraint(String suffix) {
		assertEquals(DomainSuffix.of(suffix), JSON.readValue(JSON.writeValueAsString(suffix), DomainSuffix.class));
		assertFalse(DomainSuffix.of(suffix).isValid(), suffix);
		assertEquals(Set.of(), violatedPaths(DomainSuffix.of(suffix)), "the shape is a per-version server rule: " + suffix);
	}

	/** The one bean constraint is the column's width, counted in code points as {@code varchar} counts. */
	@Test
	void suffix_isBoundedAtTheColumnWidth() {
		assertEquals(Set.of(), violatedPaths(DomainSuffix.of("😀".repeat(DomainSuffix.MAX_LENGTH))));
		assertEquals(Set.of("value"), violatedPaths(DomainSuffix.of("x".repeat(DomainSuffix.MAX_LENGTH + 1))));
	}

	@Test
	void aBlankSuffixIsNone() {
		assertTrue(DomainSuffix.of("").isNone());
		assertTrue(DomainSuffix.of(" ").isNone());
		assertFalse(DomainSuffix.of("example.test").isNone());
	}
}
