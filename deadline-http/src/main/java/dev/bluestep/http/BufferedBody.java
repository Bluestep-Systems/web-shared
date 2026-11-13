package dev.bluestep.http;

import java.net.http.HttpResponse;
import java.nio.charset.Charset;
import java.util.Objects;

/**
 * A response body read completely before {@link DeadlineHttp#send} returns, so the deadline covers it. The only
 * bodies {@code DeadlineHttp} takes: a streaming handler ({@code BodyHandlers.ofInputStream()}, {@code ofLines()},
 * {@code ofPublisher()}) lets {@code HttpClient.send} return once the headers arrive, and the body read afterwards
 * could stall for as long as the peer likes, past any deadline. This type cannot wrap one: it is sealed, and its only
 * values come from the factories below.
 *
 * <p>The uncapped factories ({@link #ofString()}, {@link #ofByteArray()}) hold whatever the peer sends within the
 * deadline. A peer that is not trusted to bound its answer is read with a capped one ({@link #ofByteArray(long)},
 * {@link #ofString(long)}), which fails with {@link BodyTooLargeException} past the cap.</p>
 *
 * @param <T> the body's type
 */
public sealed interface BufferedBody<T> permits BufferedHandler {

	/** The JDK handler that reads it, for {@link ExecutorDeadlineHttp}. */
	HttpResponse.BodyHandler<T> handler();

	/**
	 * The body as text, in the charset the response names (UTF-8 if none).
	 *
	 * @return the body
	 */
	static BufferedBody<String> ofString() {
		return new BufferedHandler<>(HttpResponse.BodyHandlers.ofString());
	}

	/**
	 * The body as text in {@code charset}.
	 *
	 * @param charset the charset
	 * @return the body
	 */
	static BufferedBody<String> ofString(Charset charset) {
		return new BufferedHandler<>(HttpResponse.BodyHandlers.ofString(Objects.requireNonNull(charset, "charset")));
	}

	/**
	 * The body as bytes.
	 *
	 * @return the body
	 */
	static BufferedBody<byte[]> ofByteArray() {
		return new BufferedHandler<>(HttpResponse.BodyHandlers.ofByteArray());
	}

	/**
	 * The body as bytes, at most {@code maxBytes} of them: what to read a peer's answer with when the peer is not
	 * trusted to bound it. The deadline still applies. Over the cap, {@link DeadlineHttp#send} fails with
	 * {@link BodyTooLargeException}, the exchange cancelled:
	 * <ul>
	 * <li>a declared {@code Content-Length} over the cap is refused before any of the body is read (except on a 204
	 * or 304, which have no body; a {@code HEAD} request takes {@link #discarding()}, since its length describes a
	 * body it does not send);</li>
	 * <li>a body that streams past the cap (chunked, of unknown length) is refused as soon as it does, the chunk that
	 * passed it never copied, so no more than the cap is held while reading, however the peer splits its chunks.</li>
	 * </ul>
	 * A body with a declared length is read straight into one array of that length and held once. A body of unknown
	 * length is read into fixed-size blocks and joined into one array at the end, so for that moment it is held twice:
	 * size the cap with that in mind.
	 *
	 * @param maxBytes the most body bytes accepted, from 0 (only an empty body) to {@code Integer.MAX_VALUE - 8}
	 * @return the body
	 * @throws IllegalArgumentException the cap is negative or larger than an array can hold
	 * @since 1.1.0
	 */
	static BufferedBody<byte[]> ofByteArray(long maxBytes) {
		return new BufferedHandler<>(new CappedBodyHandler<>(maxBytes, response -> bytes -> bytes));
	}

	/**
	 * The body as text in the charset the response names (UTF-8 if none), capped at {@code maxBytes} bytes of the
	 * body as sent, not characters: {@link #ofByteArray(long)}'s cap and refusals, then decoded as
	 * {@link #ofString()} decodes.
	 *
	 * @param maxBytes the most body bytes accepted, from 0 to {@code Integer.MAX_VALUE - 8}
	 * @return the body
	 * @throws IllegalArgumentException the cap is negative or larger than an array can hold
	 * @since 1.1.0
	 */
	static BufferedBody<String> ofString(long maxBytes) {
		return new BufferedHandler<>(new CappedBodyHandler<>(maxBytes,
			response -> bytes -> CappedBodyHandler.decodeAsOfString(response, bytes)));
	}

	/**
	 * The body as text in {@code charset}, capped at {@code maxBytes} bytes of the body as sent:
	 * {@link #ofByteArray(long)}'s cap and refusals.
	 *
	 * @param charset  the charset
	 * @param maxBytes the most body bytes accepted, from 0 to {@code Integer.MAX_VALUE - 8}
	 * @return the body
	 * @throws IllegalArgumentException the cap is negative or larger than an array can hold
	 * @since 1.1.0
	 */
	static BufferedBody<String> ofString(Charset charset, long maxBytes) {
		Objects.requireNonNull(charset, "charset");
		return new BufferedHandler<>(new CappedBodyHandler<>(maxBytes, response -> bytes -> new String(bytes, charset)));
	}

	/**
	 * The body read and thrown away: the status and headers are the answer.
	 *
	 * @return the body
	 */
	static BufferedBody<Void> discarding() {
		return new BufferedHandler<>(HttpResponse.BodyHandlers.discarding());
	}
}
