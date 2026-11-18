package dev.bluestep.global.dto.consoledirectory;

import java.util.List;
import java.util.Objects;

/**
 * web-global's answer to {@code GET /api/contract/console-directory} (from 6.10.0): every reseller, and what it
 * holds about every tenant beyond the tenant record, in one fetch. Unpaged: the fleet's tenants number in the
 * hundreds.
 *
 * @param resellers every reseller, by name then key
 * @param tenants   one entry per tenant, by key
 */
public record ConsoleDirectoryResponse(List<ConsoleDirectoryReseller> resellers, List<ConsoleDirectoryTenant> tenants) {

	/** Copies both lists, so an answer cannot change once built. */
	public ConsoleDirectoryResponse {
		resellers = List.copyOf(Objects.requireNonNull(resellers, "resellers"));
		tenants = List.copyOf(Objects.requireNonNull(tenants, "tenants"));
	}
}
