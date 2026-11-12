package dev.bluestep.global.dto.historymanager;

import java.time.Instant;
import java.util.Optional;

import org.jspecify.annotations.Nullable;

import dev.bluestep.global.dto.tenantaccess.GlobalUserKey;

/**
 * One history-manager grant as the API reports it (6.4.0).
 *
 * @param user      the global user holding (or who held) the grant
 * @param grantedBy the super who gave it; never {@link #user}
 * @param granted   when it was given
 * @param revoked   when it was withdrawn, or absent while it stands
 * @param revokedBy who withdrew it, present exactly when {@link #revoked} is
 * @param effective whether it counts as of this answer: it stands and {@link #user} is a super now. Settled by
 *                  the server, the same rule as {@code GlobalUserAccessResponse.historyManager}, so no caller
 *                  re-derives it
 */
public record HistoryManagerGrantResponse(GlobalUserKey user, GlobalUserKey grantedBy, Instant granted,
		@Nullable Instant revoked, @Nullable GlobalUserKey revokedBy, boolean effective) {

	public HistoryManagerGrantResponse {
		if ((revoked == null) != (revokedBy == null)) {
			throw new IllegalArgumentException("a revoked grant names its revoker, and only a revoked one does");
		}
		if (revoked != null && effective) {
			throw new IllegalArgumentException("a revoked grant is never effective");
		}
	}

	/** {@link #revoked}, as an {@code Optional}. */
	public Optional<Instant> optRevoked() {
		return Optional.ofNullable(revoked);
	}

	/** {@link #revokedBy}, as an {@code Optional}. */
	public Optional<GlobalUserKey> optRevokedBy() {
		return Optional.ofNullable(revokedBy);
	}
}
