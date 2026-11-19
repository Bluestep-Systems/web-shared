package dev.bluestep.global.dto.consoleglobaluser;

import java.util.List;
import java.util.Optional;

import org.jspecify.annotations.Nullable;

/**
 * One page of the Console's global-user directory (from 6.13.0): the answer of
 * {@code GET /console-global-users?first&after&search&includeDisabled}.
 *
 * @param users       the page, by surname then forename (an absent name after every present one), then key
 * @param totalCount  how many accounts match the query, whatever the page
 * @param hasNextPage whether accounts follow this page
 * @param endCursor   opaque: pass it as {@code after} for the next page; absent for an empty page
 */
public record ConsoleGlobalUserPage(List<ConsoleGlobalUser> users, long totalCount, boolean hasNextPage,
		@Nullable String endCursor) {

	/** Copies the page, and refuses a negative count. */
	public ConsoleGlobalUserPage {
		users = List.copyOf(users);
		if (totalCount < 0) {
			throw new IllegalArgumentException("A count is not negative");
		}
	}

	/** {@link #endCursor}, as an {@code Optional}. */
	public Optional<String> optEndCursor() {
		return Optional.ofNullable(endCursor);
	}
}
