package dev.bluestep.global.dto.globaluserrecord;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;

/**
 * Resolving a global user by the external identity they signed in with, at no tenant (6.9.0): the gateway's
 * single sign-on, which happens at gateway.bluestep.net and not inside an organization
 * ({@link UntenantedUsernameLookupRequest}'s reason, for {@link LinkedAccountLookupRequest}'s question).
 *
 * <p>Every account carrying the identity resolves, whatever its reseller; the answer is the account's
 * {@link GlobalUserRecordResponse}, whose {@code reseller} says whether it is restricted to one, as data. A caller
 * deciding inside an organization applies that restriction itself, or asks {@link LinkedAccountLookupRequest}
 * instead; signing in at no tenant does not widen what the account may reach afterwards, which is decided from its
 * record and grants. Matched exactly as {@link LinkedAccountLookupRequest} matches: both halves, containment over the
 * stored array, the lowest key when several accounts carry it. Not found is a 404 with no body. A POST for a read,
 * so the identity stays out of URLs and logs.</p>
 *
 * @param linkedAccount the issuer and subject from the provider's ID token, exactly as it carries them
 */
public record UntenantedLinkedAccountLookupRequest(@NotNull @Valid LinkedAccount linkedAccount) {
}
