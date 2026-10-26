package dev.bluestep.global.dto.usage.threshold;

import static dev.bluestep.global.dto.usage.threshold.UsageThresholdFixtures.ALICE;
import static dev.bluestep.global.dto.usage.threshold.UsageThresholdFixtures.BOB;
import static dev.bluestep.global.dto.usage.threshold.UsageThresholdFixtures.CREATED;
import static dev.bluestep.global.dto.usage.threshold.UsageThresholdFixtures.DAY;
import static dev.bluestep.global.dto.usage.threshold.UsageThresholdFixtures.DAY_END;
import static dev.bluestep.global.dto.usage.threshold.UsageThresholdFixtures.DAY_START;
import static dev.bluestep.global.dto.usage.threshold.UsageThresholdFixtures.SCHEMA;
import static dev.bluestep.global.dto.usage.threshold.UsageThresholdFixtures.TRIPPED;
import static dev.bluestep.global.dto.usage.threshold.UsageThresholdFixtures.UPDATED;
import static dev.bluestep.global.dto.usage.threshold.UsageThresholdFixtures.emailAlice;
import static dev.bluestep.global.dto.usage.threshold.UsageThresholdFixtures.notifyThenBreak;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.EnumSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.OptionalLong;
import java.util.Set;
import java.util.stream.LongStream;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import dev.bluestep.global.dto.tenantaccess.TenantUserKey;
import dev.bluestep.global.dto.usage.UsageCategory;
import dev.bluestep.global.dto.usage.UsageMetric;

/** Every rule the threshold shapes' constructors enforce, one case per rule and per side of it. */
class UsageThresholdValidationTest {

	private static final UsageThresholdAction BREAK_AT_2000 =
			new UsageThresholdAction(2_000, new UsageThresholdAction.Break());

	private static final List<UsageThresholdAction> NOTIFY_ONLY =
			List.of(new UsageThresholdAction(1_000, emailAlice()));

	private static UsageThreshold threshold(final UsageMetric metric, final UsageCategory category,
			final UsageThresholdWindow window, final List<UsageThresholdAction> actions) {
		return new UsageThreshold(1, 0, SCHEMA, metric, category, window, actions, CREATED, CREATED);
	}

	private static UsageThresholdRequest request(final UsageMetric metric, final UsageCategory category,
			final UsageThresholdWindow window, final List<UsageThresholdAction> actions) {
		return new UsageThresholdRequest(SCHEMA, metric, category, window, actions, null);
	}

	/** A window the metric's aggregation suits. */
	private static UsageThresholdWindow windowFor(final UsageMetric metric) {
		return metric.aggregation() == UsageMetric.Aggregation.LEVEL
				? UsageThresholdWindow.LATEST
				: UsageThresholdWindow.DAILY;
	}

	private static void assertRefused(final String messagePart, final Runnable construct) {
		final IllegalArgumentException refused = assertThrows(IllegalArgumentException.class,
				construct::run);
		assertTrue(refused.getMessage().contains(messagePart),
				"expected a refusal mentioning \"" + messagePart + "\", got: " + refused.getMessage());
	}

	private static void assertNpe(final String name, final Runnable construct) {
		final NullPointerException refused = assertThrows(NullPointerException.class, construct::run);
		assertEquals(name, refused.getMessage());
	}

	// --- window suits the metric's aggregation --------------------------------------------------

	@ParameterizedTest
	@EnumSource(UsageMetric.class)
	void everyMetric_acceptsExactlyTheWindowsItsAggregationSuits(final UsageMetric metric) {
		for (final UsageThresholdWindow window : UsageThresholdWindow.values()) {
			final boolean level = metric.aggregation() == UsageMetric.Aggregation.LEVEL;
			final boolean suits = level == (window == UsageThresholdWindow.LATEST);
			assertEquals(suits, window.suits(metric.aggregation()), metric + " over " + window);
			if (suits) {
				assertDoesNotThrow(() -> threshold(metric, null, window, NOTIFY_ONLY));
				assertDoesNotThrow(() -> request(metric, null, window, NOTIFY_ONLY));
			} else {
				assertRefused("does not suit", () -> threshold(metric, null, window, NOTIFY_ONLY));
				assertRefused("does not suit", () -> request(metric, null, window, NOTIFY_ONLY));
			}
		}
	}

