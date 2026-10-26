package dev.bluestep.global.dto.usage.threshold;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;

import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeName;

import dev.bluestep.global.dto.tenantaccess.TenantUserKey;
import dev.bluestep.global.dto.usage.ContractRelease;

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
 * consumer's Boot 4 defaults. Under them an unknown enum value or kind fails binding, an omitted
 * primitive fails binding, and an unknown property is ignored, which is what keeps a later additive
 * component safe for an older reader.</p>
 */
class UsageThresholdWireTest {

	private static final ObjectMapper JSON = JsonMapper.builder().build();
	private static final ObjectMapper CBOR = CBORMapper.builder().build();

	private static final String NOTIFY_THEN_BREAK = """
			[{"limit":1000,"kind":{"type":"notify","channels":["EMAIL"],
			  "recipients":[{"classid":7001,"seqnum":42}]}},
			 {"limit":2000,"kind":{"type":"break"}}]""";

	private static final String PINNED_THRESHOLD = """
			{"id":11,"version":3,"schemaName":"U1000001","metric":"HITS","category":"http",
			 "window":"DAILY","actions":%s,
			 "createdAt":"2026-09-07T10:15:00Z","updatedAt":"2026-09-08T11:30:00Z"}
			""".formatted(NOTIFY_THEN_BREAK);

	private static final String PINNED_REQUEST = """
			{"schemaName":"U1000001","metric":"HITS","category":"http","window":"DAILY",
			 "actions":%s,"expectedVersion":3}
			""".formatted(NOTIFY_THEN_BREAK);

	private static final String DAY_SPAN = """
			{"start":"2026-09-07T00:00:00Z","end":"2026-09-08T00:00:00Z"}""";

	private static final String PINNED_TRIP = """
			{"id":21,"thresholdId":11,"thresholdVersion":3,"actionIndex":0,
			 "limit":1000,"observed":1234,"breaks":false,"span":%s,
			 "trippedAt":"2026-09-07T13:45:00Z","clearedAt":null,"notifiedAt":"2026-09-07T13:46:00Z"}
			""".formatted(DAY_SPAN);

	private static final String PINNED_NOTIFICATION = """
			{"id":31,"tripId":21,"schemaName":"U1000001","thresholdId":11,"actionIndex":0,
			 "channel":"EMAIL","recipient":{"classid":7001,"seqnum":42},"metric":"HITS","category":"http",
			 "observed":1234,"limit":1000,"breaks":false,"span":%s,"trippedAt":"2026-09-07T13:45:00Z"}
			""".formatted(DAY_SPAN);

	private static final String PINNED_BATCH = """
			{"generatedAt":"2026-09-07T13:45:05Z","notifications":[%s]}
			""".formatted(PINNED_NOTIFICATION);

	private static final String PINNED_BREAKERS = """
			{"generatedAt":"2026-09-07T13:45:00Z","breakers":[
			 {"schemaName":"U1000001","metric":"HITS","category":"http","clearsAt":"2026-09-08T00:00:00Z"},
			 {"schemaName":"U1000001","metric":"STORAGE_FILE_BYTES","category":null,"clearsAt":null}]}
			""";

	private static final String PINNED_NOTIFICATION_REQUEST = """
			{"schemaNames":["U1000001"]}""";

	private static final String PINNED_ACK = """
			{"acks":[{"id":9,"outcome":"DELIVERED"},{"id":3,"outcome":"UNDELIVERABLE"}]}""";

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
	void notificationBatch_isPinned() {
		assertPinned(PINNED_BATCH, UsageThresholdFixtures.batch());
	}

	@Test
	void breakers_arePinned() {
		assertPinned(PINNED_BREAKERS, UsageThresholdFixtures.breakers());
	}

	@Test
	void notificationRequest_isPinned() {
		assertPinned(PINNED_NOTIFICATION_REQUEST, new UsageNotificationRequest(Set.of("U1000001")));
	}

	@Test
	void ack_isPinned() {
		assertPinned(PINNED_ACK, new UsageNotificationAckRequest(List.of(
				new UsageNotificationAck(9, UsageNotificationOutcome.DELIVERED),
				new UsageNotificationAck(3, UsageNotificationOutcome.UNDELIVERABLE))));
	}

	// --- order survives the wire -----------------------------------------------------------------

	/**
	 * Recipients keep the order they were given through JSON, not only through the constructor: a
	 * {@code Set} component would bind as a hash set and lose it before the constructor ran.
	 */
	@Test
	void recipients_keepTheirOrderThroughJson() {
		final UsageThresholdAction.Notify notify = JSON.readValue("""
				{"type":"notify","channels":["EMAIL"],"recipients":[
				 {"classid":7001,"seqnum":99},{"classid":7001,"seqnum":42},{"classid":1,"seqnum":5}]}""",
				UsageThresholdAction.Notify.class);
		final List<TenantUserKey> given = List.of(new TenantUserKey(7001, 99), new TenantUserKey(7001, 42),
				new TenantUserKey(1, 5));
		assertEquals(given, notify.recipients());
		assertEquals(given, JSON.readValue(JSON.writeValueAsString(notify), UsageThresholdAction.Notify.class)
				.recipients());
	}

