package dev.bluestep.global.dto.globaluserrecord;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import org.jspecify.annotations.Nullable;

import dev.bluestep.global.dto.constraints.CodePointSize;
import dev.bluestep.global.dto.tenantaccess.ResellerKey;
import jakarta.validation.Valid;

/**
 * Replacing a {@code global.globaluser} row's editable columns.
 *
 * <h2>The whole record, not a patch</h2>
 *
 * <p>Every editable column is named on every call, and an absent value sets the column to
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
		@CodePointSize(max = 30) @Nullable String firstName,
		@CodePointSize(max = 30) @Nullable String lastName,
		@CodePointSize(max = 50) @Nullable String username,
		@CodePointSize(max = 255) @Nullable String userEmail,
		@Valid @Nullable ResellerKey reseller,
		boolean superUser,
		boolean disabled,
		@CodePointSize(max = 4000) @Nullable String attribs,
		@Nullable LocalDateTime passwordExpireStart,
		boolean emailValidated,
		@Nullable LocalDateTime emailValidatedExpireStart,
		@Valid List<LinkedAccount> linkedAccounts
) {

	/** An omitted {@code linkedAccounts} binds as {@code null}; it is defaulted to empty and copied. */
	public GlobalUserRecordUpdateRequest {
		linkedAccounts = linkedAccounts == null ? List.of() : List.copyOf(linkedAccounts);
	}

	/** {@link #firstName}, as an {@code Optional}. */
	public Optional<String> optFirstName() {
		return Optional.ofNullable(firstName);
	}

	/** {@link #lastName}, as an {@code Optional}. */
	public Optional<String> optLastName() {
		return Optional.ofNullable(lastName);
	}

	/** {@link #username}, as an {@code Optional}. */
	public Optional<String> optUsername() {
		return Optional.ofNullable(username);
	}

	/** {@link #userEmail}, as an {@code Optional}. */
	public Optional<String> optUserEmail() {
		return Optional.ofNullable(userEmail);
	}

	/** {@link #reseller}, as an {@code Optional}. */
	public Optional<ResellerKey> optReseller() {
		return Optional.ofNullable(reseller);
	}

	/** {@link #attribs}, as an {@code Optional}. */
	public Optional<String> optAttribs() {
		return Optional.ofNullable(attribs);
	}

	/** {@link #passwordExpireStart}, as an {@code Optional}. */
	public Optional<LocalDateTime> optPasswordExpireStart() {
		return Optional.ofNullable(passwordExpireStart);
	}

	/** {@link #emailValidatedExpireStart}, as an {@code Optional}. */
	public Optional<LocalDateTime> optEmailValidatedExpireStart() {
		return Optional.ofNullable(emailValidatedExpireStart);
	}
}
