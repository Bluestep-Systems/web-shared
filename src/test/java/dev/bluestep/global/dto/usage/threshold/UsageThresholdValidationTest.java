package dev.bluestep.global.dto.usage.threshold;

import static dev.bluestep.global.dto.usage.threshold.UsageThresholdFixtures.ALICE;
import static dev.bluestep.global.dto.usage.threshold.UsageThresholdFixtures.BOB;
import static dev.bluestep.global.dto.usage.threshold.UsageThresholdFixtures.CREATED;
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

import java.util.ArrayList;
import java.util.Arrays;
import java.util.EnumSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.OptionalLong;
import java.util.Set;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import dev.bluestep.global.dto.usage.UsageCategory;
import dev.bluestep.global.dto.usage.UsageMetric;

/** Every rule the threshold shapes' constructors enforce, one case per rule and per side of it. */
class UsageThresholdValidationTest {

	private static final UsageThresholdAction BREAK_AT_2000 =
			new UsageThresholdAction(2_000, new UsageThresholdAction.Break());

	private static UsageThreshold threshold(final UsageMetric metric, final UsageCategory category,
			final UsageThresholdWindow window, final List<UsageThresholdAction> actions) {
		return new UsageThreshold(1, 0, SCHEMA, metric, category, window, actions, CREATED, CREATED);
	}

	private static UsageThresholdRequest request(final UsageMetric metric, final UsageCategory category,
			final UsageThresholdWindow window, final List<UsageThresholdAction> actions) {
		return new UsageThresholdRequest(SCHEMA, metric, category, window, actions, null);
	}

	private static void assertRefused(final String messagePart, final Runnable construct) {
		final IllegalArgumentException refused = assertThrows(IllegalArgumentException.class,
				construct::run);
		assertTrue(refused.getMessage().contains(messagePart),
				"expected a refusal mentioning \"" + messagePart + "\", got: " + refused.getMessage());
	}

	// --- window suits the metric's aggregation --------------------------------------------------

