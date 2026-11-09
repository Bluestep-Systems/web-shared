/**
 * Wire shapes for history-manager grants (from 6.4.0): the endorsement a super needs, beside a token carrying
 * {@code kb:rewrite}, to rewrite a knowledge base's history (a force push or a ref deletion) through the gateway.
 *
 * <ul>
 *   <li>{@link HistoryManagerGrantRequest} grants it to one global user, {@code PUT /history-managers/{classid}/{seqnum}};
 *       {@link HistoryManagerRevokeRequest} withdraws it, {@code POST /history-managers/{classid}/{seqnum}/revocations}.
 *       Both name the acting super in {@code actor}, which must be a global user.</li>
 *   <li>{@link HistoryManagerGrantResponse} is one grant; {@link HistoryManagerGrantsResponse} the listing,
 *       {@code GET /history-managers}.</li>
 *   <li>A grant or revocation the rules refuse answers {@code 409} with a {@link HistoryManagerGrantRefusal}.</li>
 * </ul>
 *
 * <h2>The rule</h2>
 *
 * <p>A grant is given by an enabled super to a different enabled super. Grants are never deleted: a revocation
 * closes one (who and when), and a later grant is a new one, so the listing with revoked grants included is the
 * whole history. What the gateway acts on is {@code GlobalUserAccessResponse.historyManager}: an unrevoked grant
 * held by an account that is a super now.</p>
 *
 * <p>Reached with the blanket key only (web's administration screens). The gateway's key cannot reach this
 * surface in 6.4.0.</p>
 *
 * <h2>Null-marked</h2>
 *
 * <p>Every type in this package is non-null unless annotated {@link org.jspecify.annotations.Nullable}. A record
 * component that may be absent is a {@code @Nullable} value with an {@code optX()} accessor that wraps it.</p>
 */
@NullMarked
package dev.bluestep.global.dto.historymanager;

import org.jspecify.annotations.NullMarked;
