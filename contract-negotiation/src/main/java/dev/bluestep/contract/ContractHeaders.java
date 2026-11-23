package dev.bluestep.contract;

/**
 * The protocol's header and attribute names, spelled once for every service: before this library web-global, the
 * monolith and the gateway each declared their own copy of {@value #REFUSAL}. A holder of constants.
 */
public final class ContractHeaders {

	/**
	 * The negotiated tag: sent by a client on a stable-path request, and echoed by the server on the answer, naming
	 * the tag that served it (for the client's logs).
	 */
	public static final String CONTRACT = "B6P-Contract";

	/**
	 * Why the contract machinery refused a request: one of {@link ContractRefusalToken}'s tokens. A client requires
	 * an empty body <b>and</b> no such header before it reads a {@code 404} as "no such row".
	 */
	public static final String REFUSAL = "X-Contract-Refusal";

	/**
	 * The request attribute a server's negotiation filter sets on an accepted stable-path request: the negotiated tag
	 * as a string. A handler (or a later filter) that needs the tag reads it here.
	 */
	public static final String NEGOTIATED_ATTRIBUTE = "B6P-Contract.negotiated";

	private ContractHeaders() {
	}
}
