package dev.bluestep.global.dto;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jdk8.Jdk8Module;

import dev.bluestep.global.dto.globaluserrecord.GlobalUserCredentialUpdateRequest;
import dev.bluestep.global.dto.globaluserrecord.GlobalUserRecordCreateRequest;
import dev.bluestep.global.dto.globaluserrecord.GlobalUserRecordUpdateRequest;

/**
 * What the global-user record write shapes actually put on the wire, under both Jackson families.
 *
 * <h2>The property that has to hold, and nearly did not</h2>
 *
 * <p>A {@code @AssertTrue} constraint is a JavaBeans getter. Jackson does not know it is a
 * constraint; it sees {@code isStoredCredentialInStoredForm()} and publishes
 * {@code storedCredentialInStoredForm} as a property of the record. That is a field the record cannot
 * bind back — it has no such component — so
 * the type stops round-tripping through its own mapper, and a strict mapper rejects it outright.</p>
 *
 * <p>{@code @JsonIgnore} is what prevents it, and nothing else would have caught its removal: the
 * constraint keeps working, the validation tests keep passing, and the extra key only shows up in a
 * payload. Hence this suite. On a credential shape the stakes are higher than tidiness — the fewer
 * derived fields on that wire, the better.</p>
 *
 * <p>Both mappers, because both are real: web-global runs Jackson 3 as its primary and keeps a
 * hand-built Jackson 2 island for msgpack, so a shape that behaves differently between them is a
 * shape whose behaviour depends on which endpoint it arrived at.</p>
 */
@DisplayName("global user record wire shapes")
class GlobalUserRecordWireTest {

	private static final long CLASSID = 222_222L;
	private static final String STORED_CREDENTIAL = "\n1Q0lQSEVSVEVYVA==";

	private static final ObjectMapper JACKSON2 = new ObjectMapper().registerModule(new Jdk8Module());
	private static final tools.jackson.databind.ObjectMapper JACKSON3 =
			new tools.jackson.databind.json.JsonMapper();

	// ---------------------------------------------------------------- no derived fields on the wire

	@Test
	@DisplayName("no constraint method appears as a JSON property, under either Jackson")
	void constraintMethodsAreNotSerialized() throws Exception {
		String credential2 = JACKSON2.writeValueAsString(credentialUpdate());
		String credential3 = JACKSON3.writeValueAsString(credentialUpdate());
		String create2 = JACKSON2.writeValueAsString(create());
		String create3 = JACKSON3.writeValueAsString(create());
		String update2 = JACKSON2.writeValueAsString(update());
		String update3 = JACKSON3.writeValueAsString(update());

		for (String json : List.of(credential2, credential3, create2, create3, update2, update3)) {
			assertFalse(json.contains("InStoredForm"),
					"a @AssertTrue getter leaked onto the wire: " + json);
		}
		assertEquals("{\"storedCredential\":\"" + STORED_CREDENTIAL.replace("\n", "\\n") + "\"}",
				credential2, "the credential shape is one component and should serialize as one key");
		assertEquals(credential2, credential3, "the two mappers must agree on the credential shape");
	}

	/**
	 * The consequence of the leak, stated as the property rather than the symptom. A type that cannot
	 * read back what it wrote is one a caller cannot round-trip through a strict mapper — and the
	 * msgpack mapper this service still registers is hand-built and strict.
	 */
	@Test
	@DisplayName("every write shape round-trips through its own mapper")
	void writeShapesRoundTrip() {
		assertDoesNotThrow(() -> {
			assertEquals(credentialUpdate(), JACKSON2.readValue(
					JACKSON2.writeValueAsString(credentialUpdate()),
					GlobalUserCredentialUpdateRequest.class));
			assertEquals(create(), JACKSON2.readValue(JACKSON2.writeValueAsString(create()),
					GlobalUserRecordCreateRequest.class));
			assertEquals(update(), JACKSON2.readValue(JACKSON2.writeValueAsString(update()),
					GlobalUserRecordUpdateRequest.class));
		});
	}

	// ---------------------------------------------------------------- omitted fields bind to null

	/**
	 * {@code {}} binds — it does not throw — and produces a {@code null} component, which is exactly
	 * why {@code @NotBlank} rather than a compact-constructor default is what makes the credential
	 * required. A shape that relied on binding to fail would be relying on mapper configuration this
	 * module does not control; a constraint answers the same way under any mapper.
	 *
	 * <p>Both Jackson families, because both bind this shape in production and a single-component
	 * record is where a delegating-creator heuristic could plausibly diverge.</p>
	 */
	@Test
	@DisplayName("an omitted or null credential binds to null, leaving @NotBlank to refuse it")
	void omittedCredentialBindsToNull() throws Exception {
		GlobalUserCredentialUpdateRequest fromJackson2 =
				JACKSON2.readValue("{}", GlobalUserCredentialUpdateRequest.class);
		GlobalUserCredentialUpdateRequest fromJackson3 =
				JACKSON3.readValue("{}", GlobalUserCredentialUpdateRequest.class);
		GlobalUserCredentialUpdateRequest explicitNull =
				JACKSON2.readValue("{\"storedCredential\":null}",
						GlobalUserCredentialUpdateRequest.class);

		for (GlobalUserCredentialUpdateRequest bound :
				List.of(fromJackson2, fromJackson3, explicitNull)) {
			assertNull(bound.storedCredential());
		}
	}

	/**
	 * And the value survives binding unchanged — the leading newline in particular, which is the one
	 * character the whole guard turns on and the one most likely to be mangled by an encoder.
	 */
	@Test
	@DisplayName("the marker survives a round trip through JSON escaping")
	void markerSurvivesEscaping() throws Exception {
		GlobalUserCredentialUpdateRequest bound = JACKSON2.readValue(
				JACKSON2.writeValueAsString(credentialUpdate()),
				GlobalUserCredentialUpdateRequest.class);

		assertEquals(STORED_CREDENTIAL, bound.storedCredential());
		assertTrue(bound.isStoredCredentialInStoredForm());
	}

	// ---------------------------------------------------------------- fixtures

	private static GlobalUserCredentialUpdateRequest credentialUpdate() {
		return new GlobalUserCredentialUpdateRequest(STORED_CREDENTIAL);
	}

	private static GlobalUserRecordCreateRequest create() {
		return new GlobalUserRecordCreateRequest(CLASSID, Optional.empty(), Optional.of("Del"),
				Optional.of("Novak"), Optional.of("del.novak"), Optional.of("del@example.test"),
				Optional.empty(), false, false, Optional.empty(), Optional.empty(), false,
				Optional.empty(), List.of(), Optional.of(STORED_CREDENTIAL));
	}

	private static GlobalUserRecordUpdateRequest update() {
		return new GlobalUserRecordUpdateRequest(Optional.of("Del"), Optional.of("Novak"),
				Optional.of("del.novak"), Optional.of("del@example.test"), Optional.empty(), false,
				false, Optional.empty(), Optional.empty(), false, Optional.empty(), List.of());
	}
}
