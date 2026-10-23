package dev.bluestep.global.dto.usage.threshold;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

import java.util.Arrays;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;

import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeName;

import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.dataformat.cbor.CBORMapper;

/**
 * Pins every wire key and every name of the threshold shapes, and the polymorphism of an action's
 * kind.
 *
 * <p>web-global, web's pods and web's dispatcher bind the same records from different releases, so a
 * renamed key, a renamed constant or a renamed kind would compile clean everywhere and mis-bind
 * across a staggered deploy. These tests fail instead. The pinned JSON literals are the contract as
 * prose.</p>
 *
 * <p>The mappers are bare, as {@code OptionalWireContractTest}'s is: what binds here binds under any
 * consumer's Boot 4 defaults, and an unknown property or value fails here as it fails there.</p>
 */
class UsageThresholdWireTest {

	private static final ObjectMapper JSON = JsonMapper.builder().build();
	private static final ObjectMapper CBOR = CBORMapper.builder().build();

	private static final String NOTIFY_THEN_BREAK = """
			[{"level":1000,"kind":{"type":"notify","channels":["EMAIL"],
			  "recipients":[{"classid":7001,"seqnum":42}]}},
			 {"level":2000,"kind":{"type":"break"}}]""";

	private static final String PINNED_THRESHOLD = """
			{"id":11,"version":3,"schemaName":"U1000001","metric":"HITS","category":"http",
			 "window":"DAILY","actions":%s,
			 "createdAt":"2026-09-07T10:15:00Z","updatedAt":"2026-09-08T11:30:00Z"}
			""".formatted(NOTIFY_THEN_BREAK);

	private static final String PINNED_REQUEST = """
			{"schemaName":"U1000001","metric":"HITS","category":"http","window":"DAILY",
			 "actions":%s,"expectedVersion":3}
			""".formatted(NOTIFY_THEN_BREAK);

	private static final String PINNED_TRIP = """
			{"id":21,"thresholdId":11,"actionIndex":1,"windowStart":"2026-09-07T00:00:00Z",
			 "trippedAt":"2026-09-07T13:45:00Z","clearedAt":null,"lastNotifiedAt":"2026-09-07T13:46:00Z"}
			""";

	private static final String PINNED_NOTIFICATION = """
			{"id":31,"schemaName":"U1000001","thresholdId":11,"actionIndex":0,"channel":"EMAIL",
			 "recipients":[{"classid":7001,"seqnum":42}],"metric":"HITS","category":"http",
			 "observed":1234,"level":1000,
			 "window":{"start":"2026-09-07T00:00:00Z","end":"2026-09-08T00:00:00Z"}}
			""";

	private static final String PINNED_BREAKERS = """
			{"generatedAt":"2026-09-07T13:45:00Z","breakers":[
			 {"schemaName":"U1000001","metric":"HITS","category":"http","retryAfter":"2026-09-08T00:00:00Z"},
			 {"schemaName":"U1000001","metric":"STORAGE_FILE_BYTES","category":null,"retryAfter":null}]}
			""";

	private static final String PINNED_ACK = """
			{"ids":[9,3]}""";

	/** Both sides are parsed from text, so a long and an int of one value compare equal. */
	private static void assertPinned(final String pinned, final Object value) {
		assertEquals(JSON.readTree(pinned), JSON.readTree(JSON.writeValueAsString(value)),
				"the exact keys and values are the wire contract");
		assertEquals(value, JSON.readValue(pinned, value.getClass()), "the pinned JSON binds back");
		assertEquals(value, CBOR.readValue(CBOR.writeValueAsBytes(value), value.getClass()),
				"CBOR round-trips");
	}

	@Test
	void threshold_isPinned() {
		assertPinned(PINNED_THRESHOLD, UsageThresholdFixtures.threshold());
	}

	@Test
	void request_isPinned() {
		assertPinned(PINNED_REQUEST, UsageThresholdFixtures.request());
	}

	@Test
	void trip_isPinned_andItsDerivedStateStaysOffTheWire() {
		assertPinned(PINNED_TRIP, UsageThresholdFixtures.trip());
	}

	@Test
	void notification_isPinned() {
		assertPinned(PINNED_NOTIFICATION, UsageThresholdFixtures.notification());
	}

	@Test
	void breakers_arePinned() {
		assertPinned(PINNED_BREAKERS, UsageThresholdFixtures.breakers());
	}

	@Test
	void ack_isPinned() {
		assertPinned(PINNED_ACK, new UsageNotificationAckRequest(new LinkedHashSet<>(List.of(9L, 3L))));
	}

	// --- absence ---------------------------------------------------------------------------------

	@Test
	void anAbsentKeyAndAnExplicitNull_bothBindAsAbsent() {
		final UsageTripState omitted = JSON.readValue("""
				{"id":1,"thresholdId":2,"actionIndex":0,"trippedAt":"2026-09-07T13:45:00Z"}""",
				UsageTripState.class);
		final UsageTripState nulled = JSON.readValue("""
				{"id":1,"thresholdId":2,"actionIndex":0,"trippedAt":"2026-09-07T13:45:00Z",
				 "windowStart":null,"clearedAt":null,"lastNotifiedAt":null}""", UsageTripState.class);
		assertEquals(omitted, nulled);
		assertTrue(omitted.optWindowStart().isEmpty());
		assertTrue(omitted.optClearedAt().isEmpty());
		assertTrue(omitted.optLastNotifiedAt().isEmpty());

		final UsageThresholdRequest create = JSON.readValue("""
				{"schemaName":"U1000001","metric":"STORAGE_DB_BYTES","window":"LEVEL",
				 "actions":[{"level":5,"kind":{"type":"break"}}]}""", UsageThresholdRequest.class);
		assertTrue(create.optCategory().isEmpty());
		assertTrue(create.optExpectedVersion().isEmpty());
	}

