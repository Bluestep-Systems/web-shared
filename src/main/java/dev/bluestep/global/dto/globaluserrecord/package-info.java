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
 * digest. No response record here has a credential component, and the one request that does
 * ({@link dev.bluestep.global.dto.globaluserrecord.GlobalUserRecordCreateRequest}) carries the
 * monolith's already-encrypted stored form rather than a password, is write-only, and is never
 * echoed back. See that record for the whole of the reasoning.</p>
 *
 * <p>Null-marked: every type here is non-null unless explicitly annotated
 * {@link org.jspecify.annotations.Nullable}. Record components in particular are never null — a
 * component that may be absent is declared {@code Optional<T>} instead.</p>
 */
@NullMarked
package dev.bluestep.global.dto.globaluserrecord;

import org.jspecify.annotations.NullMarked;
