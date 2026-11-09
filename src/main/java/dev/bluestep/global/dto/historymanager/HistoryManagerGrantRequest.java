package dev.bluestep.global.dto.historymanager;

import com.fasterxml.jackson.annotation.JsonIgnore;

import dev.bluestep.global.dto.tenant.TenantActor;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotNull;

/**
 * Granting one global user the history-manager endorsement (6.4.0): {@code PUT /history-managers/{classid}/{seqnum}}.
 *
 * <p>Idempotent: {@code 201} with the new {@link HistoryManagerGrantResponse}, or {@code 200} with the standing one
 * when the user already holds an unrevoked grant, which keeps its original grantor and time. {@code 409}
 * {@link HistoryManagerGrantRefusal} when the actor is not an enabled super, is the grantee, or the grantee is not
 * an enabled super. {@code 400} for an absent actor, a tenant-user one, or a global-user one with no key
 * ({@link #isActorAGlobalUser()}).</p>
 *
 * @param actor the super giving the grant; must be a {@link TenantActor.GlobalUser}, and not the grantee
 */
public record HistoryManagerGrantRequest(@NotNull TenantActor actor) {

	/**
	 * Bean Validation's half of "the actor is a global user, by key": {@code @NotNull} sees only the wrapper, so
	 * a {@code tenant-user} actor, or {@code {"kind":"global-user"}} with no {@code user} (Jackson binds it as a
	 * {@code GlobalUser} whose key is null, whatever the null-marking says), would otherwise pass and reach a
	 * handler that dereferences the key. {@code true} for an absent actor, which {@code @NotNull} reports.
	 *
	 * <p>{@code @JsonIgnore}: an {@code @AssertTrue} method is a JavaBeans getter, and is no wire property.</p>
	 *
	 * @return whether the actor, when present, is a {@link TenantActor.GlobalUser} naming a key
	 */
	@JsonIgnore
	@AssertTrue(message = "actor must be a global user, with its key")
	public boolean isActorAGlobalUser() {
		return actor == null || (actor instanceof TenantActor.GlobalUser global && global.user() != null);
	}
}