	@Test
	void aRecipientNamedTwiceInJson_failsBinding() {
		final JacksonException refused = assertThrows(JacksonException.class, () -> JSON.readValue("""
				{"type":"notify","channels":["EMAIL"],"recipients":[
				 {"classid":7001,"seqnum":42},{"classid":7001,"seqnum":42}]}""",
				UsageThresholdAction.Notify.class));
		assertCause(refused, IllegalArgumentException.class, "twice");
	}

	@Test
	void acks_keepTheirOrderThroughJson() {
		final UsageNotificationAckRequest ack = JSON.readValue("""
				{"acks":[{"id":9,"outcome":"DELIVERED"},{"id":3,"outcome":"DELIVERED"},
				 {"id":100,"outcome":"UNDELIVERABLE"},{"id":1,"outcome":"DELIVERED"}]}""",
				UsageNotificationAckRequest.class);
		assertEquals(List.of(9L, 3L, 100L, 1L), ack.acks().stream().map(UsageNotificationAck::id).toList());
	}

	// --- absence ---------------------------------------------------------------------------------

	@Test
	void anAbsentKeyAndAnExplicitNull_bothBindAsAbsent() {
		final UsageTripState omitted = JSON.readValue("""
				{"id":1,"thresholdId":2,"thresholdVersion":0,"actionIndex":0,
				 "limit":5,"observed":5,"breaks":true,"trippedAt":"2026-09-07T13:45:00Z"}""",
				UsageTripState.class);
		final UsageTripState nulled = JSON.readValue("""
				{"id":1,"thresholdId":2,"thresholdVersion":0,"actionIndex":0,
				 "limit":5,"observed":5,"breaks":true,"trippedAt":"2026-09-07T13:45:00Z",
				 "span":null,"clearedAt":null,"notifiedAt":null}""", UsageTripState.class);
		assertEquals(omitted, nulled);
		assertTrue(omitted.optSpan().isEmpty());
		assertTrue(omitted.optClearedAt().isEmpty());
		assertTrue(omitted.optNotifiedAt().isEmpty());

		final UsageThresholdRequest create = JSON.readValue("""
				{"schemaName":"U1000001","metric":"STORAGE_DB_BYTES","window":"LATEST",
				 "actions":[{"limit":5,"kind":{"type":"break"}}]}""", UsageThresholdRequest.class);
		assertTrue(create.optCategory().isEmpty());
		assertTrue(create.optExpectedVersion().isEmpty());
	}

	/**
	 * An omitted primitive fails binding rather than defaulting to zero. That is what stops a trip's
	 * {@code actionIndex} silently binding as stage 0; a consumer that turns
	 * {@code FAIL_ON_NULL_FOR_PRIMITIVES} off loses it.
	 */
	@Test
	void anOmittedPrimitive_failsBinding() {
		assertThrows(JacksonException.class, () -> JSON.readValue("""
				{"id":1,"thresholdId":2,"thresholdVersion":0,
				 "limit":5,"observed":5,"breaks":true,"trippedAt":"2026-09-07T13:45:00Z"}""",
				UsageTripState.class));
		assertThrows(JacksonException.class, () -> JSON.readValue("""
				{"kind":{"type":"break"}}""", UsageThresholdAction.class));
	}

	/**
	 * A pod replaces its breaker map with each answer, so a list that binds as empty when its key is
	 * missing or misspelled would clear every circuit fleet-wide. It fails binding instead, and so
	 * does a notification batch without its list.
	 */
	@Test
	void anEnvelopeWithoutItsList_failsBinding() {
		final JacksonException refused = assertThrows(JacksonException.class, () -> JSON.readValue("""
				{"generatedAt":"2026-09-07T13:45:00Z"}""", UsageBreakerList.class));
		assertCause(refused, NullPointerException.class, "breakers");
		assertTrue(JSON.readValue("""
				{"generatedAt":"2026-09-07T13:45:00Z","breakers":[]}""", UsageBreakerList.class)
				.breakers().isEmpty());
		final JacksonException noNotifications = assertThrows(JacksonException.class, () -> JSON.readValue("""
				{"generatedAt":"2026-09-07T13:45:00Z"}""", UsageNotificationBatch.class));
		assertCause(noNotifications, NullPointerException.class, "notifications");
	}

	/** The bare mapper ignores a key it does not know, so a later additive component is safe. */
	@Test
	void anUnknownProperty_isIgnored() {
		assertEquals(new UsageBreakerList(UsageThresholdFixtures.TRIPPED, List.of()), JSON.readValue("""
				{"generatedAt":"2026-09-07T13:45:00Z","breakers":[],"extra":1}""", UsageBreakerList.class));
	}

