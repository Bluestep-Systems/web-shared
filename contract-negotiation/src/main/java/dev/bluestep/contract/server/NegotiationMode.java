package dev.bluestep.contract.server;

import java.util.Objects;

/** What {@link NegotiatedContractFilter} does with a request it accepted. */
public sealed interface NegotiationMode {

	/**
	 * Rewrite the request onto the tagged path, {@code <taggedPrefix><tag>/<segment>/…}, so that an existing
	 * path-versioned registry and its controllers serve it unchanged (web-global: {@code /api/contract/x/…} with
	 * {@code B6P-Contract: 6.6.0} is served as {@code /api/v6.6.0/x/…}).
	 *
	 * <p>Assumes the dispatcher servlet is mapped to {@code /} (Spring Boot's default): the rewritten request's servlet
	 * path is the whole rewritten path and its path info is null.</p>
	 *
	 * @param taggedPrefix what precedes the tag in the tagged path, such as {@code /api/v}; starts with {@code /}
	 */
	record Rewrite(String taggedPrefix) implements NegotiationMode {

		/** Holds the prefix to the shape a path needs. */
		public Rewrite {
			Objects.requireNonNull(taggedPrefix, "taggedPrefix");
			if (!taggedPrefix.startsWith("/") || taggedPrefix.endsWith("/") || taggedPrefix.contains("?")) {
				throw new IllegalArgumentException("A tagged prefix starts with '/' and does not end with one: "
					+ taggedPrefix);
			}
		}
	}

	/**
	 * Leave the path alone: the handlers map the stable path themselves and read the tag from
	 * {@link dev.bluestep.contract.ContractHeaders#NEGOTIATED_ATTRIBUTE} (web's harness, which has no path-style form).
	 */
	record Attribute() implements NegotiationMode {
	}
}
