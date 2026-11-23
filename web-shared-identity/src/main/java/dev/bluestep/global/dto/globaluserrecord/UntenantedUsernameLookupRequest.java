package dev.bluestep.global.dto.globaluserrecord;

import dev.bluestep.global.dto.constraints.CodePointSize;
import jakarta.validation.constraints.NotBlank;

/**
 * Resolving a global user by login name at no tenant (6.4.0): the gateway's sign-in, which happens at
 * gateway.bluestep.net and not inside an organization. Every account resolves, whatever its reseller; what it
 * may reach afterwards is decided from its record and grants, not from where it signed in. A POST for a read for
 * {@link UsernameLookupRequest}'s reasons.
 *
 * @param username the login name, matched case-insensitively; at most 50 code points
 */
public record UntenantedUsernameLookupRequest(@NotBlank @CodePointSize(max = 50) String username) {
}
