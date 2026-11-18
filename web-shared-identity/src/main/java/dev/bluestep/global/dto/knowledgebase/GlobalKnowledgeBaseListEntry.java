package dev.bluestep.global.dto.knowledgebase;

import java.math.BigInteger;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

import org.jspecify.annotations.Nullable;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

import dev.bluestep.global.dto.tenant.TenantActor;

/**
 * One row of {@code GET /global-knowledge-bases} from 6.9.0: a {@link GlobalKnowledgeBase}'s members as that record
 * writes them, plus the row's {@link #version} and its {@link #connections}, so a caller that lists the registry need
 * not read each knowledge base and its tenants one by one.
 *
 * <p><b>An integral version, on the wire.</b> Bound through {@link #fromJson}, which takes {@code version} only as an
 * integral JSON number: a mapper would otherwise truncate {@code 7.9} to {@code 7} and {@code -0.5} to {@code 0}, and
 * a malformed row would hand its reader a valid-looking {@code If-Match}. A fraction (even {@code 7.0}), a string, or
 * a number outside {@code long} fails binding.</p>
 *
 * <p><b>Additive on the wire.</b> The first eight members are {@link GlobalKnowledgeBase}'s, named and written as it
 * writes them, so a reader that binds a row as a {@code GlobalKnowledgeBase} (with unknown members ignored) still
 * reads it. Tags before 6.9.0 keep answering plain {@code GlobalKnowledgeBase} rows.</p>
 *
 * @param id          the id
 * @param name        the name
 * @param title       the title, held to {@link GlobalKnowledgeBase}'s rule
 * @param liveBranch  the live branch
 * @param created     when it was created
 * @param modified    when it was last patched, or created
 * @param creator     who created it, absent for no actor
 * @param modifier    who last patched it, absent for no actor or when nobody has
 * @param version     the row's version: the value {@code GET /global-knowledge-bases/{id}} sends as its {@code ETag}
 *                    (written there as {@code "<version>"}), which a patch or a delete is pinned to
 * @param connections every tenant connected to it, as {@code GET /global-knowledge-bases/{id}/tenants} lists them
 *                    (by tenant key); each names this knowledge base
 */
public record GlobalKnowledgeBaseListEntry(
		GlobalKnowledgeBaseId id,
		KnowledgeBaseName name,
		String title,
		LiveBranch liveBranch,
		Instant created,
		Instant modified,
		@Nullable TenantActor creator,
		@Nullable TenantActor modifier,
		long version,
		List<KnowledgeBaseTenantConnection> connections) {

	/**
	 * Holds the parts to {@link GlobalKnowledgeBase}'s rules, a version to being non-negative, and every connection to
	 * naming this knowledge base; so a row that breaks any of them fails binding.
	 */
	public GlobalKnowledgeBaseListEntry {
		new GlobalKnowledgeBase(id, name, title, liveBranch, created, modified, creator, modifier);
		if (version < 0) {
			throw new IllegalArgumentException("A knowledge base's version is not negative: " + version);
		}
		connections = List.copyOf(Objects.requireNonNull(connections, "connections"));
		for (KnowledgeBaseTenantConnection connection : connections) {
			if (!id.equals(connection.knowledgeBase())) {
				throw new IllegalArgumentException("A listed connection names another knowledge base: "
						+ connection.knowledgeBase());
			}
		}
	}

	/** The wire's binding: every member as the record declares it, but {@code version} an integral JSON number. */
	@JsonCreator
	static GlobalKnowledgeBaseListEntry fromJson(@JsonProperty("id") GlobalKnowledgeBaseId id,
			@JsonProperty("name") KnowledgeBaseName name, @JsonProperty("title") String title,
			@JsonProperty("liveBranch") LiveBranch liveBranch, @JsonProperty("created") Instant created,
			@JsonProperty("modified") Instant modified, @JsonProperty("creator") @Nullable TenantActor creator,
			@JsonProperty("modifier") @Nullable TenantActor modifier, @JsonProperty("version") @Nullable Object version,
			@JsonProperty("connections") List<KnowledgeBaseTenantConnection> connections) {
		return new GlobalKnowledgeBaseListEntry(id, name, title, liveBranch, created, modified, creator, modifier,
				integral(version), connections);
	}

	private static long integral(@Nullable Object version) {
		if (version instanceof Integer || version instanceof Long) {
			return ((Number) version).longValue();
		}
		if (version instanceof BigInteger big && big.bitLength() < Long.SIZE) {
			return big.longValue();
		}
		throw new IllegalArgumentException("A knowledge base's version is an integral JSON number, not " + version);
	}

	/** The row from its knowledge base, version and connections. */
	public static GlobalKnowledgeBaseListEntry of(GlobalKnowledgeBase knowledgeBase, long version,
			List<KnowledgeBaseTenantConnection> connections) {
		return new GlobalKnowledgeBaseListEntry(knowledgeBase.id(), knowledgeBase.name(), knowledgeBase.title(),
				knowledgeBase.liveBranch(), knowledgeBase.created(), knowledgeBase.modified(), knowledgeBase.creator(),
				knowledgeBase.modifier(), version, connections);
	}

	/** The knowledge base itself, as every other read of it answers. */
	public GlobalKnowledgeBase knowledgeBase() {
		return new GlobalKnowledgeBase(id, name, title, liveBranch, created, modified, creator, modifier);
	}

	/** {@link #creator}, as an {@code Optional}. */
	public Optional<TenantActor> optCreator() {
		return Optional.ofNullable(creator);
	}

	/** {@link #modifier}, as an {@code Optional}. */
	public Optional<TenantActor> optModifier() {
		return Optional.ofNullable(modifier);
	}
}
