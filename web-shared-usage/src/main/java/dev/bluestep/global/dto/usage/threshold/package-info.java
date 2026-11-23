/**
 * Wire shapes for per-tenant usage thresholds: a limit on one {@link
 * dev.bluestep.global.dto.usage.UsageMetric} that, when reached, notifies someone or breaks the
 * circuit for the usage it measures.
 *
 * <ul>
 *   <li>{@link UsageThresholdRequest} creates or replaces a threshold; {@link UsageThreshold} is the
 *       stored one, with its id and version. A tenant with no threshold is unlimited and silent.</li>
 *   <li>{@link UsageThresholdAction} is one stage of a threshold: a limit and what happens when it is
 *       reached, a {@link UsageThresholdAction.Notify} or a {@link UsageThresholdAction.Break}.</li>
 *   <li>{@link UsageThresholdWindow} is the stretch of time a threshold measures, UTC-aligned, or
 *       {@link UsageThresholdWindow#LATEST} for the latest reading.</li>
 *   <li>{@link UsageTripState} is a stage of one threshold version that has tripped, what it tripped
 *       on, and whether it has cleared.</li>
 *   <li>{@link UsageBreakerList} is every circuit currently broken, fleet-wide, for the pods that
 *       enforce them.</li>
 *   <li>{@link UsageNotification} is one notice, for one recipient on one channel, waiting for web's
 *       dispatcher to send. The dispatcher asks with a {@link UsageNotificationRequest}, is answered
 *       a {@link UsageNotificationBatch}, and reports each outcome in a
 *       {@link UsageNotificationAckRequest}.</li>
 * </ul>
 *
 * <p>Recipients are {@link dev.bluestep.global.dto.tenantaccess.TenantUserKey}s.</p>
 *
 * <h2>Validation lives in the records</h2>
 *
 * <p>Each compact constructor refuses a shape that is incoherent: a window that does not suit the
 * metric, a category on a metric that has none, actions out of limit order, a break that could only
 * lock a tenant out, a notice with nobody to send it to. Jackson runs the same constructor, so a
 * payload that breaks a rule fails binding on whichever side reads it, and a consumer never has to
 * re-check what it was handed.</p>
 *
 * <h2>Vocabulary and rules grow only with a release, and ride the contract tag</h2>
 *
 * <p>{@link dev.bluestep.global.dto.usage.UsageMetric}, {@link
 * dev.bluestep.global.dto.usage.UsageCategory}, {@link UsageThresholdWindow}, {@link NotifyChannel},
 * {@link UsageNotificationOutcome} and the action kinds bind only the values this release names, and
 * the constructors refuse what this release's rules refuse; either fails the whole payload. So the
 * guarantee is on the server, not the reader. web-global serves the threshold, trip, breaker and
 * notification paths under the {@code /api/v{tag}/} {@code ContractVersion} routing, where the tag is
 * the caller's {@code ContractVersion} path tag, and <b>answers a caller only rows that bind under
 * that tag's vocabulary and rules.</b> A row that names a later constant, or that only a later
 * release's relaxed rule admits, is left out of that caller's answer, not sent for it to fail on.
 * web-global keeps, per tag, the predicate that says which rows a tag can bind. For the vocabulary it
 * reads each constant's {@code introducedIn()}: web-global always runs the newest artifact, so the
 * constants it holds include every one a caller could be sent.</p>
 *
 * <p>A caller reaches 5.3.0's vocabulary by moving its contract tag to {@code 5.3.0}, which it does
 * only after web-global serves that tag. Moving to the 5.3.0 artifact alone does not change the tag.
 * On {@code /api/v5.3.0/} the caller is promised that every threshold, trip, breaker and notification
 * it is answered uses only the metrics, categories, windows, action kinds, channels and outcomes
 * 5.3.0 names, in the shapes 5.3.0 declares, and satisfies 5.3.0's rules, for as long as web-global
 * serves that tag. <b>Release order:</b> this artifact, then web-global serving a {@code 5.3.0} tag
 * for these paths and for every surface the earlier tags serve, then the monolith moving its contract
 * tag to it.</p>
 *
 * <p>For a breaker that is fail-open, which matches the enforcement design: a pod that cannot name a
 * metric cannot enforce it either. For a notification it means the notice waits, pending, for a
 * dispatcher that can send it.</p>
 *
 * <h2>Null-marked</h2>
 *
 * <p>Every type in this package is non-null unless annotated {@link
 * org.jspecify.annotations.Nullable}. A record component that may be absent is a {@code @Nullable}
 * value named for what it holds, and the record declares an {@code optX()} accessor that wraps it;
 * outside the record, read the accessor. An absent key and an explicit JSON {@code null} both bind
 * as absent.</p>
 */
@NullMarked
package dev.bluestep.global.dto.usage.threshold;

import org.jspecify.annotations.NullMarked;