	// --- a break only where refusing usage holds the metric still --------------------------------

	@ParameterizedTest
	@EnumSource(UsageMetric.class)
	void everyMetric_notifiesAlways_andBreaksOnlyWhenCumulativeOrALevel(final UsageMetric metric) {
		final UsageThresholdWindow window = windowFor(metric);
		final List<UsageThresholdAction> notifyThenBreak = notifyThenBreak();
		assertDoesNotThrow(() -> threshold(metric, null, window, NOTIFY_ONLY), metric + " notify");
		final boolean breakable = metric.aggregation() == UsageMetric.Aggregation.CUMULATIVE
				|| metric.aggregation() == UsageMetric.Aggregation.LEVEL;
		if (breakable) {
			assertDoesNotThrow(() -> threshold(metric, null, window, notifyThenBreak), metric + " break");
			assertDoesNotThrow(() -> request(metric, null, window, notifyThenBreak), metric + " break");
		} else {
			assertRefused("only locks the tenant out", () -> threshold(metric, null, window, notifyThenBreak));
			assertRefused("only locks the tenant out", () -> request(metric, null, window, notifyThenBreak));
		}
	}

	@Test
	void theBreakableAggregations_areExactlyCumulativeAndLevel() {
		final List<UsageMetric> breakable = new ArrayList<>();
		for (final UsageMetric metric : UsageMetric.values()) {
			try {
				threshold(metric, null, windowFor(metric), List.of(BREAK_AT_2000));
				breakable.add(metric);
			} catch (final IllegalArgumentException refused) {
				// not breakable
			}
		}
		assertEquals(Arrays.stream(UsageMetric.values()).filter(m -> m != UsageMetric.PAGE_MILLIS_AVG
				&& m != UsageMetric.PAGE_MILLIS_PEAK).toList(), breakable);
	}

	// --- nothing above the break -----------------------------------------------------------------

	@Test
	void aStageAboveTheBreak_isRefused_butOneAtItsLimitIsNot() {
		final List<UsageThresholdAction> above = List.of(BREAK_AT_2000,
				new UsageThresholdAction(5_000, emailAlice()));
		assertRefused("above the break", () -> threshold(UsageMetric.HITS, null, UsageThresholdWindow.DAILY, above));
		assertRefused("above the break", () -> request(UsageMetric.HITS, null, UsageThresholdWindow.DAILY, above));
		final List<UsageThresholdAction> tiedAfter = List.of(BREAK_AT_2000,
				new UsageThresholdAction(2_000, emailAlice()));
		assertEquals(tiedAfter,
				threshold(UsageMetric.HITS, null, UsageThresholdWindow.DAILY, tiedAfter).actions());
	}

	// --- category fits the metric ----------------------------------------------------------------

	@Test
	void aPerCategoryMetric_takesACategoryOrNone() {
		assertEquals(Optional.of(UsageCategory.FILE),
				threshold(UsageMetric.RESPONSE_BYTES, UsageCategory.FILE, UsageThresholdWindow.MONTHLY,
						notifyThenBreak()).optCategory());
		assertEquals(Optional.empty(),
				threshold(UsageMetric.RESPONSE_BYTES, null, UsageThresholdWindow.MONTHLY, notifyThenBreak())
						.optCategory());
	}

	@Test
	void aMetricWithNoCategory_refusesOne() {
		assertRefused("has no category", () -> threshold(UsageMetric.STORAGE_DB_BYTES, UsageCategory.HTTP,
				UsageThresholdWindow.LATEST, notifyThenBreak()));
		assertRefused("has no category", () -> request(UsageMetric.STORAGE_DB_BYTES, UsageCategory.HTTP,
				UsageThresholdWindow.LATEST, notifyThenBreak()));
		assertRefused("has no category",
				() -> new UsageBreaker(SCHEMA, UsageMetric.STORAGE_FILE_BYTES, UsageCategory.FILE, null));
	}

	// --- actions ---------------------------------------------------------------------------------

	@Test
	void noActions_isRefused() {
		assertRefused("at least one action",
				() -> threshold(UsageMetric.HITS, null, UsageThresholdWindow.DAILY, List.of()));
		assertRefused("at least one action",
				() -> request(UsageMetric.HITS, null, UsageThresholdWindow.DAILY, List.of()));
	}

