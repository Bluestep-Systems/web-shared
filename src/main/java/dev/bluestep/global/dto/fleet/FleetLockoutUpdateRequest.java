package dev.bluestep.global.dto.fleet;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;

/**
 * Setting the fleet-wide lockout (from 6.0.0): {@code PUT /fleet-lockout} with a required
 * {@code If-Match}, answered with the {@link FleetLockout} and its new version as the {@code ETag}.
 *
 * <p>The lockout is written whole: an absent value clears its column. It carries no actor, because the
 * table records none.</p>
 *
 * @param lockout the lockout, in full
 */
public record FleetLockoutUpdateRequest(@NotNull @Valid FleetLockout lockout) {}
