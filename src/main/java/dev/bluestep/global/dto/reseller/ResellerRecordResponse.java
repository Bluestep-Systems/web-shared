package dev.bluestep.global.dto.reseller;

import java.time.LocalDateTime;
import java.util.Optional;

import dev.bluestep.global.dto.basetable.BaseTableKey;
import dev.bluestep.global.dto.tenantaccess.ResellerKey;

/**
 * A reseller as the monolith holds it: the branding columns, plus everything its
 * {@code global.basetable} row carries about the object itself.
 *
 * <h2>Why this exists beside {@link ResellerResponse}</h2>
 *
 * <p>{@code ResellerResponse} carries the twelve columns of {@code global.reseller} and nothing else,
 * because that is all that table has: it has no name column at all. The monolith nevertheless renders
 * and sorts resellers by name, and it gets that name from the row's {@code global.basetable} entry —
 * which is why its own listing joins {@code basetable} rather than reading {@code reseller} alone. A
 * response without the display name therefore cannot back a reseller picker, which is what six of the
 * monolith's screens use this data for.</p>
 *
 * <p>A separate record rather than a component added to {@code ResellerResponse}: that record is what
 * the already-released 4.0.0 and 4.1.0 contracts serve, and reshaping it would mean freezing snapshots
 * of it into both of their folders to keep the promise each of them made.</p>
 *
 * <h2>The rest of the basetable row</h2>
 *
 * <p>The display name is not the only thing the monolith's own join read. Its generic loader populated
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
 * <p>Timestamps are {@code LocalDateTime} rather than {@code Instant}: the columns are
 * {@code timestamp without time zone} and carry a wall-clock reading with no zone, so presenting them
 * as instants would invent an offset nobody stored.</p>
 *
 * @param reseller       the key, in the spelling a classification uses
 * @param displayName    the name the monolith shows, from the joined {@code basetable} row. Empty
 *                       when that row carries none — the column is nullable, and a reseller without
 *                       one sorts last rather than being hidden.
 * @param supportEmail   where this reseller's users are told to write
 * @param defaultDomain  the host its tenants front by default
 * @param privacyPageUrl the privacy policy it renders
 * @param termsPageUrl   the terms it renders
 * @param icons          its icon set
 * @param creator        who created the row. Empty exactly when there is no {@code basetable} row at
 *                       all — the reseller row can outlive it, and such a reseller is readable but
 *                       not writable.
 * @param created        when it was created, empty on the same terms as {@code creator}
 */
public record ResellerRecordResponse(
		ResellerKey reseller,
		Optional<String> displayName,
		Optional<String> supportEmail,
		Optional<String> defaultDomain,
		Optional<String> privacyPageUrl,
		Optional<String> termsPageUrl,
		ResellerIcons icons,
		Optional<BaseTableKey> creator,
		Optional<LocalDateTime> created
) {
}