	@Test
	void atMostTwentyActions() {
		assertEquals(20, UsageThreshold.MAX_ACTIONS);
		final List<UsageThresholdAction> twenty = LongStream.rangeClosed(1, 20)
				.mapToObj(limit -> new UsageThresholdAction(limit, emailAlice())).toList();
		assertDoesNotThrow(() -> threshold(UsageMetric.HITS, null, UsageThresholdWindow.DAILY, twenty));
		final List<UsageThresholdAction> twentyOne = new ArrayList<>(twenty);
		twentyOne.add(new UsageThresholdAction(21, emailAlice()));
		assertRefused("21 actions is more than 20",
				() -> threshold(UsageMetric.HITS, null, UsageThresholdWindow.DAILY, twentyOne));
		assertRefused("21 actions is more than 20",
				() -> request(UsageMetric.HITS, null, UsageThresholdWindow.DAILY, twentyOne));
	}

	@Test
	void actionsOutOfLimitOrder_areRefused() {
		final List<UsageThresholdAction> descending = List.of(new UsageThresholdAction(2_000, emailAlice()),
				new UsageThresholdAction(1_000, emailAlice()));
		assertRefused("below the limit before it",
				() -> threshold(UsageMetric.HITS, null, UsageThresholdWindow.DAILY, descending));
		assertRefused("below the limit before it",
				() -> request(UsageMetric.HITS, null, UsageThresholdWindow.DAILY, descending));
	}

	@Test
	void actionsMayShareALimit_toNotifyAndBreakTogether() {
		final List<UsageThresholdAction> tied = List.of(new UsageThresholdAction(2_000, emailAlice()),
				BREAK_AT_2000);
		assertEquals(tied, threshold(UsageMetric.HITS, null, UsageThresholdWindow.DAILY, tied).actions());
	}

	@Test
	void aSecondBreak_isRefused() {
		final List<UsageThresholdAction> twoBreaks = List.of(BREAK_AT_2000, BREAK_AT_2000);
		assertRefused("second break",
				() -> threshold(UsageMetric.HITS, null, UsageThresholdWindow.DAILY, twoBreaks));
		assertRefused("second break",
				() -> request(UsageMetric.HITS, null, UsageThresholdWindow.DAILY, twoBreaks));
	}

	@Test
	void actions_areCopied() {
		final List<UsageThresholdAction> source = new ArrayList<>(notifyThenBreak());
		final UsageThreshold threshold = threshold(UsageMetric.HITS, null, UsageThresholdWindow.DAILY, source);
		source.clear();
		assertEquals(2, threshold.actions().size());
		assertThrows(UnsupportedOperationException.class, () -> threshold.actions().clear());
	}

	@Test
	void aNullAction_isRefused() {
		final List<UsageThresholdAction> withNull = Arrays.asList(BREAK_AT_2000, null);
		assertThrows(NullPointerException.class,
				() -> threshold(UsageMetric.HITS, null, UsageThresholdWindow.DAILY, withNull));
	}

	@Test
	void limits_arePositive() {
		assertRefused("not positive", () -> new UsageThresholdAction(0, new UsageThresholdAction.Break()));
		assertRefused("not positive", () -> new UsageThresholdAction(-1, new UsageThresholdAction.Break()));
		assertDoesNotThrow(() -> new UsageThresholdAction(1, new UsageThresholdAction.Break()));
	}

	// --- notify ----------------------------------------------------------------------------------

	@Test
	void notify_needsAChannel() {
		assertRefused("at least one channel", () -> new UsageThresholdAction.Notify(Set.of(), List.of(ALICE)));
	}

	@ParameterizedTest
	@EnumSource(value = NotifyChannel.class, names = {"EMAIL", "IN_APP"})
	void aChannelThatNamesRecipients_needsOne(final NotifyChannel channel) {
		assertTrue(channel.namesRecipients());
		assertRefused("need at least one recipient",
				() -> new UsageThresholdAction.Notify(Set.of(channel), List.of()));
		assertRefused("need at least one recipient",
				() -> new UsageThresholdAction.Notify(EnumSet.of(channel, NotifyChannel.OPS), null));
	}

