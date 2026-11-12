package dev.bluestep.harness.dto.knowledgebase;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

import org.hibernate.validator.messageinterpolation.ParameterMessageInterpolator;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;

/**
 * The constraints under Hibernate Validator, the provider both consumers run: what web writes is valid, and a
 * client validating web's answer refuses what web never writes.
 */
class HarnessKnowledgeBaseValidationTest {

	/** No EL on the test classpath, as in the root library's validation tests: messages are not under test. */
	static final ValidatorFactory FACTORY = Validation.byDefaultProvider().configure()
		.messageInterpolator(new ParameterMessageInterpolator()).buildValidatorFactory();
	static final Validator VALIDATOR = FACTORY.getValidator();

	@AfterAll
	static void close() {
		FACTORY.close();
	}

	static HarnessKnowledgeBaseRow row(String name, String title, String branch) {
		return new HarnessKnowledgeBaseRow(UUID.randomUUID(), name, title, branch);
	}

	static Set<String> violations(Object value) {
		return VALIDATOR.validate(value).stream().map(ConstraintViolation::getPropertyPath).map(Object::toString)
			.collect(Collectors.toSet());
	}

	@Test
	void whatWebWritesIsValid() {
		assertThat(violations(HarnessKnowledgeBaseWireTest.FOUND)).isEmpty();
		assertThat(violations(HarnessTenantKnowledgeBaseRows.absent("U142133"))).isEmpty();
		assertThat(violations(row("a", "A", "main"))).isEmpty();
		assertThat(violations(row("a".repeat(64), "t".repeat(200), "b".repeat(200)))).isEmpty();
		assertThat(violations(HarnessTenantKnowledgeBaseRows.found("U9223372036854775807", List.of()))).isEmpty();
	}

	@Test
	void theTenantIsAUNumber() {
		for (String tenant : List.of("U0", "U01", "u1", "1", "U", "U1a", "U12345678901234567890", "")) {
			assertThat(violations(HarnessTenantKnowledgeBaseRows.found(tenant, List.of()))).as(tenant)
				.containsExactly("tenant");
		}
	}

	@Test
	void namesFollowWebsRule() {
		for (String name : List.of("", "-a", "a-", "A", "a_b", "a.b", "a".repeat(65), "kb handbook")) {
			assertThat(violations(row(name, "T", "main"))).as(name).containsExactly("name");
		}
	}

	@Test
	void titlesAreNotBlankAndFitTheColumn() {
		assertThat(violations(row("a", " ", "main"))).containsExactly("title");
		assertThat(violations(row("a", "t".repeat(201), "main"))).containsExactly("title");
	}

	@Test
	void branchesFollowWebsCharacters() {
		for (String branch : List.of("", "/main", ".main", "ma in", "b".repeat(201))) {
			assertThat(violations(row("a", "T", branch))).as(branch).containsExactly("liveBranch");
		}
	}

	@Test
	void rowsAreValidatedInsideTheAnswer() {
		HarnessTenantKnowledgeBaseRows answer = HarnessTenantKnowledgeBaseRows.found("U1",
			List.of(row("Bad", "T", "main")));
		assertThat(violations(answer)).containsExactly("knowledgeBases[0].name");
	}

	@Test
	void anAbsentTenantHasNoRows() {
		HarnessTenantKnowledgeBaseRows contradictory = new HarnessTenantKnowledgeBaseRows("U1", HarnessRowsState.ABSENT,
			List.of(row("a", "A", "main")));
		assertThat(violations(contradictory)).containsExactly("rowsConsistentWithState");
		assertThat(violations(new HarnessTenantKnowledgeBaseRows("U1", HarnessRowsState.UNKNOWN,
			List.of(row("a", "A", "main"))))).isEmpty();
	}
}
