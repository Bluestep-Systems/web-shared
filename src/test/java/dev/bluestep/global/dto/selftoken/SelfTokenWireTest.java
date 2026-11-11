package dev.bluestep.global.dto.selftoken;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

import org.hibernate.validator.messageinterpolation.ParameterMessageInterpolator;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import tools.jackson.databind.DatabindException;
import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

import dev.bluestep.global.dto.ToStringRedaction;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;

/** The 6.5.0 self-service token shapes (TK1). */
@DisplayName("6.5.0 self-token shapes")
class SelfTokenWireTest {

	private static final ObjectMapper JACKSON3 = JsonMapper.builder()
			.enable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
			.build();

	private static final Validator VALIDATOR = validator();

	private static final UUID ID = UUID.fromString("0b9a3c1e-6f1d-4e43-9b8c-2d3f4a5b6c7d");
	private static final Instant CREATED = Instant.parse("2026-11-10T10:00:00Z");
	private static final Instant EXPIRES = Instant.parse("2027-02-08T10:00:00Z");

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

	private static <T> T roundTrip(T value, Class<T> type) {
		return JACKSON3.readValue(JACKSON3.writeValueAsString(value), type);
	}

	@Test
	@DisplayName("the listing round-trips, with its wire names, and absent lists bind empty")
	void listingRoundTrips() {
		SelfTokenView full = new SelfTokenView(ID, "laptop", List.of("kb:read", "tokens:self"), CREATED, EXPIRES,
				CREATED, true);
		SelfTokenView legacy = new SelfTokenView(UUID.randomUUID(), "old", List.of("mcp"), null, null, null, false);
		SelfTokensResponse listing = new SelfTokensResponse(List.of(full, legacy));
		assertEquals(listing, roundTrip(listing, SelfTokensResponse.class));
		assertEquals(Set.of("id", "alias", "scopes", "createdAt", "expiresAt", "lastUsedAt", "current"),
				Set.copyOf(JACKSON3.valueToTree(full).propertyNames()));
		assertEquals(List.of(), JACKSON3.readValue("{}", SelfTokensResponse.class).tokens());
		SelfTokenView noScopes = JACKSON3.readValue(
				"{\"id\":\"" + ID + "\",\"alias\":\"a\",\"current\":false}", SelfTokenView.class);
		assertEquals(List.of(), noScopes.scopes());
		assertEquals(Optional.empty(), noScopes.optCreatedAt());
		assertEquals(Optional.empty(), noScopes.optExpiresAt());
		assertEquals(Optional.empty(), noScopes.optLastUsedAt());
		assertEquals(Optional.of(EXPIRES), full.optExpiresAt());
	}

	@Test
	@DisplayName("a mint request round-trips each expiry kind, and absent alias and scopes bind empty")
	void mintRequestRoundTrips() {
		for (SelfTokenMintRequest request : List.of(
				new SelfTokenMintRequest("ci", List.of("kb:read"), SelfTokenExpiry.DEFAULT, null),
				new SelfTokenMintRequest("ci", List.of("kb:read"), SelfTokenExpiry.NEVER, null),
				new SelfTokenMintRequest("ci", List.of("kb:read", "kb:push"), SelfTokenExpiry.AT, EXPIRES))) {
			assertEquals(request, roundTrip(request, SelfTokenMintRequest.class));
			assertEquals(Set.of(), violatedPaths(request));
		}
		SelfTokenMintRequest bare = JACKSON3.readValue("{\"expiry\":\"DEFAULT\"}", SelfTokenMintRequest.class);
		assertEquals("", bare.alias());
		assertEquals(List.of(), bare.scopes());
		assertEquals(Set.of("alias", "scopes", "expiry", "expiresAt"),
				Set.copyOf(JACKSON3.valueToTree(bare).propertyNames()));
	}

	@Test
	@DisplayName("expiresAt is present exactly when expiry is AT, and an absent expiry is a violation")
	void expiryInvariant() {
		assertEquals(Set.of("expiryCoherent"), violatedPaths(
				new SelfTokenMintRequest("ci", List.of("kb:read"), SelfTokenExpiry.AT, null)));
		assertEquals(Set.of("expiryCoherent"), violatedPaths(
				new SelfTokenMintRequest("ci", List.of("kb:read"), SelfTokenExpiry.NEVER, EXPIRES)));
		assertEquals(Set.of("expiryCoherent"), violatedPaths(
				new SelfTokenMintRequest("ci", List.of("kb:read"), SelfTokenExpiry.DEFAULT, EXPIRES)));
		assertEquals(Set.of("expiry"), violatedPaths(JACKSON3.readValue("{\"alias\":\"ci\"}",
				SelfTokenMintRequest.class)));
	}

	@Test
	@DisplayName("an expiry kind the app does not know fails to bind: it is never read as DEFAULT or NEVER")
	void unknownExpiryKindIsRefused() {
		assertThrows(DatabindException.class,
				() -> JACKSON3.readValue("{\"expiry\":\"FOREVER\"}", SelfTokenMintRequest.class));
	}

	/** Codex WS650 review: Jackson binds an enum from its ordinal unless told otherwise; 1 would be NEVER. */
	@Test
	@DisplayName("an expiry is bound by its exact name only: 1, \"1\", lower case and blank are refused, on any mapper")
	void expiryBindsByNameOnly() {
		for (ObjectMapper mapper : List.of(JACKSON3, JsonMapper.builder().build())) {
			for (String value : List.of("1", "\"1\"", "0", "\"0\"", "\"never\"", "\"\"", "true")) {
				assertThrows(DatabindException.class,
						() -> mapper.readValue("{\"alias\":\"a\",\"scopes\":[\"kb:read\"],\"expiry\":" + value + "}",
								SelfTokenMintRequest.class), value);
			}
			assertEquals(SelfTokenExpiry.NEVER, mapper.readValue("{\"expiry\":\"NEVER\"}",
					SelfTokenMintRequest.class).expiry());
		}
		assertEquals("\"NEVER\"", JACKSON3.writeValueAsString(SelfTokenExpiry.NEVER));
	}

