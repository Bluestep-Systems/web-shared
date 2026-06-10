package dev.bluestep.global.dto.ai;

import java.time.DayOfWeek;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.time.temporal.TemporalAdjusters;

/**
 * Budget-period granularity for an {@code ai_tenant_config} row. Pairs with a
 * {@code utc_offset_minutes} column so each tenant's window aligns to its local
 * calendar (not server UTC). {@link #LIFETIME} disables windowing — the budget
 * is the cap on cumulative consumption.
 *
 * <p>Window starts are computed in the anchor offset and converted back to UTC
 * so callers can use the result directly in {@code WHERE request_timestamp >= ?}
 * queries against {@code timestamptz} columns.</p>
 */
public enum BudgetSchedule {
	HOURLY,
	DAILY,
	WEEKLY,
	MONTHLY,
	LIFETIME;

	public boolean isWindowed() {
		return this != LIFETIME;
	}

	/**
	 * Start of the current budget window. Throws for {@link #LIFETIME} — guard
	 * with {@link #isWindowed()} at the call site.
	 */
	public OffsetDateTime windowStart(OffsetDateTime now, ZoneOffset anchor) {
		OffsetDateTime atAnchor = now.withOffsetSameInstant(anchor);
		return switch (this) {
			case HOURLY -> atAnchor.truncatedTo(ChronoUnit.HOURS);
			case DAILY -> atAnchor.truncatedTo(ChronoUnit.DAYS);
			case WEEKLY -> atAnchor.truncatedTo(ChronoUnit.DAYS)
					.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
			case MONTHLY -> atAnchor.truncatedTo(ChronoUnit.DAYS).withDayOfMonth(1);
			case LIFETIME -> throw new IllegalStateException("LIFETIME has no window start");
		};
	}
}