	@Test
	void ops_namesNobody() {
		assertFalse(NotifyChannel.OPS.namesRecipients());
		assertEquals(List.of(), new UsageThresholdAction.Notify(Set.of(NotifyChannel.OPS), null).recipients());
		assertRefused("name no recipients",
				() -> new UsageThresholdAction.Notify(Set.of(NotifyChannel.OPS), List.of(ALICE)));
	}

	@Test
	void notify_keepsChannelsInConstantOrderAndRecipientsInTheOrderGiven() {
		final LinkedHashSet<NotifyChannel> channels = new LinkedHashSet<>(
				List.of(NotifyChannel.OPS, NotifyChannel.IN_APP, NotifyChannel.EMAIL));
		final List<TenantUserKey> recipients = new ArrayList<>(List.of(BOB, ALICE));
		final UsageThresholdAction.Notify notify = new UsageThresholdAction.Notify(channels, recipients);
		assertEquals(List.of(NotifyChannel.EMAIL, NotifyChannel.IN_APP, NotifyChannel.OPS),
				List.copyOf(notify.channels()));
		assertEquals(List.of(BOB, ALICE), notify.recipients());
		channels.clear();
		recipients.clear();
		assertEquals(3, notify.channels().size());
		assertEquals(2, notify.recipients().size());
	}

	@Test
	void notify_refusesARecipientNamedTwice() {
		assertRefused("names " + ALICE + " twice", () -> new UsageThresholdAction.Notify(
				Set.of(NotifyChannel.EMAIL), List.of(ALICE, BOB, ALICE)));
	}

	@Test
	void notify_namesAtMostFiftyRecipients() {
		assertEquals(50, UsageThresholdAction.Notify.MAX_RECIPIENTS);
		final List<TenantUserKey> fifty = LongStream.rangeClosed(1, 50)
				.mapToObj(seqnum -> new TenantUserKey(7001, seqnum)).toList();
		assertEquals(fifty,
				new UsageThresholdAction.Notify(Set.of(NotifyChannel.EMAIL), fifty).recipients());
		final List<TenantUserKey> fiftyOne = new ArrayList<>(fifty);
		fiftyOne.add(new TenantUserKey(7001, 51));
		assertRefused("51 recipients is more than 50",
				() -> new UsageThresholdAction.Notify(Set.of(NotifyChannel.EMAIL), fiftyOne));
	}

	@Test
	void notify_refusesMissingChannelsAndANullRecipient() {
		assertThrows(NullPointerException.class, () -> new UsageThresholdAction.Notify(null, List.of(ALICE)));
		assertThrows(NullPointerException.class, () -> new UsageThresholdAction.Notify(
				Set.of(NotifyChannel.EMAIL), Arrays.asList(ALICE, null)));
	}

	// --- request ---------------------------------------------------------------------------------

	@Test
	void request_expectedVersionIsOptionalAndNonNegative() {
		assertEquals(OptionalLong.empty(),
				request(UsageMetric.HITS, null, UsageThresholdWindow.DAILY, notifyThenBreak())
						.optExpectedVersion());
		assertEquals(OptionalLong.of(0), new UsageThresholdRequest(SCHEMA, UsageMetric.HITS, null,
				UsageThresholdWindow.DAILY, notifyThenBreak(), 0L).optExpectedVersion());
		assertRefused("negative", () -> new UsageThresholdRequest(SCHEMA, UsageMetric.HITS, null,
				UsageThresholdWindow.DAILY, notifyThenBreak(), -1L));
	}

	@Test
	void request_refusesMissingRequiredComponentsByName() {
		assertNpe("schemaName", () -> new UsageThresholdRequest(null, UsageMetric.HITS, null,
				UsageThresholdWindow.DAILY, notifyThenBreak(), null));
		assertNpe("metric", () -> new UsageThresholdRequest(SCHEMA, null, null,
				UsageThresholdWindow.DAILY, notifyThenBreak(), null));
		assertNpe("window", () -> new UsageThresholdRequest(SCHEMA, UsageMetric.HITS, null,
				null, notifyThenBreak(), null));
		assertNpe("actions", () -> new UsageThresholdRequest(SCHEMA, UsageMetric.HITS, null,
				UsageThresholdWindow.DAILY, null, null));
	}

