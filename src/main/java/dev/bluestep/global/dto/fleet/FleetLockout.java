package dev.bluestep.global.dto.fleet;

import java.time.LocalDateTime;
import java.util.Optional;
import java.util.OptionalInt;

import org.jspecify.annotations.Nullable;

import dev.bluestep.global.dto.constraints.CodePointSize;

/**
 * The fleet-wide lockout: web-global's {@code fleet_lockout}, the monolith's single
 * {@code associationglobal} row. {@code GET /fleet-lockout} answers it with its version as the
 * {@code ETag}; {@link FleetLockoutUpdateRequest} writes it whole.
 *
 * <p>Its components are {@code TenantLock}'s, but it is its own type: a fleet lockout is not a tenant's,
 * and the type names what it is about. Timestamps are {@code LocalDateTime}: the column is
 * {@code timestamp without time zone}.</p>
 *
 * <p>Each message is bounded at the width of its {@code varchar(2000)} column, checked where a request
 * marks the lockout {@code @Valid}.</p>
 *
 * @param status          the monolith's {@code LockStatus} ordinal, verbatim; zero when the column is
 *                        absent, as the monolith reads it
 * @param autoOff         the lockout lifts itself when its window ends
 * @param lockedOut       when the lockout window began, absent if none was set
 * @param durationMinutes the window's length, absent if none was set
 * @param preMessage      shown before the lockout begins
 * @param mainMessage     shown while it holds
 * @param postMessage     shown after it lifts
 */
public record FleetLockout(
		int status,
		boolean autoOff,
		@Nullable LocalDateTime lockedOut,
		@Nullable Integer durationMinutes,
		@CodePointSize(max = 2000) @Nullable String preMessage,
		@CodePointSize(max = 2000) @Nullable String mainMessage,
		@CodePointSize(max = 2000) @Nullable String postMessage) {

	/** {@link #lockedOut}, as an {@code Optional}. */
	public Optional<LocalDateTime> optLockedOut() {
		return Optional.ofNullable(lockedOut);
	}

	/** {@link #durationMinutes}, as an {@code OptionalInt}. */
	public OptionalInt optDurationMinutes() {
		return durationMinutes == null ? OptionalInt.empty() : OptionalInt.of(durationMinutes);
	}

	/** {@link #preMessage}, as an {@code Optional}. */
	public Optional<String> optPreMessage() {
		return Optional.ofNullable(preMessage);
	}

	/** {@link #mainMessage}, as an {@code Optional}. */
	public Optional<String> optMainMessage() {
		return Optional.ofNullable(mainMessage);
	}

	/** {@link #postMessage}, as an {@code Optional}. */
	public Optional<String> optPostMessage() {
		return Optional.ofNullable(postMessage);
	}
}
