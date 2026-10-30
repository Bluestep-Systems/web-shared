package dev.bluestep.global.dto.fleet;

import java.util.Optional;

import org.jspecify.annotations.Nullable;

/**
 * One Kubernetes namespace of the fleet: a row of web-global's {@code fleet_namespace}, an entry of
 * {@code GET /namespaces}.
 *
 * <p>The version is in the body, not only an {@code ETag}, because the one write a caller makes,
 * clearing {@code modified}, acts on an entry of a list: it sends this version as its {@code If-Match},
 * so a mark set after the list was read is never cleared unseen.</p>
 *
 * @param name      the namespace's name
 * @param label     its display label, absent where it has none
 * @param legacy    the monolith's {@code legacy} switch, non-zero read as on
 * @param isPrivate the namespace is private: the legacy {@code flags} bit 0 (value 1)
 * @param modified  a domain or tenant in the namespace changed since its ingress was last rebuilt: the
 *                  legacy {@code flags} bit 1 (value 2)
 * @param version   the entry's version, advanced on every change whichever side made it
 */
public record FleetNamespaceResponse(
		String name,
		@Nullable String label,
		boolean legacy,
		boolean isPrivate,
		boolean modified,
		long version) {

	/** {@link #label}, as an {@code Optional}. */
	public Optional<String> optLabel() {
		return Optional.ofNullable(label);
	}
}
