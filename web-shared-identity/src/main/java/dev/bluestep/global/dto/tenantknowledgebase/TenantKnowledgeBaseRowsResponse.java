package dev.bluestep.global.dto.tenantknowledgebase;

import java.util.List;
import java.util.Objects;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Pattern;

/**
 * web-global's answer to {@code GET /api/contract/tenant-knowledge-base-rows/{tenantId}} (from 6.6.0): the one tenant
 * asked about, what could be said, and its rows in name order. Always {@code 200} for a valid tenant id.
 *
 * @param tenant         the tenant asked about, {@code U<number>}
 * @param state          what could be said; rows only with {@link TenantKnowledgeBaseRowsState#ANSWERED}
 * <p>Rows only come with {@link TenantKnowledgeBaseRowsState#ANSWERED}: {@code ABSENT}, {@code UNMAPPED} and
 * {@code UNANSWERED} must have none, and a response that says otherwise cannot be built, nor bound from JSON.
 * {@link TenantKnowledgeBaseRowsState#UNKNOWN}, the binding of a state newer than this reader, is tolerated with
 * whatever rows it carries, <strong>but a consumer must not use them</strong>: it does not know what the state means.</p>
 *
 * @param knowledgeBases the tenant's knowledge bases, in name order; empty unless {@code ANSWERED} (and ignored for
 *                       {@code UNKNOWN})
 */
public record TenantKnowledgeBaseRowsResponse(
		@Pattern(regexp = TENANT_ID_PATTERN) String tenant,
		TenantKnowledgeBaseRowsState state,
		List<@Valid TenantKnowledgeBaseRow> knowledgeBases) {

	/** A tenant id: {@code U} and a number without leading zeros. */
	public static final String TENANT_ID_PATTERN = "^U[1-9][0-9]{0,18}$";

	/**
	 * Requires every component, copies the rows so an answer cannot change once built, and refuses rows in a state that
	 * forbids them.
	 */
	public TenantKnowledgeBaseRowsResponse {
		Objects.requireNonNull(tenant, "tenant");
		Objects.requireNonNull(state, "state");
		knowledgeBases = List.copyOf(knowledgeBases);
		if (!knowledgeBases.isEmpty() && state != TenantKnowledgeBaseRowsState.ANSWERED
				&& state != TenantKnowledgeBaseRowsState.UNKNOWN) {
			throw new IllegalArgumentException("A " + state + " answer has no knowledge bases, but carries "
					+ knowledgeBases.size());
		}
	}

	/**
	 * The tenant's web deployment answered with these rows.
	 *
	 * @param tenant the tenant
	 * @param rows   its rows, in name order, possibly none
	 * @return the answer
	 */
	public static TenantKnowledgeBaseRowsResponse answered(String tenant, List<TenantKnowledgeBaseRow> rows) {
		return new TenantKnowledgeBaseRowsResponse(tenant, TenantKnowledgeBaseRowsState.ANSWERED, rows);
	}

	/**
	 * An answer with no rows.
	 *
	 * @param tenant the tenant
	 * @param state  why there are none: any state but {@code ANSWERED}
	 * @return the answer
	 */
	public static TenantKnowledgeBaseRowsResponse withoutRows(String tenant, TenantKnowledgeBaseRowsState state) {
		return new TenantKnowledgeBaseRowsResponse(tenant, state, List.of());
	}
}