	@ParameterizedTest
	@EnumSource(UsageMetric.class)
	void everyMetric_acceptsExactlyTheWindowsItsAggregationSuits(final UsageMetric metric) {
		for (final UsageThresholdWindow window : UsageThresholdWindow.values()) {
			final boolean level = metric.aggregation() == UsageMetric.Aggregation.LEVEL;
			final boolean suits = level == (window == UsageThresholdWindow.LEVEL);
			assertEquals(suits, window.suits(metric.aggregation()), metric + " over " + window);
			if (suits) {
				assertDoesNotThrow(() -> threshold(metric, null, window, notifyThenBreak()));
				assertDoesNotThrow(() -> request(metric, null, window, notifyThenBreak()));
			} else {
				assertRefused("does not suit", () -> threshold(metric, null, window, notifyThenBreak()));
				assertRefused("does not suit", () -> request(metric, null, window, notifyThenBreak()));
			}
		}
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
				UsageThresholdWindow.LEVEL, notifyThenBreak()));
		assertRefused("has no category", () -> request(UsageMetric.STORAGE_DB_BYTES, UsageCategory.HTTP,
				UsageThresholdWindow.LEVEL, notifyThenBreak()));
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
	void actionsOutOfLevelOrder_areRefused() {
		final List<UsageThresholdAction> descending = List.of(BREAK_AT_2000,
				new UsageThresholdAction(1_000, emailAlice()));
		assertRefused("below the level before it",
				() -> threshold(UsageMetric.HITS, null, UsageThresholdWindow.DAILY, descending));
		assertRefused("below the level before it",
				() -> request(UsageMetric.HITS, null, UsageThresholdWindow.DAILY, descending));
	}

	@Test
	void actionsMayShareALevel_toNotifyAndBreakTogether() {
		final List<UsageThresholdAction> tied = List.of(new UsageThresholdAction(2_000, emailAlice()),
				BREAK_AT_2000);
		assertEquals(tied, threshold(UsageMetric.HITS, null, UsageThresholdWindow.DAILY, tied).actions());
	}

	@Test
	void aSecondBreak_isRefused() {
		final List<UsageThresholdAction> twoBreaks = List.of(
				new UsageThresholdAction(1_000, new UsageThresholdAction.Break()), BREAK_AT_2000);
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
	void levels_arePositive() {
		assertRefused("not positive", () -> new UsageThresholdAction(0, new UsageThresholdAction.Break()));
		assertRefused("not positive", () -> new UsageThresholdAction(-1, new UsageThresholdAction.Break()));
		assertDoesNotThrow(() -> new UsageThresholdAction(1, new UsageThresholdAction.Break()));
	}

	// --- notify ----------------------------------------------------------------------------------

	@Test
	void notify_needsAChannel() {
		assertRefused("at least one channel", () -> new UsageThresholdAction.Notify(Set.of(), Set.of(ALICE)));
	}

	@ParameterizedTest
	@EnumSource(value = NotifyChannel.class, names = {"EMAIL", "IN_APP"})
	void aChannelThatNamesRecipients_needsOne(final NotifyChannel channel) {
		assertTrue(channel.namesRecipients());
		assertRefused("need at least one recipient",
				() -> new UsageThresholdAction.Notify(Set.of(channel), Set.of()));
		assertRefused("need at least one recipient",
				() -> new UsageThresholdAction.Notify(EnumSet.of(channel, NotifyChannel.OPS), null));
	}

	@Test
	void ops_namesNobody() {
		assertFalse(NotifyChannel.OPS.namesRecipients());
		assertEquals(Set.of(), new UsageThresholdAction.Notify(Set.of(NotifyChannel.OPS), null).recipients());
		assertRefused("name no recipients",
				() -> new UsageThresholdAction.Notify(Set.of(NotifyChannel.OPS), Set.of(ALICE)));
	}

	@Test
	void notify_keepsChannelsInConstantOrderAndRecipientsInTheOrderGiven() {
		final LinkedHashSet<NotifyChannel> channels = new LinkedHashSet<>(
				List.of(NotifyChannel.OPS, NotifyChannel.IN_APP, NotifyChannel.EMAIL));
		final LinkedHashSet<TenantUserKey> recipients = new LinkedHashSet<>(List.of(BOB, ALICE));
		final UsageThresholdAction.Notify notify = new UsageThresholdAction.Notify(channels, recipients);
		assertEquals(List.of(NotifyChannel.EMAIL, NotifyChannel.IN_APP, NotifyChannel.OPS),
				List.copyOf(notify.channels()));
		assertEquals(List.of(BOB, ALICE), List.copyOf(notify.recipients()));
		channels.clear();
		recipients.clear();
		assertEquals(3, notify.channels().size());
		assertEquals(2, notify.recipients().size());
	}

	@Test
	void notify_refusesMissingChannels() {
		assertThrows(NullPointerException.class, () -> new UsageThresholdAction.Notify(null, Set.of(ALICE)));
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

	@Test
	void trip_ordersItsMoments() {
		assertRefused("after trippedAt",
				() -> new UsageTripState(1, 1, 3, 0, TRIPPED.plusSeconds(1), TRIPPED, null, null));
		assertRefused("clearedAt", () -> new UsageTripState(1, 1, 3, 0, null, TRIPPED,
				TRIPPED.minusSeconds(1), null));
		assertRefused("lastNotifiedAt", () -> new UsageTripState(1, 1, 3, 0, null, TRIPPED, null,
				TRIPPED.minusSeconds(1)));
		assertRefused("actionIndex -1 is negative",
				() -> new UsageTripState(1, 1, 3, -1, null, TRIPPED, null, null));
		assertRefused("thresholdVersion -1 is negative",
				() -> new UsageTripState(1, 1, -1, 0, null, TRIPPED, null, null));
		assertEquals(3, new UsageTripState(1, 1, 3, 0, null, TRIPPED, null, null).thresholdVersion());
		assertNpe("trippedAt", () -> new UsageTripState(1, 1, 3, 0, null, null, null, null));
		assertDoesNotThrow(() -> new UsageTripState(1, 1, 3, 0, TRIPPED, TRIPPED, TRIPPED, TRIPPED));
	}

	@Test
	void trip_isActiveUntilCleared() {
		final UsageTripState standing = new UsageTripState(1, 1, 3, 0, DAY_START, TRIPPED, null, null);
		assertTrue(standing.isActive());
		assertEquals(Optional.of(DAY_START), standing.optWindowStart());
		assertEquals(Optional.empty(), standing.optClearedAt());
		assertEquals(Optional.empty(), standing.optLastNotifiedAt());
		final UsageTripState cleared = new UsageTripState(1, 1, 3, 0, null, TRIPPED, DAY_END, TRIPPED);
		assertFalse(cleared.isActive());
		assertEquals(Optional.of(DAY_END), cleared.optClearedAt());
		assertEquals(Optional.empty(), cleared.optWindowStart());
	}

	// --- breakers --------------------------------------------------------------------------------

	@Test
	void breaker_retryAfterIsPresentExactlyOnAWindowedMetric() {
		assertRefused("retryAfter is required",
				() -> new UsageBreaker(SCHEMA, UsageMetric.HITS, UsageCategory.HTTP, null));
		assertRefused("has no window to end",
				() -> new UsageBreaker(SCHEMA, UsageMetric.STORAGE_DB_BYTES, null, DAY_END));
		assertEquals(Optional.of(DAY_END),
				new UsageBreaker(SCHEMA, UsageMetric.PAGE_MILLIS_PEAK, null, DAY_END).optRetryAfter());
		assertEquals(Optional.empty(),
				new UsageBreaker(SCHEMA, UsageMetric.STORAGE_DB_BYTES, null, null).optRetryAfter());
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
			final UsageMetric metric, final UsageCategory category, final long observed, final long level,
			final UsageThresholdWindow.Span window) {
		return new UsageNotification(1, SCHEMA, 1, 0, channel, to, metric, category, observed, level, window);
	}

	private static final UsageThresholdWindow.Span DAY = new UsageThresholdWindow.Span(DAY_START, DAY_END);

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
	void notification_observedReachesAPositiveLevel() {
		assertRefused("below level", () -> notification(NotifyChannel.EMAIL, ALICE, UsageMetric.HITS,
				null, 999, 1_000, DAY));
		assertRefused("not positive", () -> notification(NotifyChannel.EMAIL, ALICE, UsageMetric.HITS,
				null, 0, 0, DAY));
		assertDoesNotThrow(() -> notification(NotifyChannel.EMAIL, ALICE, UsageMetric.HITS, null,
				1_000, 1_000, DAY));
	}

	@Test
	void notification_windowIsPresentExactlyOnAWindowedMetric() {
		assertRefused("window is required", () -> notification(NotifyChannel.EMAIL, ALICE,
				UsageMetric.HITS, null, 1, 1, null));
		assertRefused("has no window", () -> notification(NotifyChannel.EMAIL, ALICE,
				UsageMetric.STORAGE_DB_BYTES, null, 1, 1, DAY));
		assertEquals(Optional.empty(), notification(NotifyChannel.EMAIL, ALICE,
				UsageMetric.STORAGE_DB_BYTES, null, 1, 1, null).optWindow());
	}

	@Test
	void notification_categoryFitsTheMetricAndTheIndexIsNonNegative() {
		assertRefused("has no category", () -> notification(NotifyChannel.EMAIL, ALICE,
				UsageMetric.STORAGE_FILE_BYTES, UsageCategory.FILE, 1, 1, null));
		assertRefused("negative", () -> new UsageNotification(1, SCHEMA, 1, -1, NotifyChannel.EMAIL,
				ALICE, UsageMetric.HITS, null, 1, 1, DAY));
	}

	// --- acknowledgement -------------------------------------------------------------------------

	@Test
	void ack_namesAtLeastOneNotificationInOrder() {
		assertRefused("at least one", () -> new UsageNotificationAckRequest(Set.of()));
		assertNpe("ids", () -> new UsageNotificationAckRequest(null));
		final UsageNotificationAckRequest ack =
				new UsageNotificationAckRequest(new LinkedHashSet<>(List.of(9L, 3L, 5L)));
		assertEquals(List.of(9L, 3L, 5L), List.copyOf(ack.ids()));
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

	private static void assertNpe(final String name, final Runnable construct) {
		final NullPointerException refused = assertThrows(NullPointerException.class, construct::run);
		assertEquals(name, refused.getMessage());
	}

	/** The shared fixtures every wire test starts from are themselves valid. */
	@Test
	void fixturesAreValid() {
		assertDoesNotThrow(UsageThresholdFixtures::threshold);
		assertDoesNotThrow(UsageThresholdFixtures::request);
		assertDoesNotThrow(UsageThresholdFixtures::trip);
		assertDoesNotThrow(UsageThresholdFixtures::notification);
		assertDoesNotThrow(UsageThresholdFixtures::breakers);
	}
}
