package dev.bluestep.harness.dto.knowledgebase;

import java.util.Objects;
import java.util.UUID;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * One row of a tenant's {@code knowledge_base} table, as web stores it. The constraints are the column widths and
 * web's own rules for names and branches ({@code myassn.knowledgebase}), so a row web wrote is valid here; they are
 * copied, not shared, so this line stays independent of web-shared's.
 *
 * @param id         web's id for the knowledge base (a random v4 uuid)
 * @param name       the knowledge base's name: lower-case letters, digits and {@code -}, starting and ending with a
 *                   letter or digit, at most {@value #MAX_NAME_LENGTH} characters
 * @param title      its title, at most {@value #MAX_TITLE_LENGTH} characters, not blank
 * @param liveBranch the branch web serves it from: letters, digits, {@code .}, {@code _}, {@code -} and {@code /},
 *                   starting with a letter or digit, at most {@value #MAX_BRANCH_LENGTH} characters
 */
public record HarnessKnowledgeBaseRow(
		@NotNull UUID id,
		@NotNull @Size(max = MAX_NAME_LENGTH) @Pattern(regexp = NAME_PATTERN) String name,
		@NotBlank @Size(max = MAX_TITLE_LENGTH) String title,
		@NotNull @Size(max = MAX_BRANCH_LENGTH) @Pattern(regexp = BRANCH_PATTERN) String liveBranch) {

	/** {@code knowledge_base.name varchar(64)}. */
	public static final int MAX_NAME_LENGTH = 64;
	/** {@code knowledge_base.title varchar(200)}. */
	public static final int MAX_TITLE_LENGTH = 200;
	/** {@code knowledge_base.live_branch varchar(200)}. */
	public static final int MAX_BRANCH_LENGTH = 200;

	/** web's name rule, over the whole value. */
	public static final String NAME_PATTERN = "^[a-z0-9](?:[a-z0-9-]{0,62}[a-z0-9])?$";
	/** web's branch characters, over the whole value. */
	public static final String BRANCH_PATTERN = "^[A-Za-z0-9][A-Za-z0-9._/-]{0,199}$";

	/** Requires every component; the constraints are Bean Validation's to check. */
	public HarnessKnowledgeBaseRow {
		Objects.requireNonNull(id, "id");
		Objects.requireNonNull(name, "name");
		Objects.requireNonNull(title, "title");
		Objects.requireNonNull(liveBranch, "liveBranch");
	}
}
