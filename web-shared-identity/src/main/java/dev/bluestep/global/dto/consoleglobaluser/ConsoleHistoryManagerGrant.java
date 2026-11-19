package dev.bluestep.global.dto.consoleglobaluser;

import java.time.Instant;
import java.util.Objects;

/**
 * An account's standing history-manager grant (from 6.13.0): the right to force-push and rewrite knowledge-base
 * history. A revoked grant is not shown.
 *
 * @param grantedAt when it was given
 * @param grantedBy the super who gave it
 */
public record ConsoleHistoryManagerGrant(Instant grantedAt, ConsoleActor grantedBy) {

	/** Requires both members. */
	public ConsoleHistoryManagerGrant {
		Objects.requireNonNull(grantedAt, "grantedAt");
		Objects.requireNonNull(grantedBy, "grantedBy");
	}
}
