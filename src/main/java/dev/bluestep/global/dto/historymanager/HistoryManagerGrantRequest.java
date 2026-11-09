package dev.bluestep.global.dto.historymanager;

import dev.bluestep.global.dto.tenant.TenantActor;
import jakarta.validation.constraints.NotNull;

/**
 * Granting one global user the history-manager endorsement (6.4.0): {@code PUT /history-managers/{classid}/{seqnum}}.
 *
 * <p>Idempotent: {@code 201} with the new {@link HistoryManagerGrantResponse}, or {@code 200} with the standing one
 * when the user already holds an unrevoked grant, which keeps its original grantor and time. {@code 409}
 * {@link HistoryManagerGrantRefusal} when the actor is not an enabled super, is the grantee, or the grantee is not
 * an enabled super. {@code 400} for an absent actor or a tenant-user one.</p>
 *
 * @param actor the super giving the grant; must be a {@link TenantActor.GlobalUser}, and not the grantee
 */
public record HistoryManagerGrantRequest(@NotNull TenantActor actor) {
}
