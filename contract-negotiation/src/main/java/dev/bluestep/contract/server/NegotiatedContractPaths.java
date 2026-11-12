package dev.bluestep.contract.server;

import java.util.Objects;

/**
 * Where a service's negotiated contract lives, and what its filter does with an accepted request. A configuration
 * holder.
 *
 * @param handshakePath the handshake, matched exactly ({@code /api/contract}); starts with {@code /}, does not end with
 *                      one
 * @param stablePrefix  what every stable data path starts with ({@code /api/contract/}); starts and ends with
 *                      {@code /}
 * @param mode          rewrite onto the tagged path, or mark the request with the tag
 */
public record NegotiatedContractPaths(String handshakePath, String stablePrefix, NegotiationMode mode) {

	/** Holds the paths to their shapes. */
	public NegotiatedContractPaths {
		Objects.requireNonNull(handshakePath, "handshakePath");
		Objects.requireNonNull(stablePrefix, "stablePrefix");
		Objects.requireNonNull(mode, "mode");
		if (!handshakePath.startsWith("/") || handshakePath.endsWith("/") || handshakePath.contains("?")) {
			throw new IllegalArgumentException("A handshake path starts with '/' and does not end with one: "
				+ handshakePath);
		}
		if (!stablePrefix.startsWith("/") || !stablePrefix.endsWith("/") || stablePrefix.length() < 2
				|| stablePrefix.contains("?")) {
			throw new IllegalArgumentException("A stable prefix starts and ends with '/': " + stablePrefix);
		}
	}

	/**
	 * web-global's shape: handshake {@code <stable>} without its slash, data under {@code <stable>}, rewritten onto
	 * {@code <taggedPrefix><tag>/…}.
	 *
	 * @param handshakePath the handshake ({@code /api/contract})
	 * @param stablePrefix  the stable prefix ({@code /api/contract/})
	 * @param taggedPrefix  what precedes the tag in the tagged path ({@code /api/v})
	 * @return the paths
	 */
	public static NegotiatedContractPaths rewriting(String handshakePath, String stablePrefix, String taggedPrefix) {
		return new NegotiatedContractPaths(handshakePath, stablePrefix, new NegotiationMode.Rewrite(taggedPrefix));
	}

	/**
	 * web's harness shape: the handlers map the stable path, and the filter only marks the request with the tag.
	 *
	 * @param handshakePath the handshake ({@code /harness/contract})
	 * @param stablePrefix  the stable prefix ({@code /harness/})
	 * @return the paths
	 */
	public static NegotiatedContractPaths attributing(String handshakePath, String stablePrefix) {
		return new NegotiatedContractPaths(handshakePath, stablePrefix, new NegotiationMode.Attribute());
	}
}
