package dev.bluestep.global.dto.tenant;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.reflect.RecordComponent;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.hibernate.validator.messageinterpolation.ParameterMessageInterpolator;
import org.junit.jupiter.api.Test;

import dev.bluestep.global.dto.basetable.BaseTableKey;
import dev.bluestep.global.dto.tenantaccess.GlobalUserKey;
import dev.bluestep.global.dto.fleet.NamespaceName;
import dev.bluestep.global.dto.tenantaccess.ResellerKey;

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
 * Pins 6.0.0's tenant write shapes: their wire keys, absent against explicit null, the refusals'
 * reason spellings, the column-width bounds, and that a write maps from a read component by component.
 *
 * <p>web-global and every monolith build bind these from different releases, so a renamed key or
 * reason compiles clean on both sides and mis-binds across a staggered deploy. Strict on unknown
 * properties, so a derived getter leaking onto the wire fails the round trip.</p>
 */
class TenantWriteWireTest {

	private static final ObjectMapper JSON = JsonMapper.builder()
			.enable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
			.build();

	private static final Validator VALIDATOR = validator();

	private static final TenantKey TENANT = new TenantKey(TenantKey.CLASSID, 137_803L);

	private static final NamespaceName WEB_A = NamespaceName.of("web-a");

	/** One supplementary-plane character: two UTF-16 units, one code point, one {@code varchar} char. */
	private static final String CLEF = "𝄞";

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

	private static TenantSignIn signIn(String loginUrl) {
		return new TenantSignIn("ONE_STEP", "{}", loginUrl, null, 30, 5.0, "NONE");
	}

	private static TenantOrganization organization(String contactPhone) {
		return new TenantOrganization("desc", 25_000, null, null, new BaseTableKey(1_000_005L, 77L), null, null,
				"help@example.org", null, false, "Pat Doe", null, contactPhone, null, null, null, null, null,
				false, true, null);
	}

	private static TenantSettings settings(String name) {
		return new TenantSettings(name, "harbor", null, new ResellerKey(111_222L, 1L), WEB_A, 8L,
				new TenantFeatures(true, false, false, false, false, true, true, false, true, false, false, false,
						true),
				"cipher", "a,b", "fs1", 2, -1, 1_000.0, null, "help", signIn("https://in.example"),
				organization("555-0100"));
	}

	private static TenantLock lock(String mainMessage) {
		return new TenantLock(5, true, LocalDateTime.of(2026, 10, 1, 8, 30), 60, null, mainMessage, null);
	}

	private static final TenantActor CREATOR = new TenantActor.GlobalUser(new GlobalUserKey(222_222L, -180L));

	@Test
	void create_roundTripsWithItsFiveKeys() {
		TenantCreateRequest sent = new TenantCreateRequest(TENANT, "U137803", settings("Harbor Clinic"),
				lock("now"), CREATOR);

		JsonNode tree = JSON.valueToTree(sent);

		assertEquals(Set.of("tenant", "associationId", "settings", "lock", "creator"),
				Set.copyOf(tree.propertyNames()));
		assertEquals("global-user", tree.get("creator").get("kind").asString());
		assertEquals(sent, JSON.readValue(JSON.writeValueAsString(sent), TenantCreateRequest.class));
	}

	@Test
	void create_anOmittedKeyAndAnExplicitNullBothBindAsAbsent() {
		String settings = JSON.writeValueAsString(settings("Harbor Clinic"));
		String key = "\"tenant\":{\"classid\":111111,\"seqnum\":137803}";

		TenantCreateRequest omitted = JSON.readValue("{" + key + ",\"settings\":" + settings + "}",
				TenantCreateRequest.class);
		TenantCreateRequest nulled = JSON.readValue("{" + key + ",\"settings\":" + settings
				+ ",\"associationId\":null,\"lock\":null,\"creator\":null}", TenantCreateRequest.class);

		for (TenantCreateRequest read : List.of(omitted, nulled)) {
			assertTrue(read.optAssociationId().isEmpty());
			assertTrue(read.optLock().isEmpty());
			assertTrue(read.optCreator().isEmpty());
			assertEquals(Set.of(), violatedPaths(read));
		}
		assertEquals(omitted, nulled);
	}

	@Test
	void updateAndLock_roundTripWithTheirKeys() {
		TenantUpdateRequest update = new TenantUpdateRequest(settings("Harbor Clinic"), CREATOR);
		TenantLockUpdateRequest lockUpdate = new TenantLockUpdateRequest(lock("now"), null);

		assertEquals(Set.of("settings", "actor"), Set.copyOf(JSON.valueToTree(update).propertyNames()));
		assertEquals(update, JSON.readValue(JSON.writeValueAsString(update), TenantUpdateRequest.class));
		assertEquals(lockUpdate, JSON.readValue(JSON.writeValueAsString(lockUpdate),
				TenantLockUpdateRequest.class));
		assertTrue(JSON.readValue("{\"lock\":" + JSON.writeValueAsString(lock("now")) + "}",
				TenantLockUpdateRequest.class).optActor().isEmpty());
	}

	@Test
	void reservation_isItsKeyAlone() {
		assertEquals("{\"tenant\":{\"classid\":111111,\"seqnum\":137803}}",
				JSON.writeValueAsString(new TenantReservationResponse(TENANT)));
	}

