package dev.bluestep.global.dto.usage;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import org.hibernate.validator.messageinterpolation.ParameterMessageInterpolator;
import org.junit.jupiter.api.Test;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;

/**
 * The storage-sample constraints, run through a real Bean Validation provider — so "the
 * annotation is in the source" is distinguishable from "the annotation fires", including the
 * {@code @Valid} cascade into each sample that web-global's ingest relies on to answer 400.
 */
class StorageSampleValidationTest {

	private static final Validator VALIDATOR = validator();

	private static Validator validator() {
		try (ValidatorFactory factory = Validation.byDefaultProvider().configure()
				.messageInterpolator(new ParameterMessageInterpolator())
				.buildValidatorFactory()) {
			return factory.getValidator();
		}
	}

	private static Set<String> violatedPaths(final Object bean) {
		return VALIDATOR.validate(bean).stream()
				.map(ConstraintViolation::getPropertyPath)
				.map(Object::toString)
				.collect(Collectors.toUnmodifiableSet());
	}

	private static StorageSampleBatchRequest batchOf(final List<StorageSample> samples) {
		return new StorageSampleBatchRequest("b6p-07", 1_788_652_800_000L, samples);
	}

	private static StorageSample valid() {
		return new StorageSample("U1000001", 1, 2, 3);
	}

	@Test
	void aValidBatchHasNoViolations() {
		assertEquals(Set.of(), violatedPaths(batchOf(List.of(valid()))));
	}

	@Test
	void anEmptyPassIsRefused() {
		assertEquals(Set.of("samples"), violatedPaths(batchOf(List.of())));
	}

	@Test
	void theSampleCountIsBounded() {
		final List<StorageSample> tooMany =
				Collections.nCopies(StorageSampleBatchRequest.MAX_SAMPLES + 1, valid());

		assertEquals(Set.of("samples"), violatedPaths(batchOf(tooMany)));
	}

	@Test
	void theNamespaceIsRequiredAndBounded() {
		assertEquals(Set.of("namespace"), violatedPaths(
				new StorageSampleBatchRequest(" ", 1_788_652_800_000L, List.of(valid()))));
		assertEquals(Set.of("namespace"), violatedPaths(new StorageSampleBatchRequest(
				"n".repeat(UsageBatchRequest.MAX_NAMESPACE_LENGTH + 1), 1_788_652_800_000L,
				List.of(valid()))));
	}

	/** Each byte column refused on its own, so the cascade and every bound are pinned. */
	@Test
	void everySampleFieldIsBoundedThroughTheCascade() {
		final long over = StorageSample.MAX_BYTES + 1;
		final List<StorageSample> bad = List.of(
				new StorageSample("U".repeat(StorageSample.MAX_SCHEMA_NAME_LENGTH + 1), 1, 2, 3),
				new StorageSample("U1000001", -1, 2, 3),
				new StorageSample("U1000001", 1, over, 3),
				new StorageSample("U1000001", 1, 2, -1));

		assertEquals(Set.of("samples[0].schemaName", "samples[1].dbBytes",
				"samples[2].fileLogicalBytes", "samples[3].filePhysicalBytes"),
				violatedPaths(batchOf(bad)));
	}
}
