package dev.bluestep.global.dto.globaluserrecord;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * An external identity provider account linked to a global user: the OpenID Connect
 * {@code (issuer, sub)} pair, as {@code global.globaluser.linkedaccounts} holds it.
 *
 * <p>Both halves, always. {@code sub} is unique only within an issuer, so a match on the subject
 * alone would resolve one provider's user against another provider's identifier — the same hazard
 * {@link dev.bluestep.global.dto.tenantaccess.GlobalUserKey} spells out for {@code seqnum} within a
 * {@code classid}, and with the same consequence: somebody is signed in as a person they are not.</p>
 *
 * <p>Not a credential. The pair says which account at which provider this user is, and the provider
 * is what proves it — so this travels on reads, where a password never does.</p>
 *
 * @param issuer the identity provider's issuer identifier, as it appears in the ID token
 * @param sub    the provider's stable subject identifier for this user
 */
public record LinkedAccount(
		@NotBlank @Size(max = 1000) String issuer,
		@NotBlank @Size(max = 1000) String sub
) {

	/** The canonical rendering used in log lines: {@code issuer#sub}. */
	@Override
	public String toString() {
		return issuer + "#" + sub;
	}
}
