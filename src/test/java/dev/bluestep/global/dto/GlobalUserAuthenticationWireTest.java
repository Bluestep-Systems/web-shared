package dev.bluestep.global.dto;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import org.hibernate.validator.messageinterpolation.ParameterMessageInterpolator;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import tools.jackson.databind.DatabindException;
import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

import dev.bluestep.global.dto.globaluserrecord.GlobalUserAuthenticationOutcome;
import dev.bluestep.global.dto.globaluserrecord.GlobalUserAuthenticationRequest;
import dev.bluestep.global.dto.globaluserrecord.GlobalUserAuthenticationResponse;
import dev.bluestep.global.dto.globaluserrecord.GlobalUserRecordResponse;
import dev.bluestep.global.dto.tenant.TenantKey;
import dev.bluestep.global.dto.tenantaccess.GlobalUserKey;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;

/**
 * The sign-in check's two shapes (6.0.0): what they put on the wire, what they refuse, and that the
 * candidate password never reaches a string a log could print.
 */
@DisplayName("global user authentication shapes")
class GlobalUserAuthenticationWireTest {

	private static final String CANDIDATE = "s3cret-candidate";

	private static final TenantKey TENANT = new TenantKey(TenantKey.CLASSID, 42L);

	/** Strict, so a derived getter leaking onto the wire fails the round trip. */
	private static final ObjectMapper JACKSON3 = JsonMapper.builder()
			.enable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
			.build();

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

	/** A request bound from a body that names no tenant, which is how one reaches a validator. */
	private static GlobalUserAuthenticationRequest nullTenant() {
		return JACKSON3.readValue("{\"username\":\"ada\",\"candidate\":\"" + CANDIDATE + "\"}",
				GlobalUserAuthenticationRequest.class);
	}

	@Test
	void theTenantIsItsWholeKeyOnTheWire() {
		final GlobalUserAuthenticationRequest request = new GlobalUserAuthenticationRequest(TENANT, "ada", CANDIDATE);
		final JsonNode tenant = JACKSON3.valueToTree(request).get("tenant");
		assertEquals(TenantKey.CLASSID, tenant.get("classid").asLong());
		assertEquals(42L, tenant.get("seqnum").asLong());
		assertEquals(Set.of("classid", "seqnum"), Set.copyOf(tenant.propertyNames()));
		assertTrue(request.toString().contains("tenant=111111:42"), request.toString());
	}

	private static GlobalUserRecordResponse account() {
		return new GlobalUserRecordResponse(new GlobalUserKey(222_222L, -7L), "Ada", "Example", "ada", null, null,
				true, false, null, null, false, null, List.of(), 3L);
	}

	@Test
	void theRequestNeverPrintsItsCandidate() {
		final GlobalUserAuthenticationRequest request = new GlobalUserAuthenticationRequest(TENANT, "ada", CANDIDATE);
		assertFalse(request.toString().contains(CANDIDATE), request.toString());
		assertTrue(request.toString().contains("ada"));
	}

	@Test
	void theRequestRoundTripsWithExactlyItsThreeKeys() {
		final GlobalUserAuthenticationRequest request = new GlobalUserAuthenticationRequest(TENANT, "ada", CANDIDATE);
		final JsonNode tree = JACKSON3.valueToTree(request);
		assertEquals(Set.of("tenant", "username", "candidate"), Set.copyOf(tree.propertyNames()));
		assertEquals(request, JACKSON3.readValue(JACKSON3.writeValueAsString(request),
				GlobalUserAuthenticationRequest.class));
	}

	@Test
	void theRequestRefusesWhatCannotBeChecked() {
		assertEquals(Set.of(), violatedPaths(new GlobalUserAuthenticationRequest(TENANT, "ada", CANDIDATE)));
		assertEquals(Set.of("candidate"), violatedPaths(new GlobalUserAuthenticationRequest(TENANT, "ada", "")));
		assertEquals(Set.of("username"), violatedPaths(new GlobalUserAuthenticationRequest(TENANT, " ", CANDIDATE)));
		assertEquals(Set.of("tenant"), violatedPaths(nullTenant()));
	}

	@Test
	void aPasswordOfSpacesIsStillAPassword() {
		assertEquals(Set.of(), violatedPaths(new GlobalUserAuthenticationRequest(TENANT, "ada", "   ")));
	}

	@Test
	void eachOutcomeRoundTrips() {
		for (final GlobalUserAuthenticationResponse response : List.of(
				GlobalUserAuthenticationResponse.match(account(), true),
				GlobalUserAuthenticationResponse.match(account(), false),
				GlobalUserAuthenticationResponse.noMatch(),
				GlobalUserAuthenticationResponse.legacyCredential())) {
			final String json = JACKSON3.writeValueAsString(response);
			assertEquals(response, JACKSON3.readValue(json, GlobalUserAuthenticationResponse.class), json);
		}
	}

	@Test
	void theOutcomeIsItsNameOnTheWire() {
		final JsonNode tree = JACKSON3.valueToTree(GlobalUserAuthenticationResponse.legacyCredential());
		assertEquals("LEGACY_CREDENTIAL", tree.get("outcome").asString());
		assertEquals(Set.of("outcome", "record", "needsRewrite"), Set.copyOf(tree.propertyNames()));
	}

	@Test
	void onlyAMatchCarriesARecordOrARewrite() {
		assertThrows(IllegalArgumentException.class,
				() -> new GlobalUserAuthenticationResponse(GlobalUserAuthenticationOutcome.MATCH, null, false));
		assertThrows(IllegalArgumentException.class,
				() -> new GlobalUserAuthenticationResponse(GlobalUserAuthenticationOutcome.NO_MATCH, account(), false));
		assertThrows(IllegalArgumentException.class,
				() -> new GlobalUserAuthenticationResponse(GlobalUserAuthenticationOutcome.LEGACY_CREDENTIAL, null,
						true));
	}

	@Test
	void aBodyThatBreaksTheShapeIsRefusedWhereItIsBound() {
		assertThrows(DatabindException.class, () -> JACKSON3.readValue(
				"{\"outcome\":\"MATCH\",\"record\":null,\"needsRewrite\":false}", GlobalUserAuthenticationResponse.class));
		assertThrows(DatabindException.class, () -> JACKSON3.readValue(
				"{\"record\":null,\"needsRewrite\":false}", GlobalUserAuthenticationResponse.class));
		assertThrows(DatabindException.class, () -> JACKSON3.readValue(
				"{\"outcome\":\"SOMETHING_NEW\",\"record\":null,\"needsRewrite\":false}",
				GlobalUserAuthenticationResponse.class));
	}
}