	// --- stored threshold ------------------------------------------------------------------------

	@Test
	void threshold_refusesMissingRequiredComponentsByName() {
		assertNpe("schemaName", () -> new UsageThreshold(1, 0, null, UsageMetric.HITS, null,
				UsageThresholdWindow.DAILY, notifyThenBreak(), CREATED, CREATED));
		assertNpe("createdAt", () -> new UsageThreshold(1, 0, SCHEMA, UsageMetric.HITS, null,
				UsageThresholdWindow.DAILY, notifyThenBreak(), null, CREATED));
		assertNpe("updatedAt", () -> new UsageThreshold(1, 0, SCHEMA, UsageMetric.HITS, null,
				UsageThresholdWindow.DAILY, notifyThenBreak(), CREATED, null));
	}

	@Test
	void threshold_refusesANegativeVersionAndAnUpdateBeforeTheCreate() {
		assertRefused("negative", () -> new UsageThreshold(1, -1, SCHEMA, UsageMetric.HITS, null,
				UsageThresholdWindow.DAILY, notifyThenBreak(), CREATED, CREATED));
		assertRefused("before createdAt", () -> new UsageThreshold(1, 0, SCHEMA, UsageMetric.HITS, null,
				UsageThresholdWindow.DAILY, notifyThenBreak(), UPDATED, CREATED));
	}

	// --- trip state ------------------------------------------------------------------------------

	private static UsageTripState trip(final long thresholdVersion, final int actionIndex, final long limit,
			final long observed, final UsageThresholdWindow.Span span, final Instant clearedAt,
			final Instant notifiedAt) {
		return new UsageTripState(1, 1, thresholdVersion, actionIndex, limit, observed, false, span, TRIPPED,
				clearedAt, notifiedAt);
	}

	@Test
	void trip_ordersItsMoments() {
		final UsageThresholdWindow.Span later = new UsageThresholdWindow.Span(TRIPPED.plusSeconds(1), DAY_END);
		assertRefused("is before span start", () -> trip(3, 0, 1, 1, later, null, null));
		assertRefused("clearedAt", () -> trip(3, 0, 1, 1, null, TRIPPED.minusSeconds(1), null));
		assertRefused("notifiedAt", () -> trip(3, 0, 1, 1, null, null, TRIPPED.minusSeconds(1)));
		assertNpe("trippedAt", () -> new UsageTripState(1, 1, 3, 0, 1, 1, false, null, null, null, null));
		assertDoesNotThrow(() -> trip(3, 0, 1, 1,
				new UsageThresholdWindow.Span(TRIPPED, DAY_END), TRIPPED, TRIPPED));
	}

	@Test
	void trip_refusesNegativeIndicesAndAnUnreachedLimit() {
		assertRefused("actionIndex -1 is negative", () -> trip(3, -1, 1, 1, null, null, null));
		assertRefused("thresholdVersion -1 is negative", () -> trip(-1, 0, 1, 1, null, null, null));
		assertRefused("limit 0 is not positive", () -> trip(3, 0, 0, 1, null, null, null));
		assertRefused("observed 999 is below limit 1000", () -> trip(3, 0, 1_000, 999, null, null, null));
		final UsageTripState trip = trip(3, 2, 1_000, 1_000, DAY, null, null);
		assertEquals(3, trip.thresholdVersion());
		assertEquals(2, trip.actionIndex());
	}

	@Test
	void trip_isActiveUntilCleared() {
		final UsageTripState standing = trip(3, 0, 1, 1, DAY, null, null);
		assertTrue(standing.isActive());
		assertEquals(Optional.of(DAY), standing.optSpan());
		assertEquals(Optional.empty(), standing.optClearedAt());
		assertEquals(Optional.empty(), standing.optNotifiedAt());
		final UsageTripState cleared = trip(3, 0, 1, 1, null, DAY_END, TRIPPED);
		assertFalse(cleared.isActive());
		assertEquals(Optional.of(DAY_END), cleared.optClearedAt());
		assertEquals(Optional.of(TRIPPED), cleared.optNotifiedAt());
		assertEquals(Optional.empty(), cleared.optSpan());
	}

