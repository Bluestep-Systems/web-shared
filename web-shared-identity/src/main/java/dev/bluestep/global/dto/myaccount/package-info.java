/**
 * Wire shapes for the Console's My Account (from 6.14.0): what a signed-in global user reads about their own account
 * through the gateway, and the revoke of one of their own tokens. Any signed-in account may ask, super or not.
 *
 * <h2>Whose account</h2>
 *
 * <p>No path or body names a user. Every call carries {@link MyAccountHeaders#ACTING_GLOBAL_USER_HEADER}, which the
 * gateway sets from the browser session alone (never from a token or anything the client sends), and web-global
 * answers for that account only, on the gateway's key only.</p>
 *
 * <ul>
 *   <li>{@code GET /my-account}: {@code 200} {@link MyAccountResponse}.</li>
 *   <li>{@code DELETE /my-account/access-tokens/{id}}: {@code 204}, whether the token was deleted now or was already
 *       gone (another owner's id is "already gone" too, so nothing leaks about it).</li>
 * </ul>
 *
 * <p>A missing or malformed header, or one naming the retired System User, is {@code 400}. A missing or disabled
 * account is {@code 403} with a {@link MyAccountRefusal} ({@code ACCOUNT_DISABLED}). Every answer carries
 * {@code Cache-Control: no-store}.</p>
 *
 * <h2>What never travels here</h2>
 *
 * <p>No credential or anything derived from one, no token secret or hash (a token is its row's {@code id}), no
 * {@code attribs} ciphertext and no linked account's full subject, as in {@code dto.consoleglobaluser}, whose leaf
 * shapes these reuse. Also left out, because they are other people's or operators' details: who created or edited the
 * account, who granted its history-manager grant, the catalog scope's reason, the version tag, and the super and
 * disabled flags.</p>
 *
 * <h2>Null-marked</h2>
 *
 * <p>Every type in this package is non-null except the members marked {@code @Nullable}: a value web-global holds
 * nothing for is absent.</p>
 */
@NullMarked
package dev.bluestep.global.dto.myaccount;

import org.jspecify.annotations.NullMarked;
