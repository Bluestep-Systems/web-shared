package dev.bluestep.global.dto.knowledgebase;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Set;
import java.util.stream.Collectors;

import org.hibernate.validator.messageinterpolation.ParameterMessageInterpolator;
import org.junit.jupiter.api.Test;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;

/** The request constraints, run through a real Bean Validation provider, so they are shown to fire. */
class KnowledgeBaseValidationTest {

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
	void aCreate_withOnlyAName_isValid() {
		assertTrue(violatedPaths(new GlobalKnowledgeBaseCreateRequest(new KnowledgeBaseName("handbook"), null, null,
				null)).isEmpty());
	}

	@Test
	void aCreate_withNoName_isRefused() {
		assertEquals(Set.of("name"), violatedPaths(new GlobalKnowledgeBaseCreateRequest(null, null, null, null)));
	}

	@Test
	void aTitle_overTheLimitOrHoldingAControlCharacter_isRefused() {
		KnowledgeBaseName name = new KnowledgeBaseName("handbook");
		String longest = "x".repeat(200);

		assertTrue(violatedPaths(new GlobalKnowledgeBaseCreateRequest(name, longest, null, null)).isEmpty());
		assertEquals(Set.of("title"), violatedPaths(new GlobalKnowledgeBaseCreateRequest(name, longest + "x", null,
				null)));
		assertEquals(Set.of("title"), violatedPaths(new GlobalKnowledgeBaseCreateRequest(name, "a\tb", null, null)));
		assertEquals(Set.of("title"), violatedPaths(new GlobalKnowledgeBasePatchRequest(null, longest + "x", null,
				null)));
		assertEquals(Set.of("title"), violatedPaths(new GlobalKnowledgeBasePatchRequest(null, "a\u007fb", null,
				null)));
	}

	@Test
	void aTitle_isJudgedTrimmed_asTheStoredRuleJudgesIt() {
		KnowledgeBaseName name = new KnowledgeBaseName("handbook");
		String padded = " " + "x".repeat(200) + " ";

		// Surrounding whitespace is trimmed before the length and control-character checks, exactly as
		// GlobalKnowledgeBase.title() stores it, so neither of these is refused at the edge.
		assertTrue(violatedPaths(new GlobalKnowledgeBaseCreateRequest(name, padded, null, null)).isEmpty());
		assertTrue(violatedPaths(new GlobalKnowledgeBaseCreateRequest(name, "\tHandbook\n", null, null)).isEmpty());
		assertTrue(violatedPaths(new GlobalKnowledgeBasePatchRequest(null, padded, null, null)).isEmpty());
		assertTrue(violatedPaths(new GlobalKnowledgeBasePatchRequest(null, "\tHandbook\n", null, null)).isEmpty());
		// Blank once trimmed is not a title.
		assertEquals(Set.of("title"), violatedPaths(new GlobalKnowledgeBaseCreateRequest(name, " \t ", null, null)));
		assertEquals(Set.of("title"), violatedPaths(new GlobalKnowledgeBasePatchRequest(null, "", null, null)));
	}

	@Test
	void anEmptyPatch_isValid() {
		assertTrue(violatedPaths(new GlobalKnowledgeBasePatchRequest(null, null, null, null)).isEmpty());
	}
}
