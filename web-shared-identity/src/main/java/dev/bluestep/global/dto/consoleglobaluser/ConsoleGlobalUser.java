package dev.bluestep.global.dto.consoleglobaluser;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

import org.jspecify.annotations.Nullable;

import dev.bluestep.global.dto.tenantaccess.GlobalUserKey;

/**
 * One global user as the Console's directory lists it (from 6.13.0): the account, without its credential, its
 * {@code attribs} ciphertext or a linked account's subject.
 *
 * <p>The three stamps are {@code LocalDateTime} because their columns are {@code timestamp without time zone}: a
 * wall-clock reading of the stamp zone (America/Phoenix in every deployment), which the reader puts the offset on.</p>
 *
 * @param user                the account's key
 * @param firstName           as stored
 * @param lastName            as stored; the directory's first sort key
 * @param username            the login name; the column is nullable
 * @param email               the account's e-mail address
 * @param emailValidated      whether that address has been confirmed
 * @param superUser           the {@code super} column: set for fleet supers and reseller supers alike, so
 *                            {@link #reseller} says which
 * @param reseller            the reseller the account is restricted to; absent for one that is not
 * @param disabled            whether the account is turned off
 * @param passwordExpireStart when the password-expiry clock started (wall clock of the stamp zone); absent when none
 *                            runs. Honoured by the monolith's sign-in only.
 * @param createdAt           when the account was created (wall clock); absent for accounts that predate the column
 * @param modifiedAt          when it was last edited (wall clock); absent when never edited since
 * @param createdBy           the global user who created it; absent when none is recorded, or a tenant user did
 * @param modifiedBy          the global user who last edited it; on the same terms
 * @param version             opaque: the account's tag (its version and its credential's change counter), what a
 *                            later write hands back to say which state it was decided on
 * @param linkedAccounts      the linked identities, in stored order, each reduced to its issuer, provider and subject
 *                            hint; absent when the stored list cannot be read whole (so a partial list never reads as
 *                            all of them)
 */
public record ConsoleGlobalUser(
		GlobalUserKey user,
		@Nullable String firstName,
		@Nullable String lastName,
		@Nullable String username,
		@Nullable String email,
		boolean emailValidated,
		boolean superUser,
		@Nullable ConsoleReseller reseller,
		boolean disabled,
		@Nullable LocalDateTime passwordExpireStart,
		@Nullable LocalDateTime createdAt,
		@Nullable LocalDateTime modifiedAt,
		@Nullable ConsoleActor createdBy,
		@Nullable ConsoleActor modifiedBy,
		String version,
		@Nullable List<ConsoleLinkedAccount> linkedAccounts) {

	/** Requires the key and version, and copies the linked accounts. */
	public ConsoleGlobalUser {
		Objects.requireNonNull(user, "user");
		Objects.requireNonNull(version, "version");
		linkedAccounts = linkedAccounts == null ? null : List.copyOf(linkedAccounts);
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

	/** {@link #email}, as an {@code Optional}. */
	public Optional<String> optEmail() {
		return Optional.ofNullable(email);
	}

	/** {@link #reseller}, as an {@code Optional}. */
	public Optional<ConsoleReseller> optReseller() {
		return Optional.ofNullable(reseller);
	}

	/** {@link #passwordExpireStart}, as an {@code Optional}. */
	public Optional<LocalDateTime> optPasswordExpireStart() {
		return Optional.ofNullable(passwordExpireStart);
	}

	/** {@link #createdAt}, as an {@code Optional}. */
	public Optional<LocalDateTime> optCreatedAt() {
		return Optional.ofNullable(createdAt);
	}

	/** {@link #modifiedAt}, as an {@code Optional}. */
	public Optional<LocalDateTime> optModifiedAt() {
		return Optional.ofNullable(modifiedAt);
	}

	/** {@link #createdBy}, as an {@code Optional}. */
	public Optional<ConsoleActor> optCreatedBy() {
		return Optional.ofNullable(createdBy);
	}

	/** {@link #modifiedBy}, as an {@code Optional}. */
	public Optional<ConsoleActor> optModifiedBy() {
		return Optional.ofNullable(modifiedBy);
	}

	/** {@link #linkedAccounts}, as an {@code Optional}: empty when the stored list cannot be read whole. */
	public Optional<List<ConsoleLinkedAccount>> optLinkedAccounts() {
		return Optional.ofNullable(linkedAccounts);
	}
}
