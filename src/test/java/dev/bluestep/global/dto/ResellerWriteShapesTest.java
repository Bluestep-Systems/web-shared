package dev.bluestep.global.dto;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.HashSet;
import java.util.Optional;
import java.util.OptionalLong;
import java.util.Set;
import java.util.stream.Collectors;

import org.hibernate.validator.messageinterpolation.ParameterMessageInterpolator;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

import dev.bluestep.global.dto.basetable.BaseTableKey;
import dev.bluestep.global.dto.reseller.ResellerCreateRequest;
import dev.bluestep.global.dto.reseller.ResellerDeleteRefusal;
import dev.bluestep.global.dto.reseller.ResellerIcons;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;

/**
 * The reseller create request and delete refusal: what they put on the wire, what they bind from it,
 * and which constraints a real Bean Validation provider fires on them.
 *
 * <p>Strict Jackson 3, as {@link GlobalUserRecordWireTest} uses, so a component that leaked onto the
 * wire under a name the record cannot bind back fails the round trip instead of being ignored.</p>
 */
@DisplayName("reseller write shapes")
class ResellerWriteShapesTest {

	private static final BaseTableKey CREATOR = new BaseTableKey(222_222L, 7L);

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
				.collect(Collectors.toSet());
	}

	private static ResellerIcons noIcons() {
		return new ResellerIcons(null, null, null, null,
				null, null, null, null);
	}

	private static ResellerCreateRequest create(String displayName) {
		return new ResellerCreateRequest(CREATOR, displayName, "help@example.test",
				"example.test", null, null, noIcons(), 1L);
	}

	// ---------------------------------------------------------------- create: wire

	@Test
	@DisplayName("a create request round-trips through a strict mapper")
	void createRoundTrips() throws Exception {
		ResellerCreateRequest request = create("Example Reseller");

		assertEquals(request, JACKSON3.readValue(JACKSON3.writeValueAsString(request),
				ResellerCreateRequest.class));
	}

	@Test
	@DisplayName("omitted optional components bind to empty, so an absent flags word means zero to the service")
	void omittedOptionalsBindAsAbsent() throws Exception {
		String json = """
				{"creator":{"classid":222222,"seqnum":7},"displayName":"Example Reseller","icons":{}}
				""";

		ResellerCreateRequest bound = JACKSON3.readValue(json, ResellerCreateRequest.class);

		assertEquals(CREATOR, bound.creator());
		assertEquals(Optional.empty(), bound.optSupportEmail());
		assertEquals(Optional.empty(), bound.optDefaultDomain());
		assertEquals(Optional.empty(), bound.optPrivacyPageUrl());
		assertEquals(Optional.empty(), bound.optTermsPageUrl());
		assertEquals(OptionalLong.empty(), bound.optFlags());
	}

	// ---------------------------------------------------------------- create: constraints

	@Test
	@DisplayName("a well-formed create request violates nothing")
	void wellFormedCreateIsValid() {
		assertEquals(Set.of(), violatedPaths(create("Example Reseller")));
	}

	@Test
	@DisplayName("a blank display name is refused")
	void blankDisplayNameIsRefused() {
		assertEquals(Set.of("displayName"), violatedPaths(create("  ")));
	}

	@Test
	@DisplayName("the creator and the icon set are required")
	void creatorAndIconsAreRequired() {
		ResellerCreateRequest request = new ResellerCreateRequest(null, "Example Reseller", null,
				null, null, null, null, null);

		assertEquals(Set.of("creator", "icons"), violatedPaths(request));
	}

	@Test
	@DisplayName("a creator with an unset half is refused, and a negative one is a real creator")
	void creatorMustNameARow() {
		assertEquals(Set.of("creatorKeyed"), violatedPaths(withCreator(new BaseTableKey(222_222L, 0L))));
		assertEquals(Set.of("creatorKeyed"), violatedPaths(withCreator(new BaseTableKey(0L, 7L))));
		assertEquals(Set.of(), violatedPaths(withCreator(new BaseTableKey(222_222L, -1L))));
	}

	@Test
	@DisplayName("the creator check does not appear on the wire")
	void creatorCheckIsNotSerialized() throws Exception {
		String json = JACKSON3.writeValueAsString(create("Example Reseller"));

		assertFalse(json.contains("creatorKeyed"), "a @AssertTrue getter leaked onto the wire: " + json);
	}

	private static ResellerCreateRequest withCreator(BaseTableKey creator) {
		return new ResellerCreateRequest(creator, "Example Reseller", null, null,
				null, null, noIcons(), null);
	}

	@Test
	@DisplayName("the email and domain bounds fire on a present value, in code points")
	void lengthBoundsFireOnAPresentValue() {
		String overLong = "x".repeat(257);
		String atBoundInSupplementaryCharacters = "😀".repeat(256);
		ResellerCreateRequest request = new ResellerCreateRequest(CREATOR, "Example Reseller",
				overLong, atBoundInSupplementaryCharacters, null,
				null, noIcons(), null);

		Set<String> paths = violatedPaths(request);

		assertEquals(1, paths.size(), "only the over-long email, not the domain at its bound: " + paths);
		assertTrue(paths.iterator().next().startsWith("supportEmail"), paths.toString());
	}

	// ---------------------------------------------------------------- delete refusal

	@Test
	@DisplayName("a delete refusal round-trips every reason through a strict mapper")
	void refusalRoundTrips() throws Exception {
		ResellerDeleteRefusal refusal = new ResellerDeleteRefusal(Set.of(ResellerDeleteRefusal.Reason.values()));

		assertEquals(refusal, JACKSON3.readValue(JACKSON3.writeValueAsString(refusal),
				ResellerDeleteRefusal.class));
	}

	@Test
	@DisplayName("a delete refusal is a copy of the reasons it was built from")
	void refusalCopiesItsReasons() {
		Set<ResellerDeleteRefusal.Reason> reasons = new HashSet<>(Set.of(ResellerDeleteRefusal.Reason.HAS_TENANTS));
		ResellerDeleteRefusal refusal = new ResellerDeleteRefusal(reasons);

		reasons.add(ResellerDeleteRefusal.Reason.HAS_GLOBAL_USERS);

		assertEquals(Set.of(ResellerDeleteRefusal.Reason.HAS_TENANTS), refusal.reasons());
		assertThrows(UnsupportedOperationException.class,
				() -> refusal.reasons().add(ResellerDeleteRefusal.Reason.DEFAULT_RESELLER));
	}
}
