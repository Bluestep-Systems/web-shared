/**
 * Wire shapes for the Console's global-user directory (from 6.13.0): what the gateway's Console reads about global
 * users, read only, for fleet supers.
 *
 * <ul>
 *   <li>{@link ConsoleGlobalUserPage} is one page of the directory: {@link ConsoleGlobalUser} accounts in surname then
 *       forename order, with an opaque cursor for the next page.</li>
 *   <li>{@link ConsoleGlobalUserDetail} is one account with what the directory leaves out: its access tokens'
 *       metadata ({@link ConsoleAccessToken}), its tenant grants ({@link ConsoleTenantAccess}), its standing
 *       history-manager grant ({@link ConsoleHistoryManagerGrant}) and its catalog scope
 *       ({@link ConsoleCatalogScope}).</li>
 *   <li>{@link ConsoleActor} and {@link ConsoleReseller} name a global user and a reseller by key, with the name
 *       web-global holds for them.</li>
 *   <li>{@link GlobalUserFilter} and {@link GlobalUserKind} (from 6.17.0) narrow the page on the server: the query
 *       parameters, and the one rule that says what kind of account a record is.</li>
 *   <li>{@link ConsoleGlobalUserDisabledRequest} and {@link ConsoleGlobalUserWriteRefusal} (from 6.18.0) are the
 *       Console's first writes: a fleet super turns one account off or on (behind a step-up), or revokes one of its
 *       tokens by id. The acting account rides in a header, never the body.</li>
 *   <li>{@link ConsoleGlobalUserEndorsementsRequest} (from 6.20.0) changes one account's platform endorsements: the
 *       catalog ids to hold and which of its unrecognized tokens to keep. Never free text, never the ciphertext.</li>
 * </ul>
 *
 * <h2>What never travels here</h2>
 *
 * <p>No credential or anything derived from one, no token secret and no token hash (a token is its row's
 * {@code id}), no stored form, no {@code attribs} ciphertext, and no linked account's full subject: a linked account
 * is its issuer, a provider class and the subject's last four characters ({@link ConsoleLinkedAccount}), so the
 * pair that signs a person in is never on this wire.</p>
 *
 * <h2>Null-marked</h2>
 *
 * <p>Every type in this package is non-null except the members marked {@code @Nullable}: a value web-global holds
 * nothing for is absent.</p>
 */
@NullMarked
package dev.bluestep.global.dto.consoleglobaluser;

import org.jspecify.annotations.NullMarked;
