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
 * The {@link DbUsageBatchRequest} constraints, run through a real Bean Validation provider,
 * including the {@code @Valid} cascade into each row. Every bound is exercised one past the limit
 * and accepted at it.
 */
class DbUsageValidationTest {

	private static final Validator VALIDATOR = validator();
	private static final OffsetDateTime START = OffsetDateTime.of(2026, 9, 7, 0, 0, 0, 0, ZoneOffset.UTC);
	private static final long MAX = DbUsageRow.MAX_TOTAL;

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

	private static DbUsageBatchRequest batch(final long dealloc, final long entries, final long entriesMax,
			final List<DbUsageRow> tenants) {
		return new DbUsageBatchRequest("b6p-07", START, START.plusMinutes(5), dealloc, false, entries,
				entriesMax, tenants);
	}

	private static DbUsageBatchRequest batchOf(final List<DbUsageRow> tenants) {
		return batch(0, 10, 50_000, tenants);
	}

	@Test
	void aValidBatchAndTheLimitsHaveNoViolations() {
		assertEquals(Set.of(), violatedPaths(batchOf(List.of(new DbUsageRow("U1000001", 1, 2, 3, 4)))));
		assertEquals(Set.of(), violatedPaths(batch(MAX, MAX, MAX,
				List.of(new DbUsageRow("x".repeat(DbUsageRow.MAX_SCHEMA_NAME_LENGTH), MAX, MAX, MAX, MAX)))));
		assertEquals(Set.of(), violatedPaths(batchOf(List.of())), "an empty window is the heartbeat");
	}

	@Test
	void namespaceAndWindowAreRequired() {
		assertEquals(Set.of("namespace", "windowStart", "windowEnd"), violatedPaths(
				new DbUsageBatchRequest(" ", null, null, 0, false, 0, 100, List.of())));
	}

	@Test
	void namespaceFigures_areBounded() {
		assertEquals(Set.of("deallocDelta", "entries", "entriesMax"),
				violatedPaths(batch(-1, -1, -1, List.of())));
		assertEquals(Set.of("deallocDelta", "entries", "entriesMax"),
				violatedPaths(batch(MAX + 1, MAX + 1, MAX + 1, List.of())));
	}

	@Test
	void rowFigures_areBoundedThroughTheCascade() {
		assertEquals(Set.of("tenants[0].calls", "tenants[0].execMicros", "tenants[0].sharedBlksRead",
				"tenants[0].sharedBlksHit"),
				violatedPaths(batchOf(List.of(new DbUsageRow("U1000001", -1, -1, -1, -1)))));
		assertEquals(Set.of("tenants[0].calls", "tenants[0].execMicros", "tenants[0].sharedBlksRead",
				"tenants[0].sharedBlksHit"),
				violatedPaths(batchOf(List.of(new DbUsageRow("U1000001", MAX + 1, MAX + 1, MAX + 1, MAX + 1)))));
	}

	@Test
	void schemaName_isRequiredAndBounded() {
		assertEquals(Set.of("tenants[0].schemaName"),
				violatedPaths(batchOf(List.of(new DbUsageRow(" ", 0, 0, 0, 0)))));
		assertEquals(Set.of("tenants[0].schemaName"), violatedPaths(batchOf(List.of(
				new DbUsageRow("x".repeat(DbUsageRow.MAX_SCHEMA_NAME_LENGTH + 1), 0, 0, 0, 0)))));
	}

	@Test
	void tenantCount_isBounded() {
		final DbUsageRow row = new DbUsageRow("U1000001", 0, 0, 0, 0);
		assertEquals(Set.of(), violatedPaths(batchOf(Collections.nCopies(DbUsageBatchRequest.MAX_TENANTS, row))));
		assertEquals(Set.of("tenants"),
				violatedPaths(batchOf(Collections.nCopies(DbUsageBatchRequest.MAX_TENANTS + 1, row))));
	}

	@Test
	void unattributedRow_isValid() {
		assertEquals(Set.of(), violatedPaths(batchOf(List.of(new DbUsageRow("unattributed", 1, 2, 3, 4)))));
	}

	@Test
	void entriesMax_mustBePositive() {
		assertEquals(Set.of("entriesMax"), violatedPaths(batch(0, 0, 0, List.of())));
		assertEquals(Set.of(), violatedPaths(batch(0, 1, 1, List.of())));
	}

	/** {@code entries} may reach {@code entriesMax} but never pass it. */
	@Test
	void entries_mayNotExceedEntriesMax() {
		assertEquals(Set.of(), violatedPaths(batch(0, 100, 100, List.of())));
		assertEquals(Set.of("entriesWithinCapacity"), violatedPaths(batch(0, 101, 100, List.of())));
	}
}
