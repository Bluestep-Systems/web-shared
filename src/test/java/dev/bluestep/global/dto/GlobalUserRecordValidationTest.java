package dev.bluestep.global.dto;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Optional;
import java.util.Set;

import org.hibernate.validator.messageinterpolation.ParameterMessageInterpolator;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import dev.bluestep.global.dto.globaluserrecord.GlobalUserCredentialUpdateRequest;
import dev.bluestep.global.dto.globaluserrecord.GlobalUserRecordCreateRequest;
import dev.bluestep.global.dto.globaluserrecord.GlobalUserRecordUpdateRequest;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;

/**
 * The constraints on the global-user record shapes, run through a real Bean Validation provider.
 *
 * <h2>Why a provider and not just the methods</h2>
 *
 * <p>{@link GlobalUserRecordConstraintsTest} calls the {@code @AssertTrue} methods directly, which
 * proves their logic and nothing about whether anything ever invokes them. The annotations are part
 * of this module's published contract — the {@code @Size} bounds in particular are the column widths,
 * and a consumer relies on them to answer 400 at its edge — so "the annotation is in the source" has
 * to be distinguishable from "the annotation fires". Swap two {@code @Size} values, or move one onto
 * the {@code Optional} instead of its type argument, and every assertion in the sibling suite still
 * passes.</p>
 *
 * <p>The container-element placement is the specific thing worth pinning. A constraint written
 * {@code @Size Optional<String>} rather than {@code Optional<@Size String>} is checked against the
 * {@code Optional} itself and fails at runtime with "no validator could be found" — a mistake that
 * looks identical in a diff and is invisible until a request arrives.</p>
 *
 * <p>{@link ParameterMessageInterpolator} rather than the default: the default wants an Expression
 * Language implementation on the classpath, and these assertions are about which constraints fired
 * rather than about how their messages render.</p>
 */
@DisplayName("global user record validation")
class GlobalUserRecordValidationTest {

