package dev.bluestep.global.dto;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
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

import dev.bluestep.global.dto.accesstoken.TokenCheckResponse;
import dev.bluestep.global.dto.accesstoken.TokenScope;
import dev.bluestep.global.dto.globaluserrecord.GlobalUserAccessResponse;
import dev.bluestep.global.dto.globaluserrecord.GlobalUserRecordResponse;
import dev.bluestep.global.dto.globaluserrecord.UntenantedAuthenticationOutcome;
import dev.bluestep.global.dto.globaluserrecord.UntenantedAuthenticationRequest;
import dev.bluestep.global.dto.globaluserrecord.UntenantedAuthenticationResponse;
import dev.bluestep.global.dto.globaluserrecord.UntenantedUsernameLookupRequest;
import dev.bluestep.global.dto.historymanager.HistoryManagerGrantRefusal;
import dev.bluestep.global.dto.historymanager.HistoryManagerGrantRequest;
import dev.bluestep.global.dto.historymanager.HistoryManagerGrantResponse;
import dev.bluestep.global.dto.historymanager.HistoryManagerGrantsResponse;
import dev.bluestep.global.dto.historymanager.HistoryManagerRevokeRequest;
import dev.bluestep.global.dto.tenant.TenantActor;
import dev.bluestep.global.dto.tenantaccess.GlobalUserKey;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;

/**
 * The 6.4.0 shapes: token scopes and the check's answer, the tenant-less sign-in, the access facts, and the
 * history-manager grants.
 */
@DisplayName("6.4.0 gateway shapes")
class GatewayContractWireTest {

	private static final String CANDIDATE = "s3cret-candidate";

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

	private static GlobalUserRecordResponse account() {
		return new GlobalUserRecordResponse(new GlobalUserKey(222_222L, -7L), "Ada", "Example", "ada", null, null,
				true, false, null, null, false, null, List.of(), 3L);
	}

	@Test
	void everyScopeRoundTripsThroughItsWireSpelling() {
		for (TokenScope scope : TokenScope.values()) {
			assertEquals(Optional.of(scope), TokenScope.fromWire(scope.wire()));
		}
		assertEquals("kb:read", TokenScope.KB_READ.wire());
		assertEquals("tokens:self", TokenScope.TOKENS_SELF.wire());
		assertEquals("kb:rewrite", TokenScope.KB_REWRITE.wire());
	}

	@Test
	void theRewriteScopeParsesExactlyAndNothingNearItDoes() {
		assertEquals(Optional.of(TokenScope.KB_REWRITE), TokenScope.fromWire("kb:rewrite"));
		assertEquals(Optional.empty(), TokenScope.fromWire("KB:REWRITE"));
		assertEquals(Optional.empty(), TokenScope.fromWire("kb:rewrite "));
		assertEquals(Optional.empty(), TokenScope.fromWire("kb:force"));
		assertEquals(Optional.empty(), TokenScope.fromWire("kb:*"));
		assertEquals(Set.of("mcp", "kb:read", "kb:push", "kb:rewrite", "registry:read", "registry:push", "tokens:self"),
				Arrays.stream(TokenScope.values()).map(TokenScope::wire).collect(Collectors.toSet()));
	}

	@Test
	void aSpellingOutsideTheVocabularyNamesNothing() {
		assertEquals(Optional.empty(), TokenScope.fromWire("vicarious"));
		assertEquals(Optional.empty(), TokenScope.fromWire("KB:READ"));
		assertEquals(Optional.empty(), TokenScope.fromWire(""));
	}

	@Test
	void theTokenCheckAnswerRoundTripsWithItsTwoKeys() {
		TokenCheckResponse response = new TokenCheckResponse(new GlobalUserKey(222_222L, -42L),
				List.of("kb:read", "registry:read"));
		JsonNode tree = JACKSON3.valueToTree(response);
		assertEquals(Set.of("user", "scopes"), Set.copyOf(tree.propertyNames()));
		assertEquals(response, JACKSON3.readValue(JACKSON3.writeValueAsString(response), TokenCheckResponse.class));
	}

