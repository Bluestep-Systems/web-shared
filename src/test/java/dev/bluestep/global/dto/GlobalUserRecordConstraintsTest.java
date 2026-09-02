package dev.bluestep.global.dto;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import dev.bluestep.global.dto.globaluserrecord.GlobalUserCredentialUpdateRequest;
import dev.bluestep.global.dto.globaluserrecord.GlobalUserRecordCreateRequest;

/**
 * The credential guard's logic, exercised as the plain method it is.
 *
 * <p>A {@code @AssertTrue} is a public no-argument method returning a boolean, so its logic is
 * testable directly, and doing it that way keeps the cases here about the predicate rather than about
 * a validation provider. {@link GlobalUserRecordValidationTest} is the other half — that the
 * annotations are found and evaluated at all — and web-global's integration suite is where a request
 * actually becomes a 400. All three are needed: this suite would pass if nothing ever called the
 * method, and that one would pass if the method's logic were wrong.</p>
 *
 * <p>The credential fixtures below are invented shapes, not values from any real row: what matters to
 * every assertion is the leading marker and the body's shape, never a particular secret.</p>
 */
@DisplayName("global user record constraints")
class GlobalUserRecordConstraintsTest {

	private static final long CLASSID = 222_222L;

	/**
	 * The shape the monolith's encoder produces today: a newline, one character naming the cipher
	 * generation, then the Base64 body.
	 */
	private static final String STORED_CREDENTIAL = "\n1Q0lQSEVSVEVYVA==";

	@Nested
	@DisplayName("the credential guard")
	class CredentialGuard {

		@Test
		@DisplayName("accepts a value carrying a cipher-generation marker")
		void acceptsAMarkedValue() {
			assertTrue(credentialUpdate(STORED_CREDENTIAL).isStoredCredentialInStoredForm());
			assertTrue(create(Optional.of(STORED_CREDENTIAL)).isStoredCredentialInStoredForm());
		}

		/**
		 * The mistake it exists for: a caller reading "credential" and sending the password it already
		 * has in hand, which would store an unusable value and put a live password on the wire.
		 */
		@Test
		@DisplayName("refuses something that looks like a password")
		void refusesAPlaintextPassword() {
			assertFalse(credentialUpdate("not-a-stored-form").isStoredCredentialInStoredForm());
			assertFalse(create(Optional.of("not-a-stored-form")).isStoredCredentialInStoredForm());
		}

		/**
		 * Both generations the monolith's decoder has a case for, and nothing else.
		 *
		 * <p>Its switch is closed — {@code '0'} and {@code '1'}, no default — and it falls through to
		 * returning {@code null} while swallowing the exception. So an unrecognised generation is not a
		 * value from the future this package should wave through; it is a value that stores with a 200
		 * and then decodes to nothing, leaving an account that can never sign in and logs no reason. A
		 * genuinely new generation needs a case added to that switch anyway, so the constraint follows a
		 * monolith change rather than blocking one.</p>
		 */
		@Test
		@DisplayName("accepts only the generations the monolith's decoder has a case for")
		void acceptsOnlyDecodableGenerations() {
			assertTrue(credentialUpdate("\n0QUJD").isStoredCredentialInStoredForm());
			assertTrue(credentialUpdate("\n1QUJD").isStoredCredentialInStoredForm());
			assertFalse(credentialUpdate("\n2QUJD").isStoredCredentialInStoredForm());
			assertFalse(credentialUpdate("\n9QUJD").isStoredCredentialInStoredForm());
			assertFalse(credentialUpdate("\nxQUJD").isStoredCredentialInStoredForm());
		}

		/**
		 * The monolith's decoder requires more than two characters before it will read a generation, so
		 * a marker with no body decodes to nothing there. Accepting it here would pass a value on that
		 * is guaranteed to be unreadable at the other end.
		 */
		@Test
		@DisplayName("refuses a marker with no ciphertext after it")
		void refusesAMarkerWithNoBody() {
			assertFalse(credentialUpdate("\n1").isStoredCredentialInStoredForm());
		}

