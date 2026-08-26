package dev.bluestep.global.dto.globaluser;

/**
 * What a snapshot push actually did.
 *
 * <p>Counts rather than a bare 200, because the failure this protocol invites is a caller that
 * assembled the wrong set: a push that unclassifies far more than expected is indistinguishable from
 * a correct one at the status line, and the whole point of replace-semantics is that the payload is
 * taken as the truth. Reported back so the caller can notice without anyone going to look.</p>
 *
 * @param held    how many users are classified now — the size of the pushed set
 * @param added   users that were not classified before
 * @param updated users already classified whose scope, reseller or reason changed; an identical
 *                classification counts as neither and leaves {@code recordedAt} alone
 * @param removed users that were classified and are absent from this push, so revert to the default
 */
public record GlobalUserScopeSnapshotResponse(int held, int added, int updated, int removed) {
}
