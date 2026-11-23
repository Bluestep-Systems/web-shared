/**
 * Wire shapes for self-service access tokens (from 6.5.0): the gateway's {@code /api/v1/tokens}, served by
 * web-global under {@code /api/v6.5.0/self-tokens} to the gateway's registry key only.
 *
 * <h2>Who the subject is</h2>
 *
 * <p>Nothing here names a user. Every call carries the presenting token's SHA-512 hex hash in
 * {@code X-Access-Token}; web-global re-reads that token (live, owner enabled, holding {@code tokens:self}) and
 * acts only for its owner (Brendan's ruling 2026-11-10 "TK1 Q1"). The gateway's key alone mints nothing.</p>
 *
 * <ul>
 *   <li>{@code GET /self-tokens}: {@code 200} {@link SelfTokensResponse}, every token the owner holds, expired
 *       ones included.</li>
 *   <li>{@code POST /self-tokens} with a {@link SelfTokenMintRequest}: {@code 201} {@link SelfTokenMinted}.</li>
 *   <li>{@code DELETE /self-tokens/{id}}: {@code 204}.</li>
 *   <li>{@code POST /self-tokens/{id}/rotations} (no body): {@code 201} {@link SelfTokenMinted}, the replacement;
 *       the old token is gone in the same transaction.</li>
 * </ul>
 *
 * <p>A refusal is a {@link SelfTokenRefusal} with {@code 400} ({@code INVALID_REQUEST}), {@code 403} or
 * {@code 409} ({@code ALIAS_TAKEN}, {@code TOKEN_LIMIT}); the reason, not the status, is what a reader maps. An id
 * that is unknown, malformed or another user's is a plain {@code 404} with no body, the same for all three, so
 * existence never leaks. Every answer carries {@code Cache-Control: no-store}.</p>
 *
 * <h2>Null-marked</h2>
 *
 * <p>Every type in this package is non-null unless annotated {@link org.jspecify.annotations.Nullable}. A record
 * component that may be absent is a {@code @Nullable} value with an {@code optX()} accessor that wraps it.</p>
 */
@NullMarked
package dev.bluestep.global.dto.selftoken;

import org.jspecify.annotations.NullMarked;
