package dev.bluestep.global.dto.globaluserrecord;

import dev.bluestep.global.dto.tenant.TenantKey;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;

/**
 * Checking a password a global user just typed, inside one tenant.
 *
 * <h2>The one request on this surface that carries a password</h2>
 *
 * <p>Every other credential shape here carries the monolith's <em>stored form</em> and never a
 * password. This one cannot: the question is whether the candidate is the password behind the stored
 * Argon2id hash, and only the candidate itself can answer it. What keeps that tolerable is what the
 * answer may and may not be: an outcome and the account record, never the stored form, and nothing
 * here or on the service writes the candidate down. {@link #toString()} leaves it out, so a request
 * that reaches a log line through a framework's argument logging still does not carry it.</p>
 *
 * <p>Only a one-way ({@code \n2}, Argon2id) credential is checked. A reversible one would need the
 * monolith's cipher key, which stays in the monolith; for such a row the answer is
 * {@link GlobalUserAuthenticationOutcome#LEGACY_CREDENTIAL} and the monolith checks it itself.</p>
 *
 * <h2>Scoped exactly as the username lookup is</h2>
 *
 * <p>{@code tenant} carries the weight {@code associationSeqnum} carries on
 * {@link UsernameLookupRequest}: a reseller-scoped account does not exist inside a tenant its reseller
 * does not own, so it is {@link GlobalUserAuthenticationOutcome#NO_MATCH} there whatever the password.
 * It is the whole key rather than a bare seqnum, so nothing but a tenant can be passed for it; a key
 * whose classid is not {@link TenantKey#CLASSID} names no tenant and is refused by web-global.</p>
 *
 * @param tenant    the tenant the sign-in is happening inside
 * @param username  the login name, matched case-insensitively as the lookup matches it
 * @param candidate the password as typed. Not trimmed and not normalized: a password of spaces is a
 *                  password, so only an empty one is refused.
 */
public record GlobalUserAuthenticationRequest(
		@NotNull TenantKey tenant,
		@NotBlank String username,
		@NotEmpty String candidate
) {

	/** Everything but the candidate, which is replaced by a fixed placeholder. */
	@Override
	public String toString() {
		return "GlobalUserAuthenticationRequest[tenant=" + tenant + ", username=" + username
				+ ", candidate=<redacted>]";
	}
}