	@Test
	void anOpsOnlyNotify_mayOmitRecipients() {
		final UsageThresholdAction action = JSON.readValue("""
				{"limit":5,"kind":{"type":"notify","channels":["OPS"]}}""", UsageThresholdAction.class);
		assertEquals(new UsageThresholdAction(5,
				new UsageThresholdAction.Notify(Set.of(NotifyChannel.OPS), List.of())), action);
	}

	@Test
	void aMissingRequiredKey_failsBindingAndNamesIt() {
		final JacksonException refused = assertThrows(JacksonException.class, () -> JSON.readValue("""
				{"metric":"HITS","window":"DAILY","actions":[{"limit":5,"kind":{"type":"break"}}]}""",
				UsageThresholdRequest.class));
		assertCause(refused, NullPointerException.class, "schemaName");
	}

	@Test
	void anIncoherentPayload_failsBindingWithTheRuleItBroke() {
		final JacksonException refused = assertThrows(JacksonException.class, () -> JSON.readValue("""
				{"schemaName":"U1000001","metric":"HITS","window":"LATEST",
				 "actions":[{"limit":5,"kind":{"type":"break"}}]}""", UsageThresholdRequest.class));
		assertCause(refused, IllegalArgumentException.class, "does not suit");
		final JacksonException peakBreak = assertThrows(JacksonException.class, () -> JSON.readValue("""
				{"schemaName":"U1000001","metric":"PAGE_MILLIS_PEAK","window":"DAILY",
				 "actions":[{"limit":5,"kind":{"type":"break"}}]}""", UsageThresholdRequest.class));
		assertCause(peakBreak, IllegalArgumentException.class, "only locks the tenant out");
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
	 * An unknown kind, channel or outcome fails binding. That is the contract, not a gap: the package
	 * javadoc puts the rule on web-global, which answers a caller only what its tag can bind.
	 */
	@Test
	void anUnknownKindChannelOrOutcome_failsBinding() {
		assertThrows(JacksonException.class, () -> JSON.readValue("""
				{"limit":5,"kind":{"type":"throttle"}}""", UsageThresholdAction.class));
		assertThrows(JacksonException.class, () -> JSON.readValue("""
				{"limit":5,"kind":{"channels":["OPS"]}}""", UsageThresholdAction.class));
		assertThrows(JacksonException.class, () -> JSON.readValue("""
				{"limit":5,"kind":{"type":"notify","channels":["SMS"],
				 "recipients":[{"classid":7001,"seqnum":42}]}}""", UsageThresholdAction.class));
		assertThrows(JacksonException.class, () -> JSON.readValue("""
				{"acks":[{"id":1,"outcome":"BOUNCED"}]}""", UsageNotificationAckRequest.class));
	}

	// --- names and releases ----------------------------------------------------------------------

	@Test
	void enumNames_arePinned() {
		assertEquals(List.of("HOURLY", "DAILY", "WEEKLY", "MONTHLY", "LATEST"),
				Arrays.stream(UsageThresholdWindow.values()).map(Enum::name).toList());
		assertEquals(List.of("EMAIL", "IN_APP", "OPS"),
				Arrays.stream(NotifyChannel.values()).map(Enum::name).toList());
		assertEquals(List.of("DELIVERED", "UNDELIVERABLE"),
				Arrays.stream(UsageNotificationOutcome.values()).map(Enum::name).toList());
		for (final Enum<?> constant : List.<Enum<?>>of(UsageThresholdWindow.LATEST, NotifyChannel.IN_APP,
				UsageNotificationOutcome.UNDELIVERABLE)) {
			assertEquals("\"" + constant.name() + "\"", JSON.writeValueAsString(constant));
		}
	}

	/**
	 * Each growable constant names the release that introduced it, which web-global's per-tag filter
	 * reads. A constant added later must say its own release, and these pins make that a decision.
	 */
	@Test
	void introducedIn_isPinnedForEveryGrowableConstantAndKind() {
		for (final UsageThresholdWindow window : UsageThresholdWindow.values()) {
			assertEquals(ContractRelease.V5_3_0, window.introducedIn(), window.name());
		}
		for (final NotifyChannel channel : NotifyChannel.values()) {
			assertEquals(ContractRelease.V5_3_0, channel.introducedIn(), channel.name());
		}
		for (final UsageNotificationOutcome outcome : UsageNotificationOutcome.values()) {
			assertEquals(ContractRelease.V5_3_0, outcome.introducedIn(), outcome.name());
		}
		assertEquals(ContractRelease.V5_3_0, UsageThresholdFixtures.emailAlice().introducedIn());
		assertEquals(ContractRelease.V5_3_0, new UsageThresholdAction.Break().introducedIn());
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