	// --- breakers --------------------------------------------------------------------------------

	@Test
	void breaker_clearsAtIsPresentExactlyOnAWindowedMetric() {
		assertRefused("clearsAt is required",
				() -> new UsageBreaker(SCHEMA, UsageMetric.HITS, UsageCategory.HTTP, null));
		assertRefused("has no clearsAt",
				() -> new UsageBreaker(SCHEMA, UsageMetric.STORAGE_DB_BYTES, null, DAY_END));
		assertEquals(Optional.of(DAY_END),
				new UsageBreaker(SCHEMA, UsageMetric.HITS, null, DAY_END).optClearsAt());
		assertEquals(Optional.empty(),
				new UsageBreaker(SCHEMA, UsageMetric.STORAGE_DB_BYTES, null, null).optClearsAt());
	}

	@Test
	void breakerList_refusesAnAbsentListOrTime_butTakesAnExplicitEmptyOne() {
		assertEquals(List.of(), new UsageBreakerList(TRIPPED, List.of()).breakers());
		assertNpe("breakers", () -> new UsageBreakerList(TRIPPED, null));
		assertNpe("generatedAt", () -> new UsageBreakerList(null, List.of()));
		assertNpe("schemaName", () -> new UsageBreaker(null, UsageMetric.HITS, null, DAY_END));
		assertNpe("metric", () -> new UsageBreaker(SCHEMA, null, null, DAY_END));
	}

	// --- notifications ---------------------------------------------------------------------------

	private static UsageNotification notification(final NotifyChannel channel, final TenantUserKey to,
			final UsageMetric metric, final UsageCategory category, final long observed, final long limit,
			final UsageThresholdWindow.Span span) {
		return new UsageNotification(1, 2, SCHEMA, 1, 0, channel, to, metric, category, observed, limit, false,
				span, TRIPPED);
	}

	@Test
	void notification_isOnlyForAChannelWebSends() {
		assertRefused("not sent by web", () -> notification(NotifyChannel.OPS, ALICE, UsageMetric.HITS,
				null, 1, 1, DAY));
		assertDoesNotThrow(() -> notification(NotifyChannel.IN_APP, ALICE, UsageMetric.HITS, null, 1, 1,
				DAY));
	}

	@Test
	void notification_isForExactlyOneRecipient() {
		assertNpe("recipient", () -> notification(NotifyChannel.EMAIL, null, UsageMetric.HITS, null, 1, 1, DAY));
		assertEquals(BOB, notification(NotifyChannel.EMAIL, BOB, UsageMetric.HITS, null, 1, 1, DAY).recipient());
	}

	@Test
	void notification_observedReachesAPositiveLimit() {
		assertRefused("below limit", () -> notification(NotifyChannel.EMAIL, ALICE, UsageMetric.HITS,
				null, 999, 1_000, DAY));
		assertRefused("not positive", () -> notification(NotifyChannel.EMAIL, ALICE, UsageMetric.HITS,
				null, 0, 0, DAY));
		assertDoesNotThrow(() -> notification(NotifyChannel.EMAIL, ALICE, UsageMetric.HITS, null,
				1_000, 1_000, DAY));
	}

	@Test
	void notification_spanIsPresentExactlyOnAWindowedMetric_andHoldsTheTrip() {
		assertRefused("span is required", () -> notification(NotifyChannel.EMAIL, ALICE,
				UsageMetric.HITS, null, 1, 1, null));
		assertRefused("has no span", () -> notification(NotifyChannel.EMAIL, ALICE,
				UsageMetric.STORAGE_DB_BYTES, null, 1, 1, DAY));
		assertEquals(Optional.empty(), notification(NotifyChannel.EMAIL, ALICE,
				UsageMetric.STORAGE_DB_BYTES, null, 1, 1, null).optSpan());
		assertRefused("is before span start", () -> notification(NotifyChannel.EMAIL, ALICE,
				UsageMetric.HITS, null, 1, 1, new UsageThresholdWindow.Span(TRIPPED.plusSeconds(1), DAY_END)));
	}