		/**
		 * A bare marker is whitespace, so it is blank, so it is {@code @NotBlank}'s to refuse and this
		 * constraint stands aside — the request is still a 400, from the constraint that describes the
		 * problem best. Asserted so that the division of labour is visible rather than surprising: the
		 * two constraints between them must leave no gap, and this is where they meet.
		 */
		@Test
		@DisplayName("leaves a whitespace-only value to @NotBlank")
		void leavesWhitespaceToNotBlank() {
			assertTrue(credentialUpdate("\n").isStoredCredentialInStoredForm());
			assertTrue(credentialUpdate("\n\n\n").isStoredCredentialInStoredForm());
		}

		/**
		 * The shortest thing the guard can accept, pinned from both sides. Without the accepting half,
		 * tightening the minimum length would go unnoticed until it started refusing real values.
		 */
		@Test
		@DisplayName("the shortest legal marked value is accepted, and one character less is not")
		void pinsTheLengthBoundary() {
			assertTrue(credentialUpdate("\n1QUJD").isStoredCredentialInStoredForm(),
					"marker, generation, and one full Base64 quantum is the minimum");
			assertFalse(credentialUpdate("\n1QUJ").isStoredCredentialInStoredForm(),
					"a body that is not a whole number of quanta is not something the decoder can read");
		}

		/**
		 * The body has to be readable, not merely present. A password with a marker typed in front of
		 * it would otherwise store cleanly and then decode to nothing — the same silently-dead account
		 * an unrecognised generation produces, by a different route.
		 */
		@Test
		@DisplayName("refuses a marked value whose body is not Base64")
		void refusesAMarkedValueWithAnUnreadableBody() {
			assertFalse(credentialUpdate("\n1hunter2!").isStoredCredentialInStoredForm());
			assertFalse(credentialUpdate("\n1QUJD\r\nRUZH").isStoredCredentialInStoredForm(),
					"the monolith's encoder never wraps, so a wrapped body did not come from it");
		}

		/**
		 * An earlier cipher generation carries no marker at all, and 56.1% of the credential-carrying
		 * rows measured were of that generation. They are refused here on purpose: nothing distinguishes
		 * one from an arbitrary string by shape, so accepting them would mean accepting a plaintext
		 * password too. The write path never produces one — the monolith re-encodes from plaintext on
		 * every write — so this costs no real request.
		 */
		@Test
		@DisplayName("refuses an unmarked legacy value, which is why the guard is about the current generation")
		void refusesAnUnmarkedLegacyValue() {
			assertFalse(credentialUpdate("SUpLTA==").isStoredCredentialInStoredForm());
		}

		/**
		 * On <em>create</em>, no credential is still a legitimate account state — an account that will
		 * only ever sign in through a linked identity. The update shape is the one where absence is not
		 * expressible at all.
		 */
		@Test
		@DisplayName("create still accepts no credential at all")
		void createAcceptsAnEmptyCredential() {
			assertTrue(create(Optional.empty()).isStoredCredentialInStoredForm());
		}

		/**
		 * A missing credential is {@code @NotBlank}'s to report, not this constraint's. Two violations
		 * for one omission would tell a caller its absent value is also malformed, which is noise on top
		 * of the answer.
		 */
		@Test
		@DisplayName("defers a missing value to @NotBlank rather than reporting it twice")
		void defersAbsenceToNotBlank() {
			assertTrue(credentialUpdate(null).isStoredCredentialInStoredForm());
		}
	}

	private static GlobalUserCredentialUpdateRequest credentialUpdate(String credential) {
		return new GlobalUserCredentialUpdateRequest(credential);
	}

	private static GlobalUserRecordCreateRequest create(Optional<String> credential) {
		return new GlobalUserRecordCreateRequest(CLASSID, Optional.empty(), Optional.of("Del"),
				Optional.of("Novak"), Optional.of("del.novak"), Optional.of("del.novak@example.test"),
				Optional.empty(), false, false, Optional.empty(), Optional.empty(), false,
				Optional.empty(), List.of(), credential);
	}
}
