package dev.bluestep.global.dto.usage.threshold;

import java.time.Instant;
import java.util.List;
import java.util.Set;

import dev.bluestep.global.dto.usage.UsageCategory;
import dev.bluestep.global.dto.usage.UsageMetric;

/** Shared, valid instances of the threshold shapes, each value distinct so swapped keys show. */
final class UsageThresholdFixtures {

	static final String SCHEMA = "U1000001";
	static final TenantUserKey ALICE = new TenantUserKey(7001, 42);
	static final TenantUserKey BOB = new TenantUserKey(7001, 43);
	static final Instant CREATED = Instant.parse("2026-09-07T10:15:00Z");
	static final Instant UPDATED = Instant.parse("2026-09-08T11:30:00Z");
	static final Instant DAY_START = Instant.parse("2026-09-07T00:00:00Z");
	static final Instant DAY_END = Instant.parse("2026-09-08T00:00:00Z");
	static final Instant TRIPPED = Instant.parse("2026-09-07T13:45:00Z");

	private UsageThresholdFixtures() {}

	static UsageThresholdAction.Notify emailAlice() {
		return new UsageThresholdAction.Notify(Set.of(NotifyChannel.EMAIL), Set.of(ALICE));
	}

	static List<UsageThresholdAction> notifyThenBreak() {
		return List.of(new UsageThresholdAction(1_000, emailAlice()),
				new UsageThresholdAction(2_000, new UsageThresholdAction.Break()));
	}

	static UsageThreshold threshold() {
		return new UsageThreshold(11, 3, SCHEMA, UsageMetric.HITS, UsageCategory.HTTP,
				UsageThresholdWindow.DAILY, notifyThenBreak(), CREATED, UPDATED);
	}

	static UsageThresholdRequest request() {
		return new UsageThresholdRequest(SCHEMA, UsageMetric.HITS, UsageCategory.HTTP,
				UsageThresholdWindow.DAILY, notifyThenBreak(), 3L);
	}

	static UsageTripState trip() {
		return new UsageTripState(21, 11, 3, 1, DAY_START, TRIPPED, null, TRIPPED.plusSeconds(60));
	}

	static UsageNotification notification() {
		return new UsageNotification(31, SCHEMA, 11, 0, NotifyChannel.EMAIL, ALICE,
				UsageMetric.HITS, UsageCategory.HTTP, 1_234, 1_000,
				new UsageThresholdWindow.Span(DAY_START, DAY_END));
	}

	static UsageBreakerList breakers() {
		return new UsageBreakerList(TRIPPED, List.of(
				new UsageBreaker(SCHEMA, UsageMetric.HITS, UsageCategory.HTTP, DAY_END),
				new UsageBreaker(SCHEMA, UsageMetric.STORAGE_FILE_BYTES, null, null)));
	}
}
