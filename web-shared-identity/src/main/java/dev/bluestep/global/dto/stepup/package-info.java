/**
 * Wire shapes for a step-up (from 6.15.0, CU-1k5g91bqje9, ME2): web-global's own proof that a global user re-proved a
 * factor within the last ten minutes, which a browser token mint ({@code POST /my-account/access-tokens}) must
 * present. The gateway's key alone mints nothing.
 *
 * <h2>Who issues the ticket</h2>
 *
 * <p>web-global, and only after it verified the factor itself:</p>
 * <ul>
 *   <li>{@code POST /step-up-tickets/password} with an {@code UntenantedAuthenticationRequest}: the tenant-less password
 *       check of 6.4.0, unchanged, answered as a {@link StepUpAuthenticationResponse}. A ticket rides along exactly when
 *       the outcome is {@code MATCH} (the password is an enabled account's). The gateway's sign-in uses this route in
 *       place of {@code /global-user-records/lookups/untenanted/authenticate} once web-global serves 6.15.0, so the
 *       password is checked once.</li>
 *   <li>{@code POST /step-up-tickets/sso} with an {@link SsoStepUpRequest}: an ID token web-global verifies itself (the
 *       provider's signature, issuer, audience, expiry, and an {@code auth_time} within ten minutes), then resolves to
 *       the linked account. {@code 201} {@link StepUpTicketResponse}, or a {@link StepUpRefusal}.</li>
 * </ul>
 *
 * <h2>The ticket</h2>
 *
 * <p>32 random bytes in base64url (43 characters), shown once in the issuing answer and never again. web-global keeps
 * only its hash, fleet-wide, for ten minutes, bound to the one account it was issued for and to that account's
 * credential version. The mint carries it in {@link StepUpHeaders#STEP_UP_TICKET_HEADER}; web-global mints only for
 * the ticket's account, which must be the acting account, and a ticket is good for a bounded number of mints.</p>
 *
 * <h2>Null-marked</h2>
 *
 * <p>Every type in this package is non-null except the members marked {@code @Nullable}.</p>
 */
@NullMarked
package dev.bluestep.global.dto.stepup;

import org.jspecify.annotations.NullMarked;