	private static final long CLASSID = 222_222L;
	private static final String STORED_CREDENTIAL = "\n1Q0lQSEVSVEVYVA==";

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
				.collect(java.util.stream.Collectors.toUnmodifiableSet());
	}

	// ---------------------------------------------------------------- @Size actually fires

	/**
	 * Each bound is asserted at its own width, so a suite that still passes after two of them are
	 * swapped is not possible: 31 characters is legal in {@code username} and illegal in
	 * {@code firstName}, and 51 is the other way round.
	 */
	@Test
	@DisplayName("each string bound is the width of its own column")
	void size_boundsMatchTheirColumns() {
		assertEquals(Set.of("firstName"), violatedPaths(create(name(31), name(20), name(20))),
				"firstname is varchar(30), and 31 must fail there and nowhere else");
		assertEquals(Set.of("username"), violatedPaths(create(name(20), name(20), name(51))),
				"username is varchar(50), so 51 fails while 31 would not");
		assertEquals(Set.of(), violatedPaths(create(name(30), name(30), name(50))),
				"exactly at the bound is legal — an off-by-one here rejects real values");
	}

	@Test
	@DisplayName("the email bound is 255 and the attribs bound is 4000")
	void size_boundsOnTheWiderColumns() {
		GlobalUserRecordCreateRequest longEmail = new GlobalUserRecordCreateRequest(CLASSID,
				Optional.empty(), Optional.of("Del"), Optional.of("Novak"), Optional.of("del.novak"),
				Optional.of(name(256)), Optional.empty(), false, false, Optional.empty(),
				Optional.empty(), false, Optional.empty(), List.of(), Optional.of(STORED_CREDENTIAL));
		assertEquals(Set.of("userEmail"), violatedPaths(longEmail));

		// Base64 so the shape guard is satisfied and only the length can be at fault.
		String longAttribs = "Q".repeat(4004);
		GlobalUserRecordCreateRequest bigAttribs = new GlobalUserRecordCreateRequest(CLASSID,
				Optional.empty(), Optional.of("Del"), Optional.of("Novak"), Optional.of("del.novak"),
				Optional.of("del@example.test"), Optional.empty(), false, false,
				Optional.of(longAttribs), Optional.empty(), false, Optional.empty(), List.of(),
				Optional.of(STORED_CREDENTIAL));
		assertEquals(Set.of("attribs"), violatedPaths(bigAttribs));
	}

	@Test
	@DisplayName("the update shape carries the same bounds as the create shape")
	void size_updateMatchesCreate() {
		GlobalUserRecordUpdateRequest tooLong = new GlobalUserRecordUpdateRequest(
				Optional.of(name(31)), Optional.of("Novak"), Optional.of(name(51)),
				Optional.of("del@example.test"), Optional.empty(), false, false, Optional.empty(),
				Optional.empty(), false, Optional.empty(), List.of());

		assertEquals(Set.of("firstName", "username"), violatedPaths(tooLong),
				"a bound on create but not update lets a row be edited into a state it could not be "
						+ "created in");
	}

	// ---------------------------------------------------------------- @Positive actually fires

	@Test
	@DisplayName("a non-positive classid is refused")
	void positive_classidMustBePositive() {
		for (long classid : new long[] {0L, -1L}) {
			GlobalUserRecordCreateRequest request = new GlobalUserRecordCreateRequest(classid,
					Optional.empty(), Optional.of("Del"), Optional.of("Novak"),
					Optional.of("del.novak"), Optional.of("del@example.test"), Optional.empty(),
					false, false, Optional.empty(), Optional.empty(), false, Optional.empty(),
					List.of(), Optional.of(STORED_CREDENTIAL));

			assertEquals(Set.of("classid"), violatedPaths(request),
					"classid " + classid + " would create a row under a class nothing can find");
		}
	}

	// ---------------------------------------------------------------- the guards fire too

	/**
	 * The guards are {@code @AssertTrue} on derived booleans, so their violation path is the derived
	 * property name rather than the component. That is not cosmetic: a consumer's error handler that
	 * reports a failed constraint's rejected value would publish a live password if the constraint sat
	 * on {@code storedCredential} itself, and reports {@code false} because it does not.
	 */
	@Test
	@DisplayName("the credential guard fires, and reports the derived property rather than the value")
	void assertTrue_credentialGuardFires() {
		Set<ConstraintViolation<GlobalUserCredentialUpdateRequest>> violations =
				VALIDATOR.validate(new GlobalUserCredentialUpdateRequest(Optional.of("hunter2")));

		assertEquals(1, violations.size());
		ConstraintViolation<GlobalUserCredentialUpdateRequest> violation =
				violations.iterator().next();
		assertEquals("storedCredentialInStoredForm", violation.getPropertyPath().toString());
		assertEquals(Boolean.FALSE, violation.getInvalidValue(),
				"the rejected value must be the derived boolean, never the credential — this is what "
						+ "keeps a consumer's error body free of it");
	}

	/**
	 * The counterpart to the guard firing: {@code attribs} is deliberately held to no shape at all, so
	 * plain text in it is accepted here and stored. Asserted rather than merely absent, because a
	 * constraint added there would silently change what an already-released contract tag accepts and
	 * could make an existing row un-editable through a whole-record update.
	 */
	@Test
	@DisplayName("attribs carries no shape constraint, on either write shape")
	void attribsIsNotShapeConstrained() {
		assertEquals(Set.of(), violatedPaths(new GlobalUserRecordUpdateRequest(Optional.of("Del"),
				Optional.of("Novak"), Optional.of("del.novak"), Optional.of("del@example.test"),
				Optional.empty(), false, false, Optional.of("PLATFORM_A,PLATFORM_B"),
				Optional.empty(), false, Optional.empty(), List.of())));

		assertTrue(violatedPaths(new GlobalUserRecordCreateRequest(CLASSID, Optional.empty(),
				Optional.of("Del"), Optional.of("Novak"), Optional.of("del.novak"),
				Optional.of("del@example.test"), Optional.empty(), false, false,
				Optional.of("PLATFORM_A,PLATFORM_B"), Optional.empty(), false, Optional.empty(),
				List.of(), Optional.of(STORED_CREDENTIAL))).isEmpty());
	}

	/** A fully-populated, well-formed request must produce no violations at all. */
	@Test
	@DisplayName("a well-formed request is clean")
	void wellFormedRequestHasNoViolations() {
		assertEquals(Set.of(), violatedPaths(create(name(20), name(20), name(20))));
	}

	// ---------------------------------------------------------------- fixtures

	/** Base64-alphabet characters, so length is the only thing a fixture can be at fault for. */
	private static String name(int length) {
		return "N".repeat(length);
	}

	private static GlobalUserRecordCreateRequest create(String firstName, String lastName,
			String username) {
		return new GlobalUserRecordCreateRequest(CLASSID, Optional.empty(), Optional.of(firstName),
				Optional.of(lastName), Optional.of(username), Optional.of("del@example.test"),
				Optional.empty(), false, false, Optional.empty(), Optional.empty(), false,
				Optional.empty(), List.of(), Optional.of(STORED_CREDENTIAL));
	}
}