	@Test
	@DisplayName("a refusal reason is bound by its exact name only: an ordinal is UNKNOWN, never a real reason")
	void reasonBindsByNameOnly() {
		for (String value : List.of("\"1\"", "\"0\"", "\"scope_escalation\"")) {
			assertEquals(SelfTokenRefusal.Reason.UNKNOWN, JACKSON3.readValue(
					"{\"reason\":" + value + ",\"message\":\"m\"}", SelfTokenRefusal.class).reason(), value);
		}
		for (String value : List.of("1", "0")) {
			SelfTokenRefusal.Reason bound;
			try {
				bound = JACKSON3.readValue("{\"reason\":" + value + ",\"message\":\"m\"}", SelfTokenRefusal.class)
						.reason();
			}
			catch (DatabindException refused) {
				continue;
			}
			assertEquals(SelfTokenRefusal.Reason.UNKNOWN, bound, value);
		}
		assertEquals("\"SCOPE_ESCALATION\"", JACKSON3.writeValueAsString(SelfTokenRefusal.Reason.SCOPE_ESCALATION));
	}

	@Test
	@DisplayName("a minted token round-trips, and its secret never reaches toString")
	void mintedRoundTripsAndRedacts() {
		SelfTokenMinted minted = new SelfTokenMinted(ID, "b6pt_s3cretvalue", "ci", List.of("kb:read"), EXPIRES);
		assertEquals(minted, roundTrip(minted, SelfTokenMinted.class));
		assertEquals(Set.of("id", "token", "alias", "scopes", "expiresAt"),
				Set.copyOf(JACKSON3.valueToTree(minted).propertyNames()));
		ToStringRedaction.assertRedacts(minted, "token", "s3cretvalue");
		SelfTokenMinted never = new SelfTokenMinted(ID, "b6pt_x", "ci", List.of("mcp"), null);
		assertEquals(never, roundTrip(never, SelfTokenMinted.class));
		assertEquals(Optional.empty(), never.optExpiresAt());
	}

	@Test
	@DisplayName("a refusal round-trips each reason, with and without a scope")
	void refusalRoundTrips() {
		for (SelfTokenRefusal.Reason reason : SelfTokenRefusal.Reason.values()) {
			SelfTokenRefusal refusal = new SelfTokenRefusal(reason, null, null, "no");
			assertEquals(refusal, roundTrip(refusal, SelfTokenRefusal.class));
		}
		SelfTokenRefusal escalation = new SelfTokenRefusal(SelfTokenRefusal.Reason.SCOPE_ESCALATION, "kb:push", null,
				"The presenting token does not hold kb:push.");
		assertEquals(escalation, roundTrip(escalation, SelfTokenRefusal.class));
		assertEquals(Optional.of("kb:push"), escalation.optScope());
		assertEquals(Set.of("reason", "scope", "presentingExpiresAt", "message"),
				Set.copyOf(JACKSON3.valueToTree(escalation).propertyNames()));
		SelfTokenRefusal exceeds = new SelfTokenRefusal(SelfTokenRefusal.Reason.EXPIRY_EXCEEDS_PRESENTING, null,
				EXPIRES, "too late");
		assertEquals(exceeds, roundTrip(exceeds, SelfTokenRefusal.class));
		assertEquals(Optional.of(EXPIRES), exceeds.optPresentingExpiresAt());
		assertEquals(Optional.empty(), escalation.optPresentingExpiresAt());
	}

	/** Ruling 2026-11-09 "WS review 2": a reason a newer app adds binds as UNKNOWN, on any mapper. */
	@Test
	@DisplayName("a reason added later binds as UNKNOWN; an absent reason or message binds as UNKNOWN and empty")
	void newerReasonBindsAsUnknown() {
		assertEquals(SelfTokenRefusal.Reason.UNKNOWN,
				JACKSON3.readValue("{\"reason\":\"QUOTA_FROZEN\",\"message\":\"m\"}", SelfTokenRefusal.class)
						.reason());
		assertEquals(SelfTokenRefusal.Reason.UNKNOWN, JsonMapper.builder().build()
				.readValue("{\"reason\":\"QUOTA_FROZEN\"}", SelfTokenRefusal.class).reason());
		SelfTokenRefusal empty = JACKSON3.readValue("{}", SelfTokenRefusal.class);
		assertEquals(SelfTokenRefusal.Reason.UNKNOWN, empty.reason());
		assertEquals("", empty.message());
		assertEquals(Optional.empty(), empty.optScope());
		assertEquals(Optional.empty(), empty.optPresentingExpiresAt());
	}

	@Test
	@DisplayName("collections are copied: a shape cannot change once built")
	void collectionsAreCopied() {
		List<String> scopes = new java.util.ArrayList<>(List.of("kb:read"));
		SelfTokenView view = new SelfTokenView(ID, "a", scopes, null, null, null, false);
		scopes.add("kb:push");
		assertEquals(List.of("kb:read"), view.scopes());
		assertThrows(UnsupportedOperationException.class, () -> view.scopes().add("x"));
		assertFalse(view.current());
		assertTrue(new SelfTokenView(ID, "a", List.of(), null, null, null, true).current());
	}
}
