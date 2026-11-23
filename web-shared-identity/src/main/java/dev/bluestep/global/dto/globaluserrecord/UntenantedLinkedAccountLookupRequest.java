package dev.bluestep.global.dto.globaluserrecord;

import java.util.Map;

import org.jspecify.annotations.Nullable;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

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
 * <p><b>Strings only, on the wire.</b> Bound through {@link #fromJson}, which takes {@code issuer} and {@code sub} only
 * as JSON strings: a mapper's scalar coercion would otherwise turn {@code "sub":123} or {@code "sub":true} into the
 * text {@code "123"} or {@code "true"} before validation runs, and a malformed request would resolve whichever account
 * holds that text. Anything else (a number, a boolean, an object, a missing member, no {@code linkedAccount}) fails
 * binding. {@link LinkedAccount} itself, and the requests that carry it, keep their binding unchanged.</p>
 *
 * @param linkedAccount the issuer and subject from the provider's ID token, exactly as it carries them
 */
public record UntenantedLinkedAccountLookupRequest(@NotNull @Valid LinkedAccount linkedAccount) {

	/** The wire's binding: {@code {"linkedAccount":{"issuer":"…","sub":"…"}}}, both members JSON strings. */
	@JsonCreator
	static UntenantedLinkedAccountLookupRequest fromJson(
			@JsonProperty("linkedAccount") @Nullable Map<String, @Nullable Object> linkedAccount) {
		if (linkedAccount == null) {
			throw new IllegalArgumentException("linkedAccount is required");
		}
		return new UntenantedLinkedAccountLookupRequest(new LinkedAccount(text(linkedAccount, "issuer"),
				text(linkedAccount, "sub")));
	}

	private static String text(Map<String, @Nullable Object> linkedAccount, String member) {
		if (linkedAccount.get(member) instanceof String value) {
			return value;
		}
		throw new IllegalArgumentException("linkedAccount." + member + " must be a JSON string");
	}
}
