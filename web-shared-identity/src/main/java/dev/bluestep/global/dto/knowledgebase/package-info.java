/**
 * Wire shapes for global knowledge bases (from 6.1.0): the knowledge bases web-global holds for the
 * whole fleet, and which tenants each one is connected to.
 *
 * <ul>
 *   <li>{@link GlobalKnowledgeBase} is one knowledge base: its {@link GlobalKnowledgeBaseId}, its
 *       {@link KnowledgeBaseName}, its title and its {@link LiveBranch}.
 *       {@link GlobalKnowledgeBaseCreateRequest} creates one and {@link GlobalKnowledgeBasePatchRequest}
 *       changes its title or live branch.</li>
 *   <li>{@link KnowledgeBaseTenantConnection} is one tenant connected to one knowledge base;
 *       {@link KnowledgeBaseConnectRequest} makes one. {@link KnowledgeBaseActorRequest} names who
 *       deletes a knowledge base or disconnects a tenant (from 6.8.0).</li>
 *   <li>{@link GlobalKnowledgeBaseListEntry} is one row of the list from 6.9.0: a knowledge base with its version
 *       and its connections.</li>
 *   <li>A write that something stands in the way of answers {@code 409} with a
 *       {@link GlobalKnowledgeBaseWriteRefusal}.</li>
 * </ul>
 *
 * <h2>web-global holds the registry and web holds the content</h2>
 *
 * <p>A knowledge base's content is a git repo, and none of it is in these shapes. web-global stores
 * which knowledge bases exist and which tenants may read each one. The monolith reads the repo from
 * the forge and serves it. A global knowledge base's repo is {@value KnowledgeBaseName#GLOBAL_REPO_OWNER}
 * {@code /kb-<name>} in the fleet's forge ({@link KnowledgeBaseName#globalRepoPath()}). It is derived
 * from the name and never stored. web-global's registry does not create, check or delete it. From 6.8.0
 * web-global's gateway does: it creates the repo and protects its live branch before it registers the
 * knowledge base, and archives the repo before it deletes the registration.</p>
 *
 * <h2>One rule for names and branches</h2>
 *
 * <p>{@link KnowledgeBaseName} and {@link LiveBranch} use the same rules as the monolith's tenant
 * knowledge bases ({@code myassn.knowledgebase}), so a name or branch is valid in both places or in
 * neither. Both refuse an invalid value when built, so one cannot be bound from JSON or read back from
 * a row. web-global's table checks the same rules.</p>
 *
 * <h2>Null-marked</h2>
 *
 * <p>Every type in this package is non-null unless annotated {@link org.jspecify.annotations.Nullable}.
 * A record component that may be absent is a {@code @Nullable} value, and the record declares an
 * {@code optX()} accessor that wraps it; outside the record, read the accessor. An absent key and an
 * explicit JSON {@code null} both bind as absent.</p>
 */
@NullMarked
package dev.bluestep.global.dto.knowledgebase;

import org.jspecify.annotations.NullMarked;
