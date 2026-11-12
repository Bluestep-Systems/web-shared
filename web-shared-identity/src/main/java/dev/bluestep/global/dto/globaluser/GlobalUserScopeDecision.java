package dev.bluestep.global.dto.globaluser;

import java.time.OffsetDateTime;

/**
 * The recorded act of classifying a global user, as distinct from the classification itself.
 *
 * @param reason     why, as the deciding operator gave it
 * @param recordedAt when the <em>current</em> classification was recorded. Re-asserting an unchanged
 *                   one does not move it, so a scheduled snapshot cannot quietly reset every date; a
 *                   change does, because that is a new decision.
 * @param actor      who recorded it, or {@code migration} for a row seeded from the classifications
 *                   the monolith already held on {@code global.globaluser}
 */
public record GlobalUserScopeDecision(String reason, OffsetDateTime recordedAt, String actor) {
}
