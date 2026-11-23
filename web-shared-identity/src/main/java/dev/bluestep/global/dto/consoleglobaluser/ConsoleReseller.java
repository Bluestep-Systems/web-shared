package dev.bluestep.global.dto.consoleglobaluser;

import java.util.Objects;
import java.util.Optional;

import org.jspecify.annotations.Nullable;

import dev.bluestep.global.dto.tenantaccess.ResellerKey;

/**
 * A reseller an account or a catalog scope names, with its name (from 6.13.0).
 *
 * @param reseller the reseller's key
 * @param name     its display name; absent when web-global holds no reseller under that key (a catalog scope's
 *                 reseller has no foreign key)
 */
public record ConsoleReseller(ResellerKey reseller, @Nullable String name) {

	/** Requires the key. */
	public ConsoleReseller {
		Objects.requireNonNull(reseller, "reseller");
	}

	/** {@link #name}, as an {@code Optional}. */
	public Optional<String> optName() {
		return Optional.ofNullable(name);
	}
}
