package dev.bluestep.global.dto.aiusage;

import java.time.OffsetDateTime;
import java.util.Optional;

import jakarta.validation.constraints.NotBlank;

/**
 * Read-only usage report query. Resolves the most-specific {@code ai_tenant_config}
 * row for the {@code (tenantId, unitId, userId, flag)} scope, derives the current budget
 * window from that row's schedule, and sums spend within it.
 *
 * <p>{@code unitId}, {@code userId} and {@code flag} are optional; an empty value narrows
 * nothing (tenant-wide). The matched config row's own wildcards govern which usage
 * rows are summed, exactly as in authorization.</p>
 *
 * <h2>The explicit date range</h2>
 *
 * <p>{@code from} and {@code to} override the configured budget window: present, they are the
 * window the sum covers, and the response reports them back as {@code windowStart}/{@code windowEnd}
 * so a caller always reads the figure against the period it was actually computed over. They do not
 * change which <em>rows</em> are in scope, only which instants — scope resolution is untouched, so a
 * range report and a window report of the same scope count the same tenants, units, users and
 * flags.</p>
 *
 * <p>Half-open, {@code [from, to)}: the same convention {@code BudgetWindow} already uses for a
 * rolling window, so a caller stepping day by day neither double-counts a turn on the boundary nor
 * drops one. {@code from} without {@code to} runs to now; {@code to} without {@code from} runs from
 * the lifetime epoch. A range is a <em>reporting</em> question — the budget the gate enforces is
 * always the configured window, and asking about an arbitrary period never moves it.</p>
 *
 * @param tenantId the tenant schema to report on
 * @param unitId   narrow to one unit, or empty for every unit in the tenant
 * @param userId   narrow to one user, or empty for every user in scope
 * @param flag     narrow to one flag, or empty for every flag in scope
 * @param from     inclusive start of an explicit reporting range; empty to use the budget window
 * @param to       exclusive end of an explicit reporting range; empty means "up to now"
 */
public record AiUsageQueryRequest(
		@NotBlank String tenantId,
		Optional<String> unitId,
		Optional<String> userId,
		Optional<String> flag,
		Optional<OffsetDateTime> from,
		Optional<OffsetDateTime> to
) {

	/** Whether the caller asked for an explicit period rather than the configured budget window. */
	public boolean hasExplicitRange() {
		return from.isPresent() || to.isPresent();
	}
}
