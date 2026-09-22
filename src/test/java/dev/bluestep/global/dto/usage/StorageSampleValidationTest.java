package dev.bluestep.global.dto.usage;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
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
 * The usage DTO constraints, run through a real Bean Validation provider — so "the annotation is
 * in the source" is distinguishable from "the annotation fires", including the {@code @Valid}
 * cascade into each element that web-global's ingest relies on to answer 400.
 *
 * <p>Every bound is exercised on its own at one past the limit and accepted at the limit, so
 * deleting or loosening any single constraint fails a test.</p>
 */
class StorageSampleValidationTest {

	private static final Validator VALIDATOR = validator();
	private static final OffsetDateTime PERIOD = OffsetDateTime.of(2026, 9, 7, 0, 0, 0, 0, ZoneOffset.UTC);
	private static final long MAX = StorageSample.MAX_BYTES;

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
		return new StorageSampleBatchRequest("b6p-07", PERIOD, samples);
	}

	private static StorageSample valid() {
		return new StorageSample("U1000001", 1, 2, 3);
	}

	@Test
	void aValidBatchHasNoViolations() {
		assertEquals(Set.of(), violatedPaths(batchOf(List.of(valid()))));
	}

	@Test
	void anEmptyPassIsAValidHeartbeat() {
		assertEquals(Set.of(), violatedPaths(batchOf(List.of())));
	}

	@Test
	void theSampleCountIsBounded() {
		assertEquals(Set.of(), violatedPaths(batchOf(
				Collections.nCopies(StorageSampleBatchRequest.MAX_SAMPLES, valid()))));
		assertEquals(Set.of("samples"), violatedPaths(batchOf(
				Collections.nCopies(StorageSampleBatchRequest.MAX_SAMPLES + 1, valid()))));
	}

	@Test
	void theNamespaceIsRequiredAndBounded() {
		final int max = UsageBatchRequest.MAX_NAMESPACE_LENGTH;
		assertEquals(Set.of("namespace"), violatedPaths(
				new StorageSampleBatchRequest(" ", PERIOD, List.of(valid()))));
		assertEquals(Set.of(), violatedPaths(
				new StorageSampleBatchRequest("n".repeat(max), PERIOD, List.of(valid()))));
		assertEquals(Set.of("namespace"), violatedPaths(
				new StorageSampleBatchRequest("n".repeat(max + 1), PERIOD, List.of(valid()))));
	}

	/** The width counts code points, as {@code varchar} does, not UTF-16 units. */
	@Test
	void theNamespaceBoundCountsCodePoints() {
		final String astral = "😀".repeat(UsageBatchRequest.MAX_NAMESPACE_LENGTH);

		assertEquals(Set.of(), violatedPaths(new StorageSampleBatchRequest(astral, PERIOD, List.of())));
	}

	/**
	 * A null {@code sampledAt} is what an omitted key binds to. Every time-window refusal is
	 * web-global's, which alone can allow for clock skew — so a future value passes here.
	 */
	@Test
	void sampledAtIsRequiredButNotTimeBoundedHere() {
		assertEquals(Set.of("sampledAt"),
				violatedPaths(new StorageSampleBatchRequest("b6p-07", null, List.of())));
		assertEquals(Set.of(), violatedPaths(
				new StorageSampleBatchRequest("b6p-07", OffsetDateTime.now().plusDays(1), List.of())));
	}

	@Test
	void theSchemaNameIsRequiredAndBounded() {
		final int max = StorageSample.MAX_SCHEMA_NAME_LENGTH;
		assertEquals(Set.of("samples[0].schemaName"),
				violatedPaths(batchOf(List.of(new StorageSample("", 1, 2, 3)))));
		assertEquals(Set.of(), violatedPaths(batchOf(List.of(new StorageSample("U".repeat(max), 1, 2, 3)))));
		assertEquals(Set.of("samples[0].schemaName"),
				violatedPaths(batchOf(List.of(new StorageSample("U".repeat(max + 1), 1, 2, 3)))));
	}

	/** Each byte column refused on its own at both ends, and accepted at both limits. */
	@Test
	void everyByteColumnIsBoundedThroughTheCascade() {
		assertEquals(Set.of(), violatedPaths(batchOf(List.of(
				new StorageSample("U1000001", 0, 0, 0),
				new StorageSample("U1000001", MAX, MAX, MAX)))));

		final List<StorageSample> bad = List.of(
				new StorageSample("U1000001", -1, 2, 3),
				new StorageSample("U1000001", MAX + 1, 2, 3),
				new StorageSample("U1000001", 1, -1, 3),
				new StorageSample("U1000001", 1, MAX + 1, 3),
				new StorageSample("U1000001", 1, 2, -1),
				new StorageSample("U1000001", 1, 2, MAX + 1));

		assertEquals(Set.of("samples[0].dbBytes", "samples[1].dbBytes",
				"samples[2].fileLogicalBytes", "samples[3].fileLogicalBytes",
				"samples[4].filePhysicalBytes", "samples[5].filePhysicalBytes"),
				violatedPaths(batchOf(bad)));
	}

	/** The moved per-minute records' constraints fire here too, cascade included. */
	@Test
	void usageBatchConstraintsFire() {
		final UsageWindow ok = new UsageWindow(1L, "U1000001", "http", 1, 1, 1, 1, 1, 1, 1, 1, 1, 1);
		final UsageWindow bad = new UsageWindow(1L, "", "c".repeat(UsageWindow.MAX_KEY_LENGTH + 1),
				-1, UsageWindow.MAX_TOTAL + 1, 1, 1, 1, 1, 1, 1, 1, 1);

		assertEquals(Set.of(), violatedPaths(new UsageBatchRequest("id", "b6p-07", List.of(ok))));
		assertEquals(Set.of("batchId", "namespace", "windows[0].schemaName", "windows[0].category",
				"windows[0].hits", "windows[0].pageMillis"),
				violatedPaths(new UsageBatchRequest(" ", " ", List.of(bad))));
		assertEquals(Set.of("windows"), violatedPaths(new UsageBatchRequest("id", "b6p-07",
				Collections.nCopies(UsageBatchRequest.MAX_WINDOWS + 1, ok))));
	}
}
