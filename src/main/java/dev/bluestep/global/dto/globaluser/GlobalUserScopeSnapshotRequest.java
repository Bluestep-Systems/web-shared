package dev.bluestep.global.dto.globaluser;

import java.util.List;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * The complete set of classified global users, replacing whatever web-global holds.
 *
 * <h2>A snapshot, not a delta — and why both exist</h2>
 *
 * <p>Same protocol as {@code TenantAccessReportRequest} and for the same reason: a delta loses a
 * change permanently the one time a message is dropped, whereas a snapshot repairs itself on the next
 * push. The per-user PUT is the convenient path for a single edit; this is the one that makes drift
 * self-correcting, and it is what the monolith runs on a schedule once it holds this decision.</p>
 *
 * <p><b>An empty list means nobody is classified</b>, and will remove every row — leaving the whole
 * fleet visible to everyone, since absence resolves to {@code FLEET}. That is the honest reading of a
 * snapshot and also the dangerous one, so the response reports what changed rather than only that it
 * succeeded.</p>
 *
 * @param actor who is making this assertion; recorded against every row it creates or changes
 * @param users the whole classified set as of now; a user absent from it is no longer classified
 */
public record GlobalUserScopeSnapshotRequest(
		@NotBlank @Size(max = 100) String actor,
		// Bounded because it arrives over the wire and is written to a table. A push of a hundred
		// thousand is a bug or an attack, and either way is better refused than persisted.
		@Size(max = 100_000) List<@Valid GlobalUserScopeEntry> users
) {

	public GlobalUserScopeSnapshotRequest {
		// The list is the whole meaning of the message, so it is defended rather than trusted.
		users = List.copyOf(users);
	}
}
