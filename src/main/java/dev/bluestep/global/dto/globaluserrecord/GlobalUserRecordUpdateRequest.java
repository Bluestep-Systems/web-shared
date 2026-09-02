package dev.bluestep.global.dto.globaluserrecord;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import dev.bluestep.global.dto.constraints.CodePointSize;
import dev.bluestep.global.dto.tenantaccess.ResellerKey;
import jakarta.validation.Valid;

/**
 * Replacing a {@code global.globaluser} row's editable columns.
 *
 * <h2>The whole record, not a patch</h2>
 *
 * <p>Every editable column is named on every call, and an absent {@code Optional} sets the column to
 * NULL rather than leaving it alone. That is what the monolith's own {@code UPDATE} does, and keeping
 * it means a caller cannot half-apply an edit by omitting a field it did not think had changed. A
 * partial update is a different operation with different failure modes; if one is ever wanted it
 * wants its own verb rather than a quiet reinterpretation of this one.</p>
 *
 * <h2>No credential, on purpose</h2>
 *
 * <p>There is no password component here and there is no way to change a credential through this
 * record. {@link GlobalUserRecordCreateRequest} carries the whole reasoning; the part specific to
 * update is that the monolith's statement always writes the password column from its in-memory model,
 * so any update by something that had not first loaded the credential would silently blank it. Leaving
 * the column out of the statement makes that impossible rather than merely unlikely.</p>
 *
 * <p>The key is not here either — it is the path, and a body that could disagree with it would
 * introduce a question about which one wins.</p>
 *
 * @param firstName as stored
 * @param lastName  as stored
 * @param username  the login name
 * @param userEmail the address the monolith writes to
 * @param reseller  the reseller to restrict this account to, or empty to lift the restriction. Both
 *                  halves of the key move together, which is why this is one component.
 * @param superUser the monolith's {@code super} column, written as 1 or 0
 * @param disabled  whether the account is turned off
 * @param attribs   platform endorsements, already under the monolith's cipher
 * @param passwordExpireStart when the current credential's life began
 * @param emailValidated whether the address is confirmed
 * @param emailValidatedExpireStart when that confirmation's life began
 * @param linkedAccounts the external identities linked to this account, in full. An empty list
 *                  unlinks everything — which is the point of a whole-record update, and worth
 *                  saying out loud because it is the component where absence is most easily
 *                  mistaken for "no change".
 */
public record GlobalUserRecordUpdateRequest(
		Optional<@CodePointSize(max = 30) String> firstName,
		Optional<@CodePointSize(max = 30) String> lastName,
		Optional<@CodePointSize(max = 50) String> username,
		Optional<@CodePointSize(max = 255) String> userEmail,
		@Valid Optional<ResellerKey> reseller,
		boolean superUser,
		boolean disabled,
		Optional<@CodePointSize(max = 4000) String> attribs,
		Optional<LocalDateTime> passwordExpireStart,
		boolean emailValidated,
		Optional<LocalDateTime> emailValidatedExpireStart,
		@Valid List<LinkedAccount> linkedAccounts
) {

	/** Jackson binds omitted fields to {@code null} whatever this package's null-marking says. */
	public GlobalUserRecordUpdateRequest {
		firstName = firstName == null ? Optional.empty() : firstName;
		lastName = lastName == null ? Optional.empty() : lastName;
		username = username == null ? Optional.empty() : username;
		userEmail = userEmail == null ? Optional.empty() : userEmail;
		reseller = reseller == null ? Optional.empty() : reseller;
		attribs = attribs == null ? Optional.empty() : attribs;
		passwordExpireStart = passwordExpireStart == null ? Optional.empty() : passwordExpireStart;
		emailValidatedExpireStart =
				emailValidatedExpireStart == null ? Optional.empty() : emailValidatedExpireStart;
		linkedAccounts = linkedAccounts == null ? List.of() : List.copyOf(linkedAccounts);
	}

}
