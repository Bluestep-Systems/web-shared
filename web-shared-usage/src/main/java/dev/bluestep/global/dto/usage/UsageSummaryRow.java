package dev.bluestep.global.dto.usage;

/**
 * One tenant's usage totals over a {@link UsageSummaryResponse}'s range, summed across namespaces
 * and the requested categories. The components mirror {@link UsageWindow}'s metrics.
 *
 * <p>Totals are sums. The {@code _max} figures are the largest single-minute maximum anywhere in
 * the range — {@code pageMillisMax} is the slowest single hit, not the slowest minute's total.</p>
 *
 * @param schemaName       the canonical {@code U<seqnum>}, or {@code unattributed}
 * @param hits             requests and completed background runs
 * @param pageMillis       wall time, in milliseconds
 * @param pageMillisMax    the slowest single hit, in milliseconds
 * @param cpuMillis        thread CPU time, in milliseconds
 * @param dbMillis         time spent in JDBC calls, in milliseconds
 * @param allocBytes       heap bytes allocated
 * @param requestBytes     request body bytes
 * @param requestBytesMax  the largest single request body
 * @param responseBytes    response body bytes
 * @param responseBytesMax the largest single response body
 */
public record UsageSummaryRow(
		String schemaName,
		long hits,
		long pageMillis,
		long pageMillisMax,
		long cpuMillis,
		long dbMillis,
		long allocBytes,
		long requestBytes,
		long requestBytesMax,
		long responseBytes,
		long responseBytesMax) {
}
