package dev.bluestep.harness.dto.knowledgebase;

import java.util.List;
import java.util.Objects;

import com.fasterxml.jackson.annotation.JsonIgnore;

import dev.bluestep.harness.HarnessPaths;
import jakarta.validation.Valid;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;

/**
 * web's answer to {@code GET /harness/tenant-knowledge-base-rows/{tenantId}} (1.0.0): the one tenant asked about, what
 * web found, and that tenant's rows in name order. Always {@code 200} for a valid id; {@code Cache-Control: no-store}.
 *
 * <p>One tenant per call, by design: web-global only ever asks about a tenant a caller explicitly selected (ruling J2),
 * and a shape that cannot name two tenants makes "no mass enumeration" structural.</p>
 *
 * @param tenant         the tenant asked about, {@code U<number>}
 * @param state          what web found; {@link HarnessRowsState#UNKNOWN} only as a reader's binding of a newer state
 * @param knowledgeBases its rows, in name order; empty unless {@link HarnessRowsState#FOUND}
 */
public record HarnessTenantKnowledgeBaseRows(
		@NotNull @Pattern(regexp = HarnessPaths.TENANT_ID_PATTERN) String tenant,
		@NotNull HarnessRowsState state,
		@NotNull List<@Valid @NotNull HarnessKnowledgeBaseRow> knowledgeBases) {

	/** Requires every component, and copies the rows so an answer cannot change once built. */
	public HarnessTenantKnowledgeBaseRows {
		Objects.requireNonNull(tenant, "tenant");
		Objects.requireNonNull(state, "state");
		knowledgeBases = List.copyOf(knowledgeBases);
	}

	/**
	 * The tenant's schema is here, with these rows.
	 *
	 * @param tenant the tenant
	 * @param rows   its rows, in name order, possibly none
	 * @return the answer
	 */
	public static HarnessTenantKnowledgeBaseRows found(String tenant, List<HarnessKnowledgeBaseRow> rows) {
		return new HarnessTenantKnowledgeBaseRows(tenant, HarnessRowsState.FOUND, rows);
	}

	/**
	 * No such schema in this namespace.
	 *
	 * @param tenant the tenant
	 * @return the answer
	 */
	public static HarnessTenantKnowledgeBaseRows absent(String tenant) {
		return new HarnessTenantKnowledgeBaseRows(tenant, HarnessRowsState.ABSENT, List.of());
	}

	/**
	 * Rows only with {@link HarnessRowsState#FOUND}; an {@code ABSENT} tenant has none. ({@code UNKNOWN} is a reader's
	 * binding and is not held to anything.)
	 *
	 * <p>{@code @JsonIgnore}: an {@code @AssertTrue} method is a JavaBeans getter, and is no wire property.</p>
	 *
	 * @return whether the rows agree with the state
	 */
	@JsonIgnore
	@AssertTrue(message = "an ABSENT tenant has no knowledge bases")
	public boolean isRowsConsistentWithState() {
		return state != HarnessRowsState.ABSENT || knowledgeBases.isEmpty();
	}
}
