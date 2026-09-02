package dev.bluestep.global.dto.globaluserrecord;

import com.fasterxml.jackson.annotation.JsonIgnore;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotBlank;

/**
 * Setting a {@code global.globaluser} row's credential, on its own.
 *
 * <h2>Why this is its own operation rather than a component on update</h2>
 *
 * <p>{@link GlobalUserRecordUpdateRequest} deliberately has no credential component, and that stays
 * true — see its javadoc for the hazard it avoids, which is that an update carrying the column would
 * blank the credential of any account edited by something that had not first loaded it. A separate
 * verb keeps that property: the whole-record update still cannot touch the column, and the only thing
 * that can is a request whose entire subject is the credential.</p>
 *
 * <p>It also closes a gap that was a real defect rather than a theoretical one. The monolith's admin
 * screen has a password field, and the path behind it set the credential on its in-memory model and
 * then called update — so with no credential on the update shape, an administrator changing a global
 * user's password saw the change accepted and discarded. Silently: no error, because nothing had gone
 * wrong from the update's point of view. The screen needs an operation that exists.</p>
 *
 * <h2>Required, and there is no way to clear a credential</h2>
 *
 * <p>{@code storedCredential} is a plain {@code String} under {@code @NotBlank}, not an
 * {@code Optional}. A missing field, an explicit {@code null}, an empty string and a string of spaces
 * are all 400s. <b>This shape has no expressible form that removes a credential.</b></p>
 *
 * <p>That is the point rather than an omission. 4.1.2 modelled the component as an {@code Optional}
 * and read empty as "clear the credential", which put a destructive operation behind the most likely
 * malformed body this endpoint can receive — {@code {}} — and behind the exact payload its intended
 * caller produces by accident. The monolith never loads a credential (no response on this surface
 * carries one), so its in-memory model holds {@code null} for the password unless an administrator
 * has just typed one; a request built from that model without checking would have sent {@code {}} and
 * silently deleted the account's password while reporting success. An admin screen whose password box
 * was left blank is precisely that case.</p>
 *
 * <p>So the caller must have a credential in hand to call this at all, and a caller that does not
 * finds out at the edge with a 400 naming the field instead of afterwards, from a user who can no
 * longer sign in. An update that names nothing to write is not a request to clear something — it is
 * an incoherent request, and the fail-fast reading is the correct one.</p>
 *
 * <p>Removing a credential outright — legitimate for an account that signs in only through a linked
 * identity — is therefore <em>not</em> available here. It is a different operation with a different
 * shape and a different blast radius, and if it is ever wanted it wants its own verb saying so out
 * loud, not an empty body that could equally have been a mistake.</p>
 *
 * <h2>Still not a password</h2>
 *
 * <p>{@code storedCredential} carries the value the monolith's own cipher produced, exactly as
 * {@link GlobalUserRecordCreateRequest#storedCredential()} does, and for the same reason: the column
 * is reversible rather than a digest, so carrying the password itself would put a recoverable
 * credential on an HTTP body, through whatever sits between the two services, and into anything that
 * logs a payload. The service that stores this neither produces nor interprets it, which is what keeps
 * the monolith's password key out of a second system.</p>
 *
 * <p>Write-only, like every other credential shape here: <b>no operation on this surface returns a
 * credential</b>, and this one answers with the account record — which by construction has no
 * credential component — rather than with an echo of what it was given.</p>
 *
 * <h2>Why there is no verify operation beside it</h2>
 *
 * <p>The obvious companion is a verify: the monolith encodes a candidate password with its own key,
 * this service compares the bytes against what it holds, and answers a boolean — which would move the
 * last credential-shaped question off the monolith's direct SQL without the cipher key ever leaving
 * it. The encoding is deterministic, so that comparison would be sound <em>if</em> every stored value
 * had been written by the current encoder.</p>
 *
 * <p>It has not been. Measured over a development copy of the global schema, 133 of the 237 accounts
 * carrying a credential — 56.1% — hold an <b>earlier cipher generation</b>, which the monolith reads
 * by decoding rather than by matching. Encoding a candidate today produces the current generation, so
 * a byte comparison against one of those rows returns false for the correct password. A verify that
 * is wrong for the majority of accounts, and wrong in the direction of refusing a valid sign-in, is
 * worse than no verify at all, so this version ships the write and not the check. See
 * {@link StoredForm#carriesGenerationMarker} for the measurement in full.</p>
 *
 * <p>What would make verify shippable is a normalization pass that re-encodes the older generation —
 * which the monolith is already positioned to do, because it decodes to plaintext on every read and
 * re-encodes on every write, so every account that changes its password migrates itself. Verify wants
 * that population at zero, or an operation that says which generation it is asking about. Either is a
 * decision with its own reasoning rather than a detail of this one.</p>
 *
 * @param storedCredential the monolith's stored representation of the new password — <b>never a
 *                         password</b>, and never absent. Required, for the reasons above.
 */
public record GlobalUserCredentialUpdateRequest(
		@NotBlank String storedCredential
) {

	/**
	 * Refuses a credential that has plainly not been through the monolith's encoder.
	 *
	 * <p>{@link StoredForm#carriesGenerationMarker} carries what this does and does not establish,
	 * including which cipher generations it accepts and what that costs.</p>
	 *
	 * <p>Answers {@code true} for any value {@code @NotBlank} already refuses — null, empty, or all
	 * whitespace — so that one omission is reported once, as the absence it is, rather than twice with
	 * this constraint also complaining that nothing is not in the monolith's stored form. Absence in
	 * every shape it takes belongs to {@code @NotBlank}; this constraint only has an opinion about a
	 * value that is actually there.</p>
	 *
	 * <p>{@code @JsonIgnore} is not decoration. A {@code @AssertTrue} method is a JavaBeans getter, so
	 * without it Jackson serializes {@code storedCredentialInStoredForm} as a property of this record —
	 * and on a credential shape in particular, an extra derived field on the wire is the last thing
	 * wanted.</p>
	 */
	@JsonIgnore
	@AssertTrue(message = "storedCredential must be the monolith's stored form, not a password")
	public boolean isStoredCredentialInStoredForm() {
		return storedCredential == null || storedCredential.isBlank()
				|| StoredForm.carriesGenerationMarker(storedCredential);
	}
}