	@Test
	void notification_categoryFitsTheMetricAndTheIndexIsNonNegative() {
		assertRefused("has no category", () -> notification(NotifyChannel.EMAIL, ALICE,
				UsageMetric.STORAGE_FILE_BYTES, UsageCategory.FILE, 1, 1, null));
		assertRefused("negative", () -> new UsageNotification(1, 2, SCHEMA, 1, -1, NotifyChannel.EMAIL,
				ALICE, UsageMetric.HITS, null, 1, 1, false, DAY, TRIPPED));
	}

	@Test
	void notification_refusesMissingRequiredComponentsByName() {
		assertNpe("schemaName", () -> new UsageNotification(1, 2, null, 1, 0, NotifyChannel.EMAIL, ALICE,
				UsageMetric.HITS, null, 1, 1, false, DAY, TRIPPED));
		assertNpe("channel", () -> new UsageNotification(1, 2, SCHEMA, 1, 0, null, ALICE,
				UsageMetric.HITS, null, 1, 1, false, DAY, TRIPPED));
		assertNpe("metric", () -> new UsageNotification(1, 2, SCHEMA, 1, 0, NotifyChannel.EMAIL, ALICE,
				null, null, 1, 1, false, DAY, TRIPPED));
		assertNpe("trippedAt", () -> new UsageNotification(1, 2, SCHEMA, 1, 0, NotifyChannel.EMAIL, ALICE,
				UsageMetric.HITS, null, 1, 1, false, DAY, null));
	}

	// --- notification request, batch and acknowledgement -----------------------------------------

	@Test
	void notificationRequest_namesAtLeastOneSchema() {
		assertRefused("at least one schema", () -> new UsageNotificationRequest(Set.of()));
		assertNpe("schemaNames", () -> new UsageNotificationRequest(null));
		assertEquals(Set.of(SCHEMA, "U1000002"),
				new UsageNotificationRequest(Set.of(SCHEMA, "U1000002")).schemaNames());
	}

	@Test
	void notificationBatch_refusesAnAbsentListOrTime_butTakesAnExplicitEmptyOne() {
		assertEquals(List.of(), new UsageNotificationBatch(TRIPPED, List.of()).notifications());
		assertNpe("notifications", () -> new UsageNotificationBatch(TRIPPED, null));
		assertNpe("generatedAt", () -> new UsageNotificationBatch(null, List.of()));
	}

	private static UsageNotificationAck delivered(final long id) {
		return new UsageNotificationAck(id, UsageNotificationOutcome.DELIVERED);
	}

	@Test
	void ack_namesEachNotificationOnceInOrder() {
		assertRefused("at least one", () -> new UsageNotificationAckRequest(List.of()));
		assertNpe("acks", () -> new UsageNotificationAckRequest(null));
		assertNpe("outcome", () -> new UsageNotificationAck(1, null));
		assertRefused("notification 3 is acknowledged twice", () -> new UsageNotificationAckRequest(List.of(
				delivered(3), new UsageNotificationAck(3, UsageNotificationOutcome.UNDELIVERABLE))));
		final List<UsageNotificationAck> acks = List.of(delivered(9), delivered(3), delivered(5));
		assertEquals(acks, new UsageNotificationAckRequest(new ArrayList<>(acks)).acks());
	}

	// --- span ------------------------------------------------------------------------------------

	@Test
	void span_isNonEmptyAndHalfOpen() {
		assertRefused("not after start", () -> new UsageThresholdWindow.Span(DAY_START, DAY_START));
		assertRefused("not after start", () -> new UsageThresholdWindow.Span(DAY_END, DAY_START));
		assertTrue(DAY.contains(DAY_START));
		assertTrue(DAY.contains(DAY_END.minusNanos(1)));
		assertFalse(DAY.contains(DAY_END));
		assertFalse(DAY.contains(DAY_START.minusNanos(1)));
	}

	/** The shared fixtures every wire test starts from are themselves valid. */
	@Test
	void fixturesAreValid() {
		assertDoesNotThrow(UsageThresholdFixtures::threshold);
		assertDoesNotThrow(UsageThresholdFixtures::request);
		assertDoesNotThrow(UsageThresholdFixtures::trip);
		assertDoesNotThrow(UsageThresholdFixtures::notification);
		assertDoesNotThrow(UsageThresholdFixtures::batch);
		assertDoesNotThrow(UsageThresholdFixtures::breakers);
	}
}
