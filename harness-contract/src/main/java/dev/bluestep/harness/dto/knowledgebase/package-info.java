/**
 * The harness's knowledge-base shapes (1.0.0): a tenant's {@code knowledge_base} rows as web stores them, for one
 * tenant web-global named.
 *
 * <ul>
 *   <li>{@link dev.bluestep.harness.dto.knowledgebase.HarnessTenantKnowledgeBaseRows} is the answer to
 *       {@code GET /harness/tenant-knowledge-base-rows/{tenantId}}: the tenant, a
 *       {@link dev.bluestep.harness.dto.knowledgebase.HarnessRowsState}, and its rows.</li>
 *   <li>{@link dev.bluestep.harness.dto.knowledgebase.HarnessKnowledgeBaseRow} is one row: web's uuid, name, title and
 *       live branch. No other id travels: the tenant is the one asked about.</li>
 * </ul>
 *
 * <h2>Absence is a state, never a 404</h2>
 *
 * <p>A schema this namespace does not hold answers {@code 200} {@code ABSENT}, so a {@code 404} only ever means "not
 * served here" (another port, an older web, a refused tag) and a client never has to tell a missing row from a missing
 * route.</p>
 *
 * <h2>Null-marked</h2>
 *
 * <p>Every type in this package is non-null unless annotated {@link org.jspecify.annotations.Nullable}; nothing here
 * is.</p>
 */
@NullMarked
package dev.bluestep.harness.dto.knowledgebase;

import org.jspecify.annotations.NullMarked;
