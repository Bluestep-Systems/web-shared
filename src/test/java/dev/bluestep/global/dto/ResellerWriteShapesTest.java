package dev.bluestep.global.dto;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.HashSet;
import java.util.Optional;
import java.util.OptionalLong;
import java.util.Set;
import java.util.stream.Collectors;

import org.hibernate.validator.messageinterpolation.ParameterMessageInterpolator;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

import dev.bluestep.global.dto.basetable.BaseTableKey;
import dev.bluestep.global.dto.domain.DomainSuffix;
import dev.bluestep.global.dto.reseller.ResellerCreateRequest;
import dev.bluestep.global.dto.reseller.ResellerDeleteRefusal;
import dev.bluestep.global.dto.reseller.ResellerIcons;
import dev.bluestep.global.dto.reseller.ResellerUpdateRequest;
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
				DomainSuffix.of("example.test"), null, null, noIcons(), 1L);
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
	@DisplayName("the email bound fires on a present value, in code points")
	void emailBoundFiresOnAPresentValue() {
		String overLong = "x".repeat(257);
		String atBoundInSupplementaryCharacters = "😀".repeat(256);

		assertEquals(Set.of("supportEmail"), violatedPaths(new ResellerCreateRequest(CREATOR, "Example Reseller",
				overLong, null, null, null, noIcons(), null)));
		assertEquals(Set.of(), violatedPaths(new ResellerCreateRequest(CREATOR, "Example Reseller",
				atBoundInSupplementaryCharacters, null, null, null, noIcons(), null)));
	}

	/**
	 * The default domain is a {@link DomainSuffix}, bound under every contract version: bean validation holds
	 * it to the column's width only, so an older version still takes an administrator's text as sent. Its
	 * shape (blank or a host) is web-global's rule from 6.0.0, not a constraint here.
	 */
	@ParameterizedTest
	@ValueSource(strings = {"", "example.test", " ", "Example.com", " padded.example.com ", "1.2.3.4", "localhost"})
	@DisplayName("any default domain within the column width passes bean validation, on create and update")
	void defaultDomainWithinTheWidthPasses(String suffix) {
		assertEquals(Set.of(), violatedPaths(withDefaultDomain(suffix)), suffix);
		assertEquals(Set.of(), violatedPaths(updateWithDefaultDomain(suffix)), suffix);
	}

	@Test
	@DisplayName("a default domain over the column width is refused, counted in code points")
	void defaultDomainOverTheWidthRefused() {
		String over = "x".repeat(DomainSuffix.MAX_LENGTH + 1);
		assertEquals(Set.of("defaultDomain.value"), violatedPaths(withDefaultDomain(over)));
		assertEquals(Set.of("defaultDomain.value"), violatedPaths(updateWithDefaultDomain(over)));
		assertEquals(Set.of(), violatedPaths(withDefaultDomain("😀".repeat(DomainSuffix.MAX_LENGTH))));
	}

	private static ResellerCreateRequest withDefaultDomain(String suffix) {
		return new ResellerCreateRequest(CREATOR, "Example Reseller", null, DomainSuffix.of(suffix), null, null,
				noIcons(), null);
	}

	private static ResellerUpdateRequest updateWithDefaultDomain(String suffix) {
		return new ResellerUpdateRequest("Example Reseller", null, DomainSuffix.of(suffix), null, null, noIcons(),
				null, null);
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
