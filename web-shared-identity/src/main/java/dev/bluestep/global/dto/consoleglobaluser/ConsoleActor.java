package dev.bluestep.global.dto.consoleglobaluser;

import java.util.Objects;
import java.util.Optional;

import org.jspecify.annotations.Nullable;

import dev.bluestep.global.dto.tenantaccess.GlobalUserKey;

/**
 * A global user who did something (created or last edited an account, gave a grant), named as the Console names one
 * (from 6.13.0).
 *
 * @param user the global user's key
 * @param name first and last name, else the username; absent when web-global no longer holds the account, or holds
 *             neither
 */
public record ConsoleActor(GlobalUserKey user, @Nullable String name) {

	/** Requires the key. */
	public ConsoleActor {
		Objects.requireNonNull(user, "user");
	}

	/** {@link #name}, as an {@code Optional}. */
	public Optional<String> optName() {
		return Optional.ofNullable(name);
	}
}