	@Test
	void refusals_spellTheirReasonsByName() {
		assertEquals(List.of("KEY_TAKEN", "TENANT_ID_TAKEN"), names(TenantWriteRefusal.Reason.values()));
		assertEquals(List.of("NAMED_AS_ACTOR", "HAS_GRANTS"), names(TenantDeleteRefusal.Reason.values()));

		assertEquals("{\"reasons\":[\"HAS_GRANTS\"]}",
				JSON.writeValueAsString(new TenantDeleteRefusal(Set.of(TenantDeleteRefusal.Reason.HAS_GRANTS))));
		TenantWriteRefusal both = new TenantWriteRefusal(EnumSet.allOf(TenantWriteRefusal.Reason.class));
		assertEquals(both, JSON.readValue(JSON.writeValueAsString(both), TenantWriteRefusal.class));
		assertThrows(JacksonException.class,
				() -> JSON.readValue("{\"reasons\":[\"SOMETHING_NEW\"]}", TenantWriteRefusal.class));
	}

	@Test
	void refusals_copyTheirReasons() {
		Set<TenantWriteRefusal.Reason> reasons = new HashSet<>(Set.of(TenantWriteRefusal.Reason.KEY_TAKEN));
		TenantWriteRefusal refusal = new TenantWriteRefusal(reasons);
		reasons.add(TenantWriteRefusal.Reason.TENANT_ID_TAKEN);

		assertEquals(Set.of(TenantWriteRefusal.Reason.KEY_TAKEN), refusal.reasons());
	}

	@Test
	void settings_areBoundedAtTheirColumnWidthsCountedInCodePoints() {
		assertEquals(Set.of(), violatedPaths(settings("n".repeat(100))));
		assertEquals(Set.of(), violatedPaths(settings(CLEF.repeat(100))));
		assertEquals(Set.of("name"), violatedPaths(settings("n".repeat(101))));
		assertEquals(Set.of("name"), violatedPaths(settings(" ")));
	}

	@Test
	void settings_checkTheSignInAndOrganizationInsideThem() {
		TenantSettings ok = settings("Harbor Clinic");
		TenantSettings wideUrl = new TenantSettings(ok.name(), ok.shortName(), ok.mission(), ok.reseller(),
				ok.namespace(), ok.flags(), ok.features(), ok.requiredEndorsement(), ok.assignedServers(),
				ok.formulaServer(), ok.distributionCount(), ok.defaultEmailValidatedCountdown(),
				ok.pageLockMillis(), ok.pageLockWarningMillis(), ok.helpTopicTemplate(), signIn("u".repeat(256)),
				organization("5".repeat(21)));

		assertEquals(Set.of("signIn.loginUrl", "organization.contactPhone"), violatedPaths(wideUrl));
		assertEquals(Set.of("settings.signIn.loginUrl", "settings.organization.contactPhone"),
				violatedPaths(new TenantUpdateRequest(wideUrl, null)));
	}

	@Test
	void settings_refuseAnOmittedRequiredColumn() {
		String json = JSON.writeValueAsString(settings("Harbor Clinic"))
				.replace("\"rememberPassword\":\"NONE\"", "\"rememberPassword\":null")
				.replace("\"namespace\":\"web-a\",", "");

		assertEquals(Set.of("namespace", "signIn.rememberPassword"),
				violatedPaths(JSON.readValue(json, TenantSettings.class)));
	}

	@Test
	void settings_carryTheNamespaceAsABareStringAndRefuseAnInvalidOne() {
		String json = JSON.writeValueAsString(settings("Harbor Clinic"));

		assertTrue(json.contains("\"namespace\":\"web-a\","), json);
		assertThrows(JacksonException.class, () -> JSON.readValue(
				json.replace("\"namespace\":\"web-a\",", "\"namespace\":\"Web_A\","), TenantSettings.class));
	}

	@Test
	void lock_messagesAreBoundedWhereALockIsWritten() {
		assertEquals(Set.of(), violatedPaths(new TenantLockUpdateRequest(lock("m".repeat(2000)), null)));
		assertEquals(Set.of("lock.mainMessage"),
				violatedPaths(new TenantLockUpdateRequest(lock("m".repeat(2001)), null)));
		assertEquals(Set.of("lock.mainMessage"), violatedPaths(new TenantCreateRequest(TENANT, null,
				settings("Harbor Clinic"), lock("m".repeat(2001)), null)));
	}

	@Test
	void create_boundsTheAssociationId() {
		assertEquals(Set.of("associationId"), violatedPaths(new TenantCreateRequest(TENANT, "U1234567X",
				settings("Harbor Clinic"), null, null)));
	}

	/**
	 * The design's promise that a caller maps a read onto a write one way: every settings component is a
	 * {@link TenantRecordResponse} component of the same name and type.
	 */
	@Test
	void settings_mapFromTheRecordComponentByComponent() {
		Map<String, RecordComponent> read = Arrays.stream(TenantRecordResponse.class.getRecordComponents())
				.collect(Collectors.toMap(RecordComponent::getName, Function.identity()));

		for (RecordComponent written : TenantSettings.class.getRecordComponents()) {
			RecordComponent match = read.get(written.getName());
			assertTrue(match != null, written.getName() + " is not a TenantRecordResponse component");
			assertEquals(match.getGenericType(), written.getGenericType(), written.getName());
		}
	}

	private static List<String> names(Enum<?>[] values) {
		return Arrays.stream(values).map(Enum::name).toList();
	}
}
