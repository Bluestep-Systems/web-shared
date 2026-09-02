package dev.bluestep.global.dto.globaluserrecord;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

/**
 * Resolving a global user by the external identity they signed in with, within one organization.
 *
 * <p>The account matches when {@code global.globaluser.linkedaccounts} contains an entry with both
 * this issuer and this subject — containment over the stored array, so an account may carry several
 * linked identities and be reachable by any of them.</p>
 *
 * <p>{@code associationSeqnum} carries exactly the weight it carries on {@link UsernameLookupRequest},
 * for exactly the same reason: a reseller-scoped account must not resolve inside an organization its
 * reseller does not own, and that is part of the question rather than something to check afterwards.
 * Signing in through an identity provider does not widen who somebody is allowed to be here.</p>
 *
 * @param associationSeqnum the organization the question is being asked inside
 * @param linkedAccount     the issuer and subject from the provider's ID token
 */
public record LinkedAccountLookupRequest(
		@Positive long associationSeqnum,
		@NotNull @Valid LinkedAccount linkedAccount
) {
}