	@Test
	void anOmittedScopesArrayMeansNoScopesAndAnUnknownOneBinds() {
		TokenCheckResponse bare = JACKSON3.readValue("{\"user\":{\"classid\":222222,\"seqnum\":-42}}",
				TokenCheckResponse.class);
		assertEquals(List.of(), bare.scopes());
		TokenCheckResponse newer = JACKSON3.readValue(
				"{\"user\":{\"classid\":222222,\"seqnum\":-42},\"scopes\":[\"mcp\",\"from-the-future\"]}",
				TokenCheckResponse.class);
		assertEquals(List.of("mcp", "from-the-future"), newer.scopes());
	}

	@Test
	void theUntenantedRequestsAreBoundedAndTheCandidateIsNeverPrinted() {
		assertEquals(Set.of(), violatedPaths(new UntenantedUsernameLookupRequest("ada")));
		assertEquals(Set.of("username"), violatedPaths(new UntenantedUsernameLookupRequest(" ")));
		assertEquals(Set.of("username"), violatedPaths(new UntenantedUsernameLookupRequest("u".repeat(51))));
		assertEquals(Set.of(), violatedPaths(new UntenantedAuthenticationRequest("u".repeat(50), CANDIDATE)));
		assertEquals(Set.of("candidate"), violatedPaths(new UntenantedAuthenticationRequest("ada", "")));
		assertEquals(Set.of(), violatedPaths(new UntenantedAuthenticationRequest("ada", "   ")));
		UntenantedAuthenticationRequest request = new UntenantedAuthenticationRequest("ada", CANDIDATE);
		assertFalse(request.toString().contains(CANDIDATE), request.toString());
		assertTrue(request.toString().contains("ada"));
		assertEquals(Set.of("username", "candidate"),
				Set.copyOf(JACKSON3.valueToTree(request).propertyNames()));
	}

	@Test
	void eachUntenantedOutcomeRoundTrips() {
		for (UntenantedAuthenticationResponse response : List.of(
				UntenantedAuthenticationResponse.match(account(), true),
				UntenantedAuthenticationResponse.match(account(), false),
				UntenantedAuthenticationResponse.noMatch(),
				UntenantedAuthenticationResponse.legacyCredential(),
				UntenantedAuthenticationResponse.disabled())) {
			String json = JACKSON3.writeValueAsString(response);
			assertEquals(response, JACKSON3.readValue(json, UntenantedAuthenticationResponse.class), json);
		}
		JsonNode tree = JACKSON3.valueToTree(UntenantedAuthenticationResponse.disabled());
		assertEquals("DISABLED", tree.get("outcome").asString());
	}

	@Test
	void onlyAMatchCarriesARecordOrARewrite() {
		assertThrows(IllegalArgumentException.class,
				() -> new UntenantedAuthenticationResponse(UntenantedAuthenticationOutcome.MATCH, null, false));
		assertThrows(IllegalArgumentException.class,
				() -> new UntenantedAuthenticationResponse(UntenantedAuthenticationOutcome.DISABLED, account(), false));
		assertThrows(IllegalArgumentException.class,
				() -> new UntenantedAuthenticationResponse(UntenantedAuthenticationOutcome.NO_MATCH, null, true));
	}

	@Test
	void aBodyThatBreaksTheShapeIsRefusedWhereItIsBound() {
		assertThrows(DatabindException.class, () -> JACKSON3.readValue(
				"{\"outcome\":\"MATCH\",\"record\":null,\"needsRewrite\":false}",
				UntenantedAuthenticationResponse.class));
		assertThrows(DatabindException.class, () -> JACKSON3.readValue(
				"{\"outcome\":\"SOMETHING_NEW\",\"record\":null,\"needsRewrite\":false}",
				UntenantedAuthenticationResponse.class));
	}

	@Test
	void theAccessFactsRoundTripWithTheirSixKeys() {
		GlobalUserAccessResponse facts = new GlobalUserAccessResponse(new GlobalUserKey(222_222L, -7L), false, true,
				true, List.of("U12", "U40"), true);
		JsonNode tree = JACKSON3.valueToTree(facts);
		assertEquals(Set.of("user", "disabled", "superUser", "knowledgeBaseManager", "vouchedTenantIds",
				"historyManager"), Set.copyOf(tree.propertyNames()));
		assertTrue(tree.get("historyManager").asBoolean());
		assertEquals(facts, JACKSON3.readValue(JACKSON3.writeValueAsString(facts), GlobalUserAccessResponse.class));
	}

