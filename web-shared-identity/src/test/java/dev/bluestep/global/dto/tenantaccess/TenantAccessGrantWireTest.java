package dev.bluestep.global.dto.tenantaccess;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

import org.hibernate.validator.messageinterpolation.ParameterMessageInterpolator;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import tools.jackson.databind.DatabindException;
import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;

/** The 7.0.0 grant provenance: who wrote a tenant-access grant, and whether it provisioned admin membership. */
@DisplayName("7.0.0 tenant-access grant source")
class TenantAccessGrantWireTest {

	private static final ObjectMapper JACKSON3 = JsonMapper.builder()
			.enable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
			.build();

	private static final Validator VALIDATOR = validator();

	private static final GlobalUserKey USER = new GlobalUserKey(222_222L, -51_001L);
	private static final OffsetDateTime GRANTED = OffsetDateTime.parse("2026-11-16T10:00:00Z");
	private static final OffsetDateTime PROVISIONED = OffsetDateTime.parse("2026-11-16T10:00:01Z");
	private static final OffsetDateTime LATER = OffsetDateTime.parse("2026-12-01T00:00:00Z");

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

	@Test
	@DisplayName("a request round-trips each source, and is valid with or without an expiry")
	void requestRoundTrips() {
		for (TenantAccessGrantSource source : TenantAccessGrantSource.values()) {
			for (TenantAccessGrantRequest request : List.of(new TenantAccessGrantRequest(null, source),
					new TenantAccessGrantRequest(LATER, source))) {
				assertEquals(request, JACKSON3.readValue(JACKSON3.writeValueAsString(request),
						TenantAccessGrantRequest.class));
				assertEquals(Set.of(), violatedPaths(request));
			}
		}
		TenantAccessGrantRequest permanent = JACKSON3.readValue("{\"source\":\"SUPER\"}", TenantAccessGrantRequest.class);
		assertEquals(Optional.empty(), permanent.optExpiresAt());
		assertEquals(TenantAccessGrantSource.SUPER, permanent.source());
	}

	@Test
	@DisplayName("a request that does not name its source is a violation: the 6.x bodies {} and {expiresAt} are refused")
	void sourceIsRequired() {
		assertEquals(Set.of("source"), violatedPaths(JACKSON3.readValue("{}", TenantAccessGrantRequest.class)));
		assertEquals(Set.of("source"), violatedPaths(JACKSON3.readValue(
				"{\"expiresAt\":\"2026-12-01T00:00:00Z\"}", TenantAccessGrantRequest.class)));
		assertEquals(Set.of("source"), violatedPaths(JACKSON3.readValue(
				"{\"source\":null}", TenantAccessGrantRequest.class)));
	}

	@Test
	@DisplayName("a source is bound by its exact name only: an ordinal, another case or an unknown name is refused")
	void sourceBindsByNameOnly() {
		for (ObjectMapper mapper : List.of(JACKSON3, JsonMapper.builder().build())) {
			for (String value : List.of("0", "\"0\"", "1", "\"2\"", "\"super\"", "\"\"", "true", "\"ADMIN\"")) {
				assertThrows(DatabindException.class,
						() -> mapper.readValue("{\"source\":" + value + "}", TenantAccessGrantRequest.class), value);
			}
			assertEquals(TenantAccessGrantSource.OPS,
					mapper.readValue("{\"source\":\"OPS\"}", TenantAccessGrantRequest.class).source());
		}
		assertEquals("\"TENANT\"", JACKSON3.writeValueAsString(TenantAccessGrantSource.TENANT));
	}

	@Test
	@DisplayName("a response carries source and adminProvisionedAt, and of(...) still settles live")
	void responseCarriesProvenance() {
		TenantAccessGrantResponse provisioned = TenantAccessGrantResponse.of(USER, GRANTED, Optional.of(LATER), GRANTED,
				TenantAccessGrantSource.SUPER, Optional.of(PROVISIONED));
		assertTrue(provisioned.live());
		assertEquals(Optional.of(PROVISIONED), provisioned.optAdminProvisionedAt());
		assertEquals(provisioned, JACKSON3.readValue(JACKSON3.writeValueAsString(provisioned),
				TenantAccessGrantResponse.class));
		assertEquals(Set.of("user", "grantedAt", "expiresAt", "live", "source", "adminProvisionedAt"),
				Set.copyOf(JACKSON3.valueToTree(provisioned).propertyNames()));

		TenantAccessGrantResponse catalogOnly = TenantAccessGrantResponse.of(USER, GRANTED, Optional.of(GRANTED), LATER,
				TenantAccessGrantSource.TENANT, Optional.empty());
		assertFalse(catalogOnly.live());
		assertEquals(Optional.empty(), catalogOnly.optAdminProvisionedAt());
		assertEquals(catalogOnly, JACKSON3.readValue(JACKSON3.writeValueAsString(catalogOnly),
				TenantAccessGrantResponse.class));
	}

	@Test
	@DisplayName("a grants listing round-trips its grants' provenance")
	void listingRoundTrips() {
		TenantGrantsResponse listing = new TenantGrantsResponse("U9000000000001", List.of(
				TenantAccessGrantResponse.of(USER, GRANTED, Optional.empty(), GRANTED, TenantAccessGrantSource.OPS,
						Optional.of(PROVISIONED)),
				TenantAccessGrantResponse.of(new GlobalUserKey(222_222L, -51_002L), GRANTED, Optional.empty(), GRANTED,
						TenantAccessGrantSource.TENANT, Optional.empty())));
		assertEquals(listing, JACKSON3.readValue(JACKSON3.writeValueAsString(listing), TenantGrantsResponse.class));
	}
}