	/**
	 * A pod replaces its breaker map with each answer, so a list that binds as empty when its key is
	 * missing or misspelled would clear every circuit fleet-wide. It fails binding instead.
	 */
	@Test
	void aBreakerListWithoutItsList_failsBinding() {
		final JacksonException refused = assertThrows(JacksonException.class, () -> JSON.readValue("""
				{"generatedAt":"2026-09-07T13:45:00Z"}""", UsageBreakerList.class));
		assertCause(refused, NullPointerException.class, "breakers");
		assertTrue(JSON.readValue("""
				{"generatedAt":"2026-09-07T13:45:00Z","breakers":[]}""", UsageBreakerList.class)
				.breakers().isEmpty());
	}

	@Test
	void anOpsOnlyNotify_mayOmitRecipients() {
		final UsageThresholdAction action = JSON.readValue("""
				{"level":5,"kind":{"type":"notify","channels":["OPS"]}}""", UsageThresholdAction.class);
		assertEquals(new UsageThresholdAction(5,
				new UsageThresholdAction.Notify(Set.of(NotifyChannel.OPS), Set.of())), action);
	}

	@Test
	void aMissingRequiredKey_failsBindingAndNamesIt() {
		final JacksonException refused = assertThrows(JacksonException.class, () -> JSON.readValue("""
				{"metric":"HITS","window":"DAILY","actions":[{"level":5,"kind":{"type":"break"}}]}""",
				UsageThresholdRequest.class));
		assertCause(refused, NullPointerException.class, "schemaName");
	}

	@Test
	void anIncoherentPayload_failsBindingWithTheRuleItBroke() {
		final JacksonException refused = assertThrows(JacksonException.class, () -> JSON.readValue("""
				{"schemaName":"U1000001","metric":"HITS","window":"LEVEL",
				 "actions":[{"level":5,"kind":{"type":"break"}}]}""", UsageThresholdRequest.class));
		assertCause(refused, IllegalArgumentException.class, "does not suit");
	}

	// --- polymorphism ----------------------------------------------------------------------------

	@Test
	void kindNames_arePinned_andEveryPermittedKindIsRegistered() {
		final Set<String> registered = new TreeSet<>();
		final JsonSubTypes subTypes = UsageThresholdAction.Kind.class.getAnnotation(JsonSubTypes.class);
		assertNotNull(subTypes);
		final Set<Class<?>> listed = new HashSet<>();
		for (final JsonSubTypes.Type type : subTypes.value()) {
			listed.add(type.value());
		}
		assertEquals(Set.copyOf(Arrays.asList(UsageThresholdAction.Kind.class.getPermittedSubclasses())), listed,
				"@JsonSubTypes lists exactly the permitted kinds");
		for (final Class<?> kind : UsageThresholdAction.Kind.class.getPermittedSubclasses()) {
			final JsonTypeName name = kind.getAnnotation(JsonTypeName.class);
			assertNotNull(name, kind.getSimpleName() + " declares its wire name");
			registered.add(name.value());
		}
		assertEquals(new TreeSet<>(Set.of("break", "notify")), registered);
	}

	@Test
	void eachKind_serializesUnderItsTypeName() {
		assertEquals(JSON.readTree("{\"type\":\"break\"}"),
				JSON.valueToTree((UsageThresholdAction.Kind) new UsageThresholdAction.Break()));
		assertEquals(UsageThresholdFixtures.emailAlice(),
				JSON.readValue("""
						{"type":"notify","channels":["EMAIL"],"recipients":[{"classid":7001,"seqnum":42}]}""",
						UsageThresholdAction.Kind.class));
	}

	/**
	 * An unknown kind or channel fails binding. That is the contract, not a gap: the package javadoc
	 * puts the rule on web-global, which never sends a caller vocabulary its release lacks.
	 */
	@Test
	void anUnknownKindOrChannel_failsBinding() {
		assertThrows(JacksonException.class, () -> JSON.readValue("""
				{"level":5,"kind":{"type":"throttle"}}""", UsageThresholdAction.class));
		assertThrows(JacksonException.class, () -> JSON.readValue("""
				{"level":5,"kind":{"channels":["OPS"]}}""", UsageThresholdAction.class));
		assertThrows(JacksonException.class, () -> JSON.readValue("""
				{"level":5,"kind":{"type":"notify","channels":["SMS"],
				 "recipients":[{"classid":7001,"seqnum":42}]}}""", UsageThresholdAction.class));
	}

	// --- names -----------------------------------------------------------------------------------

	@Test
	void enumNames_arePinned() {
		assertEquals(List.of("HOURLY", "DAILY", "WEEKLY", "MONTHLY", "LEVEL"),
				Arrays.stream(UsageThresholdWindow.values()).map(Enum::name).toList());
		assertEquals(List.of("EMAIL", "IN_APP", "OPS"),
				Arrays.stream(NotifyChannel.values()).map(Enum::name).toList());
		for (final UsageThresholdWindow window : UsageThresholdWindow.values()) {
			assertEquals("\"" + window.name() + "\"", JSON.writeValueAsString(window));
		}
		for (final NotifyChannel channel : NotifyChannel.values()) {
			assertEquals("\"" + channel.name() + "\"", JSON.writeValueAsString(channel));
		}
	}

	private static void assertCause(final Throwable thrown, final Class<? extends Throwable> type,
			final String messagePart) {
		for (Throwable cause = thrown; cause != null; cause = cause.getCause()) {
			if (type.isInstance(cause) && cause.getMessage() != null && cause.getMessage().contains(messagePart)) {
				return;
			}
		}
		fail("no " + type.getSimpleName() + " mentioning \"" + messagePart + "\" in the cause chain");
	}
}
