package dev.bluestep.global.dto.historymanager;

import dev.bluestep.global.dto.tenant.TenantActor;
import jakarta.validation.constraints.NotNull;

/**
 * Withdrawing one global user's history-manager endorsement (6.4.0):
 * {@code POST /history-managers/{classid}/{seqnum}/revocations}. A POST rather than a {@code DELETE} because the
 * revocation names who made it, and the grant is closed rather than removed.
 *
 * <p>{@code 200} with the grant just closed; {@code 204} when the user holds no unrevoked grant (the state asked
 * for, so a retry after a lost answer is not a failure). {@code 409} {@link HistoryManagerGrantRefusal}
 * {@code ACTOR_NOT_SUPER} when the actor is not an enabled super. A super may revoke their own grant.</p>
 *
 * @param actor the super withdrawing the grant; must be a {@link TenantActor.GlobalUser}
 */
public record HistoryManagerRevokeRequest(@NotNull TenantActor actor) {
}
