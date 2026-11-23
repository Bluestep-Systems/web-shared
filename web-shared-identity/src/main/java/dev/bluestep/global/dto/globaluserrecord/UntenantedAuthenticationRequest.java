package dev.bluestep.global.dto.globaluserrecord;

import dev.bluestep.global.dto.constraints.CodePointSize;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;

/**
 * Checking a password a global user just typed, at no tenant (6.4.0): {@link GlobalUserAuthenticationRequest}
 * without the tenant. The candidate is never written down; {@link #toString()} leaves it out.
 *
 * @param username  the login name, matched case-insensitively; at most 50 code points
 * @param candidate the password as typed, not trimmed; 1 to
 *                  {@value GlobalUserAuthenticationRequest#MAX_CANDIDATE_LENGTH} code points
 */
public record UntenantedAuthenticationRequest(
		@NotBlank @CodePointSize(max = 50) String username,
		@NotEmpty @CodePointSize(max = GlobalUserAuthenticationRequest.MAX_CANDIDATE_LENGTH) String candidate
) {

	/** Everything but the candidate, which is replaced by a fixed placeholder. */
	@Override
	public String toString() {
		return "UntenantedAuthenticationRequest[username=" + username + ", candidate=<redacted>]";
	}
}
