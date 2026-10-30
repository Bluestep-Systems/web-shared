package dev.bluestep.global.dto.domain;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import org.hibernate.validator.messageinterpolation.ParameterMessageInterpolator;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import dev.bluestep.global.dto.basetable.BaseTableKey;
import dev.bluestep.global.dto.tenant.TenantActor;
import dev.bluestep.global.dto.tenant.TenantKey;
import dev.bluestep.global.dto.tenantaccess.GlobalUserKey;
import dev.bluestep.global.dto.tenantaccess.TenantUserKey;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

/**
 * Pins 6.0.0's domain shapes: their wire keys, absent against explicit null, the sign-in kinds' and
 * refusal reasons' spellings, and the host rule.
 *
 * <p>Strict on unknown properties, so a derived getter leaking onto the wire fails the round trip.</p>
 */
class DomainWireTest {

	private static final ObjectMapper JSON = JsonMapper.builder()
			.enable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
			.build();

	private static final Validator VALIDATOR = validator();

	private static final TenantKey TENANT = new TenantKey(TenantKey.CLASSID, 137_803L);
	private static final DomainKey DOMAIN = new DomainKey(DomainKey.CLASSID, 5_531L);
	private static final BaseTableKey UNIT = new BaseTableKey(111_000L, 12L);
	private static final LoginTypeSetting OAUTH = new LoginTypeSetting(LoginTypeName.TENANT_OAUTH2,
			"{\"issuer\":\"https://id.example\"}");

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

	private static DomainCreateRequest create(String host) {
		return new DomainCreateRequest(TENANT, host, UNIT, "harbor", 0L, 300, OAUTH,
				new TenantActor.GlobalUser(new GlobalUserKey(222_222L, -180L)));
	}

	@Test
	void record_roundTripsAndPinsItsKeys() {
		DomainRecordResponse sent = new DomainRecordResponse(DOMAIN, "harbor.example.org", TENANT, UNIT, "harbor",
				4L, 120, OAUTH, LocalDateTime.of(2020, 1, 2, 3, 4, 5), LocalDateTime.of(2026, 10, 28, 9, 0),
				new TenantActor.GlobalUser(new GlobalUserKey(222_222L, -180L)),
				new TenantActor.TenantUser(TENANT, new TenantUserKey(222_222L, 42L)));

		JsonNode tree = JSON.valueToTree(sent);

		assertEquals(Set.of("domain", "host", "tenant", "organization", "domainPrefix", "flags",
				"ingressReadTimeoutSeconds", "signIn", "created", "modified", "creator", "modifier"),
				Set.copyOf(tree.propertyNames()));
		assertEquals("{\"classid\":120100,\"seqnum\":5531}", tree.get("domain").toString());
		assertEquals("{\"type\":\"TENANT_OAUTH2\",\"data\":\"{\\\"issuer\\\":\\\"https://id.example\\\"}\"}",
				tree.get("signIn").toString());
		assertEquals(sent, JSON.readValue(JSON.writeValueAsString(sent), DomainRecordResponse.class));
	}

	@Test
	void record_anOmittedKeyAndAnExplicitNullBothBindAsAbsent() {
		String required = "\"domain\":{\"classid\":120100,\"seqnum\":5531},\"host\":\"harbor.example.org\","
				+ "\"tenant\":{\"classid\":111111,\"seqnum\":137803},\"flags\":0";

		DomainRecordResponse omitted = JSON.readValue("{" + required + "}", DomainRecordResponse.class);
		DomainRecordResponse nulled = JSON.readValue("{" + required + ",\"organization\":null,"
				+ "\"domainPrefix\":null,\"ingressReadTimeoutSeconds\":null,\"signIn\":null,\"created\":null,"
				+ "\"modified\":null,\"creator\":null,\"modifier\":null}", DomainRecordResponse.class);

		assertEquals(omitted, nulled);
		assertTrue(omitted.optOrganization().isEmpty());
		assertTrue(omitted.optDomainPrefix().isEmpty());
		assertTrue(omitted.optIngressReadTimeoutSeconds().isEmpty());
		assertTrue(omitted.optSignIn().isEmpty());
		assertTrue(omitted.optCreator().isEmpty());
	}

	@Test
	void createAndUpdate_roundTripWithTheirKeys() {
		DomainCreateRequest create = create("harbor.example.org");
		DomainUpdateRequest update = new DomainUpdateRequest("harbor.example.org", null, null, 0L, null, null, null);

		assertEquals(Set.of("tenant", "host", "organization", "domainPrefix", "flags", "ingressReadTimeoutSeconds",
				"signIn", "creator"), Set.copyOf(JSON.valueToTree(create).propertyNames()));
		assertEquals(Set.of("host", "organization", "domainPrefix", "flags", "ingressReadTimeoutSeconds", "signIn",
				"actor"), Set.copyOf(JSON.valueToTree(update).propertyNames()));
		assertEquals(create, JSON.readValue(JSON.writeValueAsString(create), DomainCreateRequest.class));
		assertEquals(update, JSON.readValue(JSON.writeValueAsString(update), DomainUpdateRequest.class));
		assertTrue(JSON.readValue("{\"host\":\"harbor.example.org\",\"flags\":0}", DomainUpdateRequest.class)
				.optActor().isEmpty());
	}

