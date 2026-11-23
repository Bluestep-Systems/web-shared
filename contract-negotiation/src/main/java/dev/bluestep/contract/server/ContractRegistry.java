package dev.bluestep.contract.server;

import java.util.List;

import dev.bluestep.contract.ContractTag;

/**
 * What a service's contract serves: the registry its {@link NegotiatedContractFilter} consults. web-global adapts its
 * {@code ContractVersion} enum; web's harness adapts its {@code HarnessContract} enum. One registry per service, so
 * the handshake and the path-style routes can never disagree about what is live.
 */
public interface ContractRegistry {

	/**
	 * The service's name in the handshake: lower-case letters, digits and {@code -}, starting with a letter
	 * ({@code web-global}, {@code web-harness}).
	 */
	String service();

	/** Every live tag, in any order. A retired tag is not live. */
	List<ContractTag> liveTags();

	/**
	 * Whether {@code tag}, a live tag, declares something on {@code segment}, the first path segment after the stable
	 * prefix. The coarse check: a finer per-operation one (web-global's interceptor) may still refuse later.
	 *
	 * @param tag     a live tag
	 * @param segment the segment, possibly empty
	 * @return whether the tag serves anything there
	 */
	boolean serves(ContractTag tag, String segment);

	/**
	 * Whether {@code tag} is live.
	 *
	 * @param tag the tag
	 * @return whether {@link #liveTags()} holds it
	 */
	default boolean isLive(ContractTag tag) {
		return liveTags().contains(tag);
	}
}
