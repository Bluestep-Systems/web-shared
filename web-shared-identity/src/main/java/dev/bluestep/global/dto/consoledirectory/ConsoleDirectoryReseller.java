package dev.bluestep.global.dto.consoledirectory;

import java.util.Objects;

import dev.bluestep.global.dto.tenantaccess.ResellerKey;

/**
 * A reseller, named, as the Console's directory lists it (from 6.10.0).
 *
 * @param reseller the reseller's key
 * @param name     the name a person reads (the reseller record's display name); blank when none was ever set
 */
public record ConsoleDirectoryReseller(ResellerKey reseller, String name) {

	/** Requires both components. */
	public ConsoleDirectoryReseller {
		Objects.requireNonNull(reseller, "reseller");
		Objects.requireNonNull(name, "name");
	}
}
