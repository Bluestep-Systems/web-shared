package dev.bluestep.global.dto.globaluserrecord;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import dev.bluestep.global.dto.tenantaccess.GlobalUserKey;
import dev.bluestep.global.dto.tenantaccess.ResellerKey;

/**
 * One {@code global.globaluser} row — the account, minus its credential.
 *
 * <h2>Every column the monolith reads except one</h2>
 *
 * <p>The monolith's own select over this table names sixteen columns. Fifteen of them are here.
 * {@code password} is not, and its absence is a decision rather than an oversight — see
 * {@link GlobalUserRecordCreateRequest} for why a response carrying it would be publishing
 * recoverable passwords rather than digests. Nothing on this surface returns a credential, which is
 * what makes a list of every account a safe thing to ask for.</p>
 *
 * @param user      the account's key
 * @param firstName as stored; the column is nullable and plenty of rows leave it so
 * @param lastName  as stored; the listing's primary sort key, upper-cased
 * @param username  the login name. Nullable in the table, and a row without one simply cannot be
 *                  resolved by {@link UsernameLookupRequest}.
 * @param userEmail the address the monolith writes to
 * @param reseller  the reseller this account is restricted to, or empty for one that is not
 *                  reseller-scoped. Both halves of the key or neither — half a key identifies
 *                  nothing, which is why this is one component rather than two.
 * @param superUser the monolith's {@code super} column, read as it reads it: any non-zero value is
 *                  {@code true}. <b>Not the answer to "does this person see every tenant"</b> — the
 *                  column defaults to 1, so an unconsidered row reads as unrestricted, and it is
 *                  legitimately set for a reseller super whose reach is one reseller. That question
 *                  is answered by {@link dev.bluestep.global.dto.globaluser.GlobalUserScopeResponse}
 *                  and by nothing here.
 * @param disabled  whether the monolith has turned the account off: {@code disabled <> 0}. The
 *                  column is a {@code smallint} with no CHECK, and an unfamiliar value is read as
 *                  disabled rather than as live, which is the safe direction for an account somebody
 *                  deliberately touched.
 * @param attribs   the account's platform endorsements, <b>as stored</b>. The monolith holds this
 *                  column under its own cipher, so the value here is that ciphertext: opaque to
 *                  everything between the two systems, and decrypted only by the monolith at its own
 *                  edge. Not a credential — it is an entitlement string — but it is not readable
 *                  either, and nothing should try.
 * @param passwordExpireStart when the current password's life began, or empty for an account with no
 *                  expiry clock running. {@code LocalDateTime} because the column is
 *                  {@code timestamp without time zone}: it carries a wall-clock reading and no zone,
 *                  and dressing it up as an instant would invent an offset nobody stored.
 * @param emailValidated whether the address above has been confirmed
 * @param emailValidatedExpireStart when that confirmation's life began, on the same terms as
 *                  {@code passwordExpireStart}
 * @param linkedAccounts every external identity linked to this account, in the order stored. Empty
 *                  for an account that signs in with a password only.
 */
public record GlobalUserRecordResponse(
		GlobalUserKey user,
		Optional<String> firstName,
		Optional<String> lastName,
		Optional<String> username,
		Optional<String> userEmail,
		Optional<ResellerKey> reseller,
		boolean superUser,
		boolean disabled,
		Optional<String> attribs,
		Optional<LocalDateTime> passwordExpireStart,
		boolean emailValidated,
		Optional<LocalDateTime> emailValidatedExpireStart,
		List<LinkedAccount> linkedAccounts
) {

	/**
	 * Jackson binds an omitted array to {@code null} whatever this package's null-marking says, and a
	 * caller that built the list itself keeps a handle on it — so the list is both defaulted and
	 * copied.
	 */
	public GlobalUserRecordResponse {
		linkedAccounts = linkedAccounts == null ? List.of() : List.copyOf(linkedAccounts);
	}
}
