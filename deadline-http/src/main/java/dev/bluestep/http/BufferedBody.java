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
	 * The body read and thrown away: the status and headers are the answer.
	 *
	 * @return the body
	 */
	static BufferedBody<Void> discarding() {
		return new BufferedHandler<>(HttpResponse.BodyHandlers.discarding());
	}
}
