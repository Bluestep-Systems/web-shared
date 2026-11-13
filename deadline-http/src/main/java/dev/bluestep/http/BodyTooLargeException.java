package dev.bluestep.http;

import java.io.IOException;

/**
 * A capped {@link BufferedBody} ({@code ofByteArray(long)}, {@code ofString(long)}) met a response body larger than its
 * cap: either the response declared a {@code Content-Length} over the cap, refused before any of the body was read,
 * or the bytes streamed so far passed it, refused the moment they did. Either way the exchange has been cancelled
 * (the JDK client closes its connection), and no more than the cap was ever held.
 *
 * <p>An {@link IOException}, the type {@link DeadlineHttp#send} fails every exchange with, so a caller that treats
 * any failure as "the peer is unreachable" needs no change. A caller that wants to tell an oversized answer apart (a
 * peer that is reachable but misbehaving) catches this type first: {@code send} throws it as itself, never wrapped.</p>
 */
public final class BodyTooLargeException extends IOException {

	private static final long serialVersionUID = 1L;

	/** The cap. */
	private final long maxBytes;
	/** The declared Content-Length, or the bytes received when the cap was passed. */
	private final long size;
	/** Whether the Content-Length was refused, as opposed to the streamed bytes. */
	private final boolean declared;

	/**
	 * @param maxBytes the cap the body passed
	 * @param size     the declared {@code Content-Length} when {@code declared}; otherwise the bytes received up to
	 *                 and including the chunk that passed the cap
	 * @param declared whether the response's {@code Content-Length} was refused before any of the body was read
	 */
	public BodyTooLargeException(long maxBytes, long size, boolean declared) {
		super(declared
			? "The response declares a body of " + size + " bytes, over the cap of " + maxBytes
			: "The response body passed the cap of " + maxBytes + " bytes (" + size + " bytes received)");
		this.maxBytes = maxBytes;
		this.size = size;
		this.declared = declared;
	}

	/**
	 * @return the cap the body passed
	 */
	public long maxBytes() {
		return maxBytes;
	}

	/**
	 * @return the declared {@code Content-Length} when {@link #declared()}; otherwise the bytes received up to and
	 *         including the chunk that passed the cap (at least {@code maxBytes() + 1}; the rest was never read)
	 */
	public long size() {
		return size;
	}

	/**
	 * @return true when the response's {@code Content-Length} was over the cap and none of the body was read; false
	 *         when the streamed bytes passed it (a chunked body, one of unknown length, or one that ran past what it
	 *         declared)
	 */
	public boolean declared() {
		return declared;
	}
}
