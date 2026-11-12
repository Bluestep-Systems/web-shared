package dev.bluestep.contract.client;

/**
 * One call to a peer, made with whatever the negotiator decided: the consumer's own request, mapped onto its own
 * answer type. For {@link Negotiation.Tagged} it sends the stable path with the header, for
 * {@link Negotiation.PathStyle} the old tagged path, and for {@link Negotiation.NoCommonTag} or
 * {@link Negotiation.Unavailable} it sends nothing and answers what it answers when a peer cannot be used.
 *
 * <p>Failures are part of the consumer's answer type ({@code NamespaceAnswer.Unreachable}, an empty optional), not
 * exceptions; only an interrupt propagates.</p>
 *
 * @param <T> the caller's answer type
 */
@FunctionalInterface
public interface ContractExchange<T> {

	/**
	 * Makes the call.
	 *
	 * @param negotiation what to send
	 * @return the caller's value, marked {@link Attempt.Refused} when the peer's {@code X-Contract-Refusal} said so
	 * @throws InterruptedException the calling thread was interrupted
	 */
	Attempt<T> attempt(Negotiation negotiation) throws InterruptedException;
}