	@Test
	void loginTypeNames_areTheMonolithsTypeNames() {
		assertEquals(List.of("TENANT_OAUTH2", "GENERAL_OAUTH2", "ONE_STEP", "TWO_STEP", "CUSTOM"),
				Arrays.stream(LoginTypeName.values()).map(Enum::name).toList());
		assertThrows(JacksonException.class,
				() -> JSON.readValue("{\"type\":\"OPENID_CONNECT\",\"data\":null}", LoginTypeSetting.class));
		assertTrue(JSON.readValue("{\"type\":\"ONE_STEP\"}", LoginTypeSetting.class).optData().isEmpty());
	}

	@Test
	void signIn_requiresItsType() {
		DomainCreateRequest typeless = JSON.readValue(JSON.writeValueAsString(create("harbor.example.org"))
				.replace("\"type\":\"TENANT_OAUTH2\"", "\"type\":null"), DomainCreateRequest.class);

		assertEquals(Set.of("signIn.type"), violatedPaths(typeless));
	}

	@Test
	void refusal_spellsItsReasonsByName() {
		assertEquals(List.of("HOST_TAKEN", "RESERVED", "TENANT_ABSENT"),
				Arrays.stream(DomainWriteRefusal.Reason.values()).map(Enum::name).toList());
		DomainWriteRefusal all = new DomainWriteRefusal(EnumSet.allOf(DomainWriteRefusal.Reason.class));
		assertEquals(all, JSON.readValue(JSON.writeValueAsString(all), DomainWriteRefusal.class));
		assertEquals("{\"reasons\":[\"RESERVED\"]}",
				JSON.writeValueAsString(new DomainWriteRefusal(Set.of(DomainWriteRefusal.Reason.RESERVED))));
	}

	@Test
	void ingressRow_pinsItsKeysAndAbsence() {
		DomainIngressRow row = new DomainIngressRow("a.harbor.example.org", 120, "a", "harbor.example.org");

		assertEquals("{\"host\":\"a.harbor.example.org\",\"ingressReadTimeoutSeconds\":120,\"domainPrefix\":\"a\","
				+ "\"wildcard\":\"harbor.example.org\"}", JSON.writeValueAsString(row));
		DomainIngressRow bare = JSON.readValue("{\"host\":\"harbor.example.org\"}", DomainIngressRow.class);
		assertTrue(bare.optWildcard().isEmpty());
		assertTrue(bare.optDomainPrefix().isEmpty());
		assertTrue(bare.optIngressReadTimeoutSeconds().isEmpty());
	}

	@Test
	void domainKey_rendersAsClassidColonSeqnum() {
		assertEquals("120100:5531", DOMAIN.toString());
	}

	@ParameterizedTest
	@ValueSource(strings = {"harbor.example.org", "a.b", "x-1.example.org", "123.example.org", "a1.example.c0m"})
	void host_aServableNameIsAccepted(String host) {
		assertEquals(Set.of(), violatedPaths(create(host)));
		assertEquals(Set.of(), violatedPaths(new DomainUpdateRequest(host, null, null, 0L, null, null, null)));
	}

	@ParameterizedTest
	@ValueSource(strings = {"Harbor.example.org", "localhost", "10.0.0.1", "-a.example.org", "a-.example.org",
		"a_b.example.org", "a..example.org", "example.org.", ".example.org", "a.example.123", " "})
	void host_anUnservableNameIsRefused(String host) {
		assertEquals(Set.of("host"), violatedPaths(create(host)));
		assertEquals(Set.of("host"), violatedPaths(new DomainUpdateRequest(host, null, null, 0L, null, null, null)));
	}

	@Test
	void host_holdsTheLabelAndTotalLengths() {
		String label63 = "a".repeat(63);
		assertEquals(Set.of(), violatedPaths(create(label63 + ".example.org")));
		assertEquals(Set.of("host"), violatedPaths(create("a".repeat(64) + ".example.org")));

		String name253 = String.join(".", label63, label63, label63, "a".repeat(57), "org");
		assertEquals(253, name253.length());
		assertEquals(Set.of(), violatedPaths(create(name253)));
		assertEquals(Set.of("host"), violatedPaths(create("b" + name253)));
	}

	@Test
	void create_refusesANonPositiveTimeoutAndAWidePrefix() {
		DomainCreateRequest ok = create("harbor.example.org");
		assertEquals(Set.of("ingressReadTimeoutSeconds", "domainPrefix"), violatedPaths(new DomainCreateRequest(
				ok.tenant(), ok.host(), null, "p".repeat(257), 0L, 0, null, null)));
		assertEquals(Set.of(), violatedPaths(new DomainCreateRequest(ok.tenant(), ok.host(), null, "p".repeat(256),
				0L, null, null, null)));
	}

	@Test
	void create_requiresItsTenant() {
		DomainCreateRequest tenantless = JSON.readValue("{\"host\":\"harbor.example.org\",\"flags\":0}",
				DomainCreateRequest.class);

		assertEquals(Set.of("tenant"), violatedPaths(tenantless));
	}
}
