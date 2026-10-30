package dev.bluestep.global.dto.reseller;

import java.time.LocalDateTime;
import java.util.Optional;

import org.jspecify.annotations.Nullable;

import dev.bluestep.global.dto.basetable.BaseTableKey;
import dev.bluestep.global.dto.tenantaccess.ResellerKey;

/**
 * A reseller as the monolith holds it: the branding columns, plus what the monolith's generic object
 * model asks of any object — its name, creator and creation stamp.
 *
 * <h2>Where those come from</h2>
 *
 * <p>Until web-global's changeset 017 they lived on the reseller's {@code global.basetable} row, beside
 * a {@code global.reseller} table that had no name column at all. From 017 on, {@code global.reseller}
 * is a table web-global owns outright and holds all of them itself; the old table is kept, unwritten,
 * as {@code global.reseller_archive}, and the old {@code basetable} rows are left as they were. The
 * shape below did not change with the storage, so its optional components stay optional: a
 * service from before 017 can still answer one empty, though one from 017 on never does.</p>
 *
 * <h2>Why this exists beside {@link ResellerResponse}</h2>
 *
 * <p>{@code ResellerResponse} carries the twelve branding columns and nothing else. The monolith
 * renders and sorts resellers by name, so a response without the display name cannot back a reseller
 * picker, which is what six of the monolith's screens use this data for.</p>
 *
 * <p>A separate record rather than a component added to {@code ResellerResponse}: that record is what
 * the already-released 4.0.0 and 4.1.0 contracts serve, and reshaping it would mean freezing snapshots
 * of it into both of their folders to keep the promise each of them made.</p>
 *
 * <h2>The rest of the object model</h2>
 *
 * <p>The display name is not the only thing the monolith's generic loader read. It populated
 * the version, the creator, the creation and last-modified stamps and the row lock on every read, and
 * every one of those is on the general {@code RemoteObject} interface — so a caller that reaches a
 * reseller generically (a script, a GraphQL field, a debug dump) can ask for any of them. Serving only
 * the name would answer null to all of it, silently.</p>
 *
 * <p><b>Two of them are still absent, on the same principle.</b> The last-modified pair would need the
 * identity of whoever is writing, and this contract authenticates a service rather than a person, so
 * nothing here could ever advance it — a value frozen at whatever wrote the row before the monolith
 * stopped writing it directly looks current and is not. The row lock is absent for the harder version
 * of the same reason: the monolith's guard was two halves, a {@code checkWriteLock} against the acting
 * subject and a pin in the SQL, and only the pin can be reproduced without an actor. A lock component
 * carrying the pin alone would be worse than none — the caller would present a value it read from this
 * very response, so anyone who read first could write over anyone's lock. Reseller row locks therefore
 * go unenforced on this path until the actor question is settled.</p>
 *
 * <p>Creation has neither problem: it never changes, so serving it cannot mislead.</p>
 *
 * <p>The version travels in the {@code ETag} rather than here, because it is what a write is pinned
 * to and belongs with the transport's own concurrency mechanism.</p>
 *
 * <h2>Why {@code flags} is a bare number</h2>
 *
 * <p>It is a bit set, and it is served as the {@code bigint} the column holds rather than as the
 * booleans the monolith reads out of it. That is the whole point of the column: a reseller-level
 * switch added later costs a bit position and nothing on this contract, where a named boolean
 * component per switch would cost a shape change — and therefore a release — every time. This
 * service does not interpret any of the bits and has no opinion about what they mean; it stores and
 * returns the word, the same way it stores and returns a branding URL it never fetches.</p>
 *
 * <p>Zero is the meaningful default rather than an absence, which is why this component is a
 * {@code long} where every optional column beside it is {@code @Nullable}. The column is
 * {@code NOT NULL DEFAULT 0}, so every reseller has a value; a reseller that has never been edited
 * has all bits clear, which is what makes "no bits set" the behaviour a new bit must be defined
 * against.</p>
 *
 * <p>Timestamps are {@code LocalDateTime} rather than {@code Instant}: the columns are
 * {@code timestamp without time zone} and carry a wall-clock reading with no zone, so presenting them
 * as instants would invent an offset nobody stored.</p>
 *
 * @param reseller       the key, in the spelling a classification uses
 * @param displayName    the name the monolith shows. Empty only from a service before changeset 017,
 *                       when the name came from a nullable {@code basetable} column; a reseller
 *                       without one sorts last rather than being hidden.
 * @param supportEmail   where this reseller's users are told to write
 * @param defaultDomain  the host its tenants front by default
 * @param privacyPageUrl the privacy policy it renders
 * @param termsPageUrl   the terms it renders
 * @param icons          its icon set
 * @param flags          its switch word, verbatim. Zero for a reseller with none set.
 * @param creator        who created the reseller. Empty only from a service before changeset 017, and
 *                       there exactly when the reseller had no {@code basetable} row.
 * @param created        when it was created, empty on the same terms as {@code creator}
 */
public record ResellerRecordResponse(
		ResellerKey reseller,
		@Nullable String displayName,
		@Nullable String supportEmail,
		@Nullable String defaultDomain,
		@Nullable String privacyPageUrl,
		@Nullable String termsPageUrl,
		ResellerIcons icons,
		long flags,
		@Nullable BaseTableKey creator,
		@Nullable LocalDateTime created
) {

	/** {@link #displayName}, as an {@code Optional}. */
	public Optional<String> optDisplayName() {
		return Optional.ofNullable(displayName);
	}

	/** {@link #supportEmail}, as an {@code Optional}. */
	public Optional<String> optSupportEmail() {
		return Optional.ofNullable(supportEmail);
	}

	/** {@link #defaultDomain}, as an {@code Optional}. */
	public Optional<String> optDefaultDomain() {
		return Optional.ofNullable(defaultDomain);
	}

	/** {@link #privacyPageUrl}, as an {@code Optional}. */
	public Optional<String> optPrivacyPageUrl() {
		return Optional.ofNullable(privacyPageUrl);
	}

	/** {@link #termsPageUrl}, as an {@code Optional}. */
	public Optional<String> optTermsPageUrl() {
		return Optional.ofNullable(termsPageUrl);
	}

	/** {@link #creator}, as an {@code Optional}. */
	public Optional<BaseTableKey> optCreator() {
		return Optional.ofNullable(creator);
	}

	/** {@link #created}, as an {@code Optional}. */
	public Optional<LocalDateTime> optCreated() {
		return Optional.ofNullable(created);
	}
}
