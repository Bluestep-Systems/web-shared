package dev.bluestep.global.dto.tenant;

import java.time.LocalDateTime;
import java.util.Optional;

import org.jspecify.annotations.Nullable;

/**
 * A tenant's own lock: its status, its lockout window and the messages shown around it. The fleet-wide
 * lock ({@code global.associationglobal}) is not part of a tenant and is not here.
 *
 * <p>Timestamps are {@code LocalDateTime}: the column is {@code timestamp without time zone} and
 * carries a wall-clock reading with no zone.</p>
 *
 * @param status        the monolith's {@code LockStatus} ordinal, verbatim; zero when the column is
 *                      absent, as the monolith reads it. web-global does not interpret it.
 * @param autoOff       {@code lockautooff}: the lock lifts itself when its window ends
 * @param lockedOut     when the lockout window began, absent if none was ever set
 * @param durationMinutes the window's length, absent if none was set
 * @param preMessage    shown before the lock begins
 * @param mainMessage   shown while it holds
 * @param postMessage   shown after it lifts
 */
public record TenantLock(
		int status,
		boolean autoOff,
		@Nullable LocalDateTime lockedOut,
		@Nullable Integer durationMinutes,
		@Nullable String preMessage,
		@Nullable String mainMessage,
		@Nullable String postMessage) {

	/** {@link #lockedOut}, as an {@code Optional}. */
	public Optional<LocalDateTime> optLockedOut() {
		return Optional.ofNullable(lockedOut);
	}

	/** {@link #durationMinutes}, as an {@code Optional}. */
	public Optional<Integer> optDurationMinutes() {
		return Optional.ofNullable(durationMinutes);
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
