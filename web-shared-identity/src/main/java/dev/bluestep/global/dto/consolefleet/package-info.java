/**
 * Wire shapes for the Console's fleet status (from 6.21.0): what each namespace's web said about itself when
 * web-global last asked, beside web-global's own record of the namespace, for the gateway's fleet supers.
 *
 * <ul>
 *   <li>{@link ConsoleFleetResponse} is the answer: when the round of checks started, and one
 *       {@link ConsoleFleetNamespace} per {@code global.fleet_namespace} row, by name.</li>
 *   <li>{@link ConsoleFleetNamespace} is one namespace: its row's label and marks, the build its web reported, and its
 *       {@link FleetReadiness} (with the {@link FleetCheck} that failed, when web could tell).</li>
 * </ul>
 *
 * <h2>What never travels here</h2>
 *
 * <p>Nothing a namespace's web sent is passed on as text except its build, and that only when it is a short version
 * string ({@link ConsoleFleetNamespace#BUILD_PATTERN}): a status code becomes an enum, a body is never forwarded. No
 * Kubernetes workload data either: web-global asks each namespace's web, never the cluster.</p>
 *
 * <h2>Null-marked</h2>
 *
 * <p>Every type in this package is non-null except the members marked {@code @Nullable}: a label or build web-global
 * holds nothing for, and a failed check that does not apply, are absent.</p>
 */
@NullMarked
package dev.bluestep.global.dto.consolefleet;

import org.jspecify.annotations.NullMarked;
