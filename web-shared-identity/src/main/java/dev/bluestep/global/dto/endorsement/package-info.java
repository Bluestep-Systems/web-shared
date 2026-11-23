/**
 * The platform endorsements the monolith's code knows (from 6.16.0): {@link PlatformEndorsement}, one constant per
 * constant of web's {@code myassn.security.Endorsement}, each with the label, group, description and implying
 * endorsements the Console shows it with, and {@link EndorsementGroup}, the four groups.
 *
 * <p>An endorsement is stored on its own, as free text inside the account's {@code attribs} cipher; a stored id this
 * catalog does not hold matches no gate in the monolith. The catalog is what the code knows, not what can be stored.</p>
 *
 * <p>Null-marked: every type in this package is non-null.</p>
 */
@NullMarked
package dev.bluestep.global.dto.endorsement;

import org.jspecify.annotations.NullMarked;
