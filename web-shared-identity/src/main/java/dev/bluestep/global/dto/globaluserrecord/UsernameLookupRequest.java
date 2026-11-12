package dev.bluestep.global.dto.globaluserrecord;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;

/**
 * Resolving a global user by login name, within one organization.
 *
 * <h2>The organization is part of the question</h2>
 *
 * <p>A global user restricted to a reseller must not resolve inside an organization that reseller
 * does not own. That is not a filter applied to an answer — it changes what the answer <em>is</em>.
 * Asked without an organization, "who is {@code jsmith}" has one answer; asked inside an organization
 * whose reseller does not own that account, it has none, and the caller must be told none rather than
 * be handed a row to check afterwards. A caller that received the row and forgot the check would have
 * signed somebody in across a reseller boundary.</p>
 *
 * <p>So {@code associationSeqnum} is required and there is no unscoped form of this call. An account
 * with no reseller resolves in every organization; a reseller-scoped one resolves only where
 * {@code global.association} agrees the organization belongs to that reseller.</p>
 *
 * <h2>A POST for a read</h2>
 *
 * <p>Because the login name is the payload. In a path segment it would need escaping for every
 * character a username may legally contain, and in a query string it would be written into every
 * access log between here and the service — for a value that identifies a person and arrives on the
 * sign-in path. The AI surface's {@code /usage/query} is a query expressed the same way for the same
 * kind of reason.</p>
 *
 * @param associationSeqnum the organization the question is being asked inside
 * @param username          the login name, matched case-insensitively — the monolith compares
 *                          {@code upper(username)} against {@code upper(?)}, so this is not a
 *                          normalization the caller has to do or can get wrong
 */
public record UsernameLookupRequest(
		@Positive long associationSeqnum,
		@NotBlank String username
) {
}
