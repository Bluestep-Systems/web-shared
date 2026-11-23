package dev.bluestep.global.dto.tenantknowledgebase;

import java.util.Objects;
import java.util.UUID;

import dev.bluestep.global.dto.knowledgebase.KnowledgeBaseName;
import dev.bluestep.global.dto.knowledgebase.LiveBranch;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * One knowledge base a tenant holds (from 6.6.0), as the tenant's web deployment describes it.
 *
 * @param id         the tenant's id for the knowledge base
 * @param name       its name
 * @param title      its title, not blank, at most {@value #MAX_TITLE_LENGTH} characters
 * @param liveBranch the branch it is served from
 */
public record TenantKnowledgeBaseRow(
		UUID id,
		KnowledgeBaseName name,
		@NotBlank @Size(max = MAX_TITLE_LENGTH) String title,
		LiveBranch liveBranch) {

	/** {@code knowledge_base.title varchar(200)} in a tenant's schema. */
	public static final int MAX_TITLE_LENGTH = 200;

	/** Requires every component. */
	public TenantKnowledgeBaseRow {
		Objects.requireNonNull(id, "id");
		Objects.requireNonNull(name, "name");
		Objects.requireNonNull(title, "title");
		Objects.requireNonNull(liveBranch, "liveBranch");
	}
}
