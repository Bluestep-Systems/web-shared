package dev.bluestep.contract;

import java.util.Optional;

import org.jspecify.annotations.Nullable;

/**
 * The values of {@value ContractHeaders#REFUSAL}: a stable, greppable reason, where the message (which names the tag
 * asked for) is not. The first three are web-global's tokens from before negotiation, unchanged.
 */
public enum ContractRefusalToken {

	/** The tag is unknown, malformed in a path, or retired: {@code 404}. */
	UNKNOWN_VERSION("unknown-version"),

	/** A live tag, addressed to a segment or surface it does not declare: {@code 404}. */
	SURFACE_NOT_SERVED("surface-not-served"),

	/** A live tag that serves the surface, reaching an operation gated to a later one: {@code 405}. */
	OPERATION_ARRIVED_LATER("operation-arrived-later"),

	/**
	 * A stable-path request whose {@value ContractHeaders#CONTRACT} header is missing, repeated or not a tag:
	 * {@code 400}. A client bug, never a rollout state, so a client does not re-negotiate on it.
	 */
	CONTRACT_HEADER_INVALID("contract-header-invalid");

	private final String token;

	ContractRefusalToken(String token) {
		this.token = token;
	}

	/** The value carried in {@value ContractHeaders#REFUSAL}. */
	public String token() {
		return token;
	}

	/**
	 * Whether this refusal means a peer mid-rollout (the handshake reached a newer pod than the call): the two
	 * {@code 404}s. A negotiating client excludes the tag for that peer, re-handshakes and retries once on these, and
	 * on nothing else.
	 */
	public boolean isMismatch() {
		return this == UNKNOWN_VERSION || this == SURFACE_NOT_SERVED;
	}

	/**
	 * The refusal a header value names.
	 *
	 * @param token the header's value, or null when there was none
	 * @return the refusal, or empty for no header or a token this version does not know
	 */
	public static Optional<ContractRefusalToken> fromToken(@Nullable String token) {
		if (token == null) {
			return Optional.empty();
		}
		String stripped = token.strip();
		for (ContractRefusalToken refusal : values()) {
			if (refusal.token.equals(stripped)) {
				return Optional.of(refusal);
			}
		}
		return Optional.empty();
	}
}
