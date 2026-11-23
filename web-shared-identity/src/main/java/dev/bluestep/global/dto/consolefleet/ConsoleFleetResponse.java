package dev.bluestep.global.dto.consolefleet;

import java.time.Instant;
import java.util.List;
import java.util.Objects;

/**
 * web-global's answer to {@code GET /api/contract/console-fleet} (from 6.21.0): every {@code global.fleet_namespace}
 * row, each with what its web said when web-global asked. web-global may answer from a round a few seconds old;
 * {@link #checkedAt} says when it started.
 *
 * @param checkedAt  when this round of checks started; every namespace's {@code checkedAt} is at or after it
 * @param namespaces one entry per namespace, by name
 */
public record ConsoleFleetResponse(Instant checkedAt, List<ConsoleFleetNamespace> namespaces) {

	/** Requires the time and copies the list, so an answer cannot change once built. */
	public ConsoleFleetResponse {
		Objects.requireNonNull(checkedAt, "checkedAt");
		namespaces = List.copyOf(Objects.requireNonNull(namespaces, "namespaces"));
	}
}