	// ---------------------------------------------------------------- history-manager grants

	private static final GlobalUserKey GRANTEE = new GlobalUserKey(222_222L, -7L);
	private static final GlobalUserKey GRANTOR = new GlobalUserKey(222_222L, -8L);

	@Test
	void theGrantRequestsCarryAGlobalUserActorAndRequireOne() {
		HistoryManagerGrantRequest grant = new HistoryManagerGrantRequest(new TenantActor.GlobalUser(GRANTOR));
		String json = JACKSON3.writeValueAsString(grant);
		assertEquals(grant, JACKSON3.readValue(json, HistoryManagerGrantRequest.class), json);
		assertEquals("global-user", JACKSON3.valueToTree(grant).get("actor").get("kind").asString());
		HistoryManagerRevokeRequest revoke = new HistoryManagerRevokeRequest(new TenantActor.GlobalUser(GRANTOR));
		assertEquals(revoke, JACKSON3.readValue(JACKSON3.writeValueAsString(revoke), HistoryManagerRevokeRequest.class));
		assertEquals(Set.of("actor"), violatedPaths(JACKSON3.readValue("{}", HistoryManagerGrantRequest.class)));
		assertEquals(Set.of("actor"), violatedPaths(JACKSON3.readValue("{}", HistoryManagerRevokeRequest.class)));
	}

	@Test
	void aStandingAndARevokedGrantRoundTrip() {
		HistoryManagerGrantResponse standing = new HistoryManagerGrantResponse(GRANTEE, GRANTOR,
				Instant.parse("2026-11-09T10:00:00Z"), null, null, true);
		HistoryManagerGrantResponse closed = new HistoryManagerGrantResponse(GRANTEE, GRANTOR,
				Instant.parse("2026-11-09T10:00:00Z"), Instant.parse("2026-11-10T10:00:00Z"), GRANTEE, false);
		HistoryManagerGrantsResponse listing = new HistoryManagerGrantsResponse(List.of(closed, standing));
		assertEquals(listing, JACKSON3.readValue(JACKSON3.writeValueAsString(listing), HistoryManagerGrantsResponse.class));
		assertEquals(Set.of("user", "grantedBy", "granted", "revoked", "revokedBy", "effective"),
				Set.copyOf(JACKSON3.valueToTree(closed).propertyNames()));
		assertEquals(List.of(), JACKSON3.readValue("{}", HistoryManagerGrantsResponse.class).grants());
	}

	@Test
	void aGrantResponseKeepsRevocationWhole() {
		Instant at = Instant.parse("2026-11-09T10:00:00Z");
		assertThrows(IllegalArgumentException.class,
				() -> new HistoryManagerGrantResponse(GRANTEE, GRANTOR, at, at, null, false));
		assertThrows(IllegalArgumentException.class,
				() -> new HistoryManagerGrantResponse(GRANTEE, GRANTOR, at, null, GRANTOR, false));
		assertThrows(IllegalArgumentException.class,
				() -> new HistoryManagerGrantResponse(GRANTEE, GRANTOR, at, at, GRANTOR, true));
	}

	@Test
	void aRefusalNamesKnownReasonsOnly() {
		HistoryManagerGrantRefusal refusal = new HistoryManagerGrantRefusal(
				Set.of(HistoryManagerGrantRefusal.Reason.SELF_GRANT));
		assertEquals(refusal, JACKSON3.readValue(JACKSON3.writeValueAsString(refusal), HistoryManagerGrantRefusal.class));
		assertThrows(IllegalArgumentException.class, () -> new HistoryManagerGrantRefusal(Set.of()));
		assertThrows(DatabindException.class,
				() -> JACKSON3.readValue("{\"reasons\":[\"SOMETHING_NEW\"]}", HistoryManagerGrantRefusal.class));
	}
}
