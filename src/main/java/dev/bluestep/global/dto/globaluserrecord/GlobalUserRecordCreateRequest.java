package dev.bluestep.global.dto.globaluserrecord;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import com.fasterxml.jackson.annotation.JsonIgnore;

import dev.bluestep.global.dto.tenantaccess.ResellerKey;
import jakarta.validation.Valid;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

/**
 * Creating a {@code global.globaluser} row.
 *
 * <h2>The credential, and why it is shaped like this</h2>
 *
 * <p>{@code global.globaluser.password} is <b>not a password hash</b>. The monolith writes it through
 * a reversible cipher and recovers the plaintext when it reads the row back; its own comparison is
 * then plaintext against plaintext. So there is no digest anywhere in this design to pass around, and
 * "just carry the column" would mean carrying recoverable user passwords on HTTP bodies, through
 * whatever sits between the two services, and into anything that logs a payload. That is a different
 * class of exposure from the one the column already has at rest, and it is why this record does not
 * simply mirror the monolith's column list.</p>
 *
 * <p>Three rules fall out of that, and together they are the whole credential design:</p>
 *
 * <ol>
 *   <li><b>No response ever carries it.</b> Not the by-key read, not either lookup, and above all not
 *       the list — a list that carried it would hand every account's recoverable credential to any
 *       caller holding the service key, in one call.</li>
 *   <li><b>Update does not carry it either.</b> A password change is not an edit to the account
 *       record and does not travel with one. That also removes a hazard the monolith has today: its
 *       {@code UPDATE} always writes the password column from whatever the in-memory model happens to
 *       hold, so an update by anything that had not loaded the credential first would blank it. An
 *       update statement that does not name the column cannot do that.</li>
 *   <li><b>Create carries it once, as the stored form.</b> An account created without one could never
 *       sign in and nothing on this surface could fix that, so create is the one operation that has
 *       to. It carries {@code storedCredential} — the value the monolith's own cipher produced — not
 *       a password. The service that stores it neither produces nor interprets it, which is what
 *       keeps the monolith's password key out of a second system.</li>
 * </ol>
 *
 * <p>What this deliberately does <em>not</em> do is move credential <em>verification</em>. Checking a
 * submitted password against a stored one requires the cipher, and copying that key into another
 * service is a security decision with its own blast radius rather than a detail of retiring some SQL.
 * Verification stays where the key is.</p>
 *
 * @param classid        the key class the new account belongs to. Required: the class vocabulary is
 *                       the caller's, not this service's, and inferring one would be guessing at
 *                       which kind of key a row is.
 * @param seqnum         the sequence number to give it, or empty to have one allocated from the
 *                       global schema's own {@code SEQ_GLOBALUSER} sequence — the same sequence the
 *                       monolith draws from, so the two cannot collide by taking different advice.
 * @param firstName      as stored
 * @param lastName       as stored
 * @param username       the login name
 * @param userEmail      the address the monolith writes to
 * @param reseller       the reseller to restrict this account to, or empty for an unrestricted one
 * @param superUser      the monolith's {@code super} column, written as 1 or 0. See
 *                       {@link GlobalUserRecordResponse} on why this is not the catalog question.
 * @param disabled       whether the account starts turned off
 * @param attribs        platform endorsements, already under the monolith's cipher — opaque here,
 *                       exactly as it is on the way back out
 * @param passwordExpireStart when the credential's life begins
 * @param emailValidated whether the address is already confirmed
 * @param emailValidatedExpireStart when that confirmation's life begins
 * @param linkedAccounts the external identities to link
 * @param storedCredential the monolith's stored representation of the password — <b>never a
 *                       password</b>. Write-only: no operation on this surface returns it. Empty
 *                       creates an account with no credential, which is legitimate for one that will
 *                       only ever sign in through a linked account.
 */
public record GlobalUserRecordCreateRequest(
		@Positive long classid,
		Optional<Long> seqnum,
		Optional<@Size(max = 30) String> firstName,
		Optional<@Size(max = 30) String> lastName,
		Optional<@Size(max = 50) String> username,
		Optional<@Size(max = 255) String> userEmail,
		@Valid Optional<ResellerKey> reseller,
		boolean superUser,
		boolean disabled,
		Optional<@Size(max = 4000) String> attribs,
		Optional<LocalDateTime> passwordExpireStart,
		boolean emailValidated,
		Optional<LocalDateTime> emailValidatedExpireStart,
		@Valid List<LinkedAccount> linkedAccounts,
		Optional<String> storedCredential
) {

	/** Jackson binds omitted fields to {@code null} whatever this package's null-marking says. */
	public GlobalUserRecordCreateRequest {
		seqnum = seqnum == null ? Optional.empty() : seqnum;
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
		storedCredential = storedCredential == null ? Optional.empty() : storedCredential;
	}

	/**
	 * Refuses a credential that has plainly not been through the monolith's encoder.
	 *
	 * <p>{@link StoredForm#carriesGenerationMarker} carries what this does and does not establish. In
	 * particular it accepts only a <em>marked</em> value, which is narrower than the set the monolith
	 * can decode — the unmarked earlier generation is 56.1% of stored rows and is measured there. This
	 * record's javadoc previously blurred that by calling the marker something "the monolith's encoder
	 * puts in front of every value it produces". Every value it produces <em>today</em>, which is not
	 * the same claim.</p>
	 *
	 * <p>{@code @JsonIgnore} is not decoration. A {@code @AssertTrue} method is a JavaBeans getter, so
	 * without it Jackson serializes {@code storedCredentialInStoredForm} as a property of this record —
	 * putting a field on the wire that the record cannot bind back, so the type fails to round-trip
	 * through its own mapper.</p>
	 */
	@JsonIgnore
	@AssertTrue(message = "storedCredential must be the monolith's stored form, not a password")
	public boolean isStoredCredentialInStoredForm() {
		return storedCredential.map(StoredForm::carriesGenerationMarker).orElse(true);
	}

}
