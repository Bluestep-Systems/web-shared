/**
 * The {@code global.globaluser} row itself — the account record, as distinct from
 * {@link dev.bluestep.global.dto.globaluser}, which carries how wide that account's tenant catalog
 * is.
 *
 * <p>The two are neighbours and are deliberately not variants of one another. A
 * {@code GlobalUserScopeResponse} answers "what is this person shown"; a
 * {@link dev.bluestep.global.dto.globaluserrecord.GlobalUserRecordResponse} answers "who is this
 * person" — their name, username, email, reseller, and the monolith's own flags. Neither is derived
 * from the other, and the scope surface deliberately does not list every account.</p>
 *
 * <h2>No credential appears in this package, in either direction on a read</h2>
 *
 * <p>{@code global.globaluser.password} is <em>not</em> a password hash. The monolith stores it under
 * a reversible cipher and recovers the plaintext when it reads the row, so a response carrying that
 * column would be carrying recoverable user passwords — categorically different from carrying a
 * digest. No response record here has a credential component, and the two requests that do
 * ({@link dev.bluestep.global.dto.globaluserrecord.GlobalUserRecordCreateRequest} and
 * {@link dev.bluestep.global.dto.globaluserrecord.GlobalUserCredentialUpdateRequest}) carry the
 * monolith's already-encrypted stored form rather than a password, are write-only, and are never
 * echoed back. See the create request for the whole of the reasoning.</p>
 *
 * <p>The credential moves on its own verb and never rides an account edit:
 * {@link dev.bluestep.global.dto.globaluserrecord.GlobalUserRecordUpdateRequest} still has no
 * credential component, which is what stops an edit by something that had not loaded the credential
 * from blanking it. There is deliberately <em>no</em> verify operation — the stored population spans
 * two cipher generations, so comparing encoded bytes would refuse the correct password for the
 * majority of accounts; the credential update record states what was measured and what verify would
 * need.</p>
 *
 * <p>Null-marked: every type here is non-null unless explicitly annotated
 * {@link org.jspecify.annotations.Nullable}. Record components in particular are never null — a
 * component that may be absent is declared {@code Optional<T>} instead.</p>
 */
@NullMarked
package dev.bluestep.global.dto.globaluserrecord;

import org.jspecify.annotations.NullMarked;
