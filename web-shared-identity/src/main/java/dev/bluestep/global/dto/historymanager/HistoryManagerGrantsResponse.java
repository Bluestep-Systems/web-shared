package dev.bluestep.global.dto.historymanager;

import java.util.List;

/**
 * The history-manager grants (6.4.0): {@code GET /history-managers}, the standing grants, or with
 * {@code ?includeRevoked=true} every grant ever given. Newest grant first.
 *
 * @param grants the grants; empty when there are none
 */
public record HistoryManagerGrantsResponse(List<HistoryManagerGrantResponse> grants) {

	/** Jackson binds an omitted array to null whatever the null-marking says; absent means none. */
	public HistoryManagerGrantsResponse {
		grants = grants == null ? List.of() : List.copyOf(grants);
	}
}
