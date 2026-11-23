/**
 * Wire shapes for the knowledge bases a selected tenant holds (from 6.6.0): web-global's answer to
 * {@code GET /api/contract/tenant-knowledge-base-rows/{tenantId}}, which it builds from what the tenant's own web
 * deployment reports. One tenant per answer, never a batch.
 *
 * <ul>
 *   <li>{@link TenantKnowledgeBaseRowsResponse} is the answer: the tenant, a {@link TenantKnowledgeBaseRowsState} and
 *       the rows.</li>
 *   <li>{@link TenantKnowledgeBaseRow} is one knowledge base of the tenant.</li>
 * </ul>
 *
 * <p>These are deliberately separate from the harness contract's records (web-global to web) and reuse this
 * line's {@link dev.bluestep.global.dto.knowledgebase.KnowledgeBaseName} and
 * {@link dev.bluestep.global.dto.knowledgebase.LiveBranch}, so the two contract lines stay independent.</p>
 *
 * <h2>Null-marked</h2>
 *
 * <p>Every type in this package is non-null.</p>
 */
@NullMarked
package dev.bluestep.global.dto.tenantknowledgebase;

import org.jspecify.annotations.NullMarked;
