package dev.bluestep.global.dto.consoleglobaluser;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Set;

import org.hibernate.validator.messageinterpolation.ParameterMessageInterpolator;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

/** The 6.18.0 Console write shapes. Every fixture is fictional. */
@DisplayName("6.18.0 Console global-user write shapes")
class ConsoleGlobalUserWriteWireTest {

	private static final ObjectMapper JSON = JsonMapper.builder()
			.enable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
			.enable(DeserializationFeature.FAIL_ON_NULL_FOR_PRIMITIVES)
			.build();

	private static Validator validator() {
		try (ValidatorFactory factory = Validation.byDefaultProvider().configure()
				.messageInterpolator(new ParameterMessageInterpolator())
				.buildValidatorFactory()) {
			return factory.getValidator();
		}
	}

	@Test
	@DisplayName("a disable request has exactly the documented wire and round trips")
	void disabledRequestWire() {
		ConsoleGlobalUserDisabledRequest request = new ConsoleGlobalUserDisabledRequest("4-2", true);
		String json = JSON.writeValueAsString(request);

		assertEquals("{\"expectedVersion\":\"4-2\",\"disabled\":true}", json);
		assertEquals(request, JSON.readValue(json, ConsoleGlobalUserDisabledRequest.class));
		assertEquals(new ConsoleGlobalUserDisabledRequest("7", false),
				JSON.readValue("{\"expectedVersion\":\"7\",\"disabled\":false}", ConsoleGlobalUserDisabledRequest.class));
	}

	@Test
	@DisplayName("an omitted disabled is refused, never read as false; an omitted version is refused")
	void omittedMembersAreRefused() {
		assertThrows(JacksonException.class,
				() -> JSON.readValue("{\"expectedVersion\":\"4\"}", ConsoleGlobalUserDisabledRequest.class));
		assertThrows(RuntimeException.class,
				() -> JSON.readValue("{\"disabled\":true}", ConsoleGlobalUserDisabledRequest.class));
	}

	@Test
	@DisplayName("a blank or overlong version fails validation")
	void versionIsValidated() {
		Validator validator = validator();
		assertTrue(validator.validate(new ConsoleGlobalUserDisabledRequest("4-2", true)).isEmpty());
		Set<ConstraintViolation<ConsoleGlobalUserDisabledRequest>> blank =
				validator.validate(new ConsoleGlobalUserDisabledRequest(" ", true));
		assertEquals(1, blank.size());
		Set<ConstraintViolation<ConsoleGlobalUserDisabledRequest>> overlong = validator.validate(
				new ConsoleGlobalUserDisabledRequest("1".repeat(ConsoleGlobalUserDisabledRequest.MAX_VERSION + 1), true));
		assertEquals(1, overlong.size());
	}

	@Test
	@DisplayName("a refusal round trips every reason, and binds an unknown or omitted one as UNKNOWN")
	void refusalWire() {
		for (ConsoleGlobalUserWriteRefusal.Reason reason : ConsoleGlobalUserWriteRefusal.Reason.values()) {
			ConsoleGlobalUserWriteRefusal refusal = new ConsoleGlobalUserWriteRefusal(reason, "No.");
			String json = JSON.writeValueAsString(refusal);
			assertEquals("{\"reason\":\"" + reason.name() + "\",\"message\":\"No.\"}", json);
			assertEquals(refusal, JSON.readValue(json, ConsoleGlobalUserWriteRefusal.class));
		}
		assertEquals(new ConsoleGlobalUserWriteRefusal(ConsoleGlobalUserWriteRefusal.Reason.UNKNOWN, "x"),
				JSON.readValue("{\"reason\":\"SOMETHING_NEWER\",\"message\":\"x\"}", ConsoleGlobalUserWriteRefusal.class));
		assertEquals(new ConsoleGlobalUserWriteRefusal(ConsoleGlobalUserWriteRefusal.Reason.UNKNOWN, ""),
				JSON.readValue("{}", ConsoleGlobalUserWriteRefusal.class));
	}
}
