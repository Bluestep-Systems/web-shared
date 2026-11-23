package dev.bluestep.http;

import java.net.http.HttpHeaders;
import java.net.http.HttpResponse;
import java.nio.ByteBuffer;
import java.util.List;
import java.util.Objects;
import java.util.OptionalLong;
import java.util.concurrent.Flow;
import java.util.function.Function;

/**
 * A body read whole but never past {@code maxBytes}: a declared {@code Content-Length} over the cap is refused before
 * any of the body is read, and a body that streams past it (chunked, of unknown length, or past what it declared) is
 * refused as soon as it does (see {@link CappedBodySubscriber}). Either refusal is a {@link BodyTooLargeException}.
 * The bytes are then decoded by {@code decoder}, chosen from the response (for a string, by its charset).
 *
 * <p>A 204 or 304 has no body whatever its {@code Content-Length} says, so the declared length is not checked for
 * those; the streamed bytes still are. A {@code HEAD} response's {@code Content-Length} describes the {@code GET}
 * body it did not send, and a response cannot tell this handler which method asked for it: a {@code HEAD} request
 * takes {@link BufferedBody#discarding()}.</p>
 *
 * @param <T>      the body's type
 * @param maxBytes the most body bytes accepted, from 0 to {@link #MAX_CAP}
 * @param decoder  from the response, what turns the bytes into the body
 */
record CappedBodyHandler<T>(long maxBytes, Function<HttpResponse.ResponseInfo, Function<byte[], T>> decoder)
		implements HttpResponse.BodyHandler<T> {

	/** The largest cap: the largest array the body can be joined into on every JVM. */
	static final long MAX_CAP = Integer.MAX_VALUE - 8;

	/** Requires the decoder and a cap from 0 to {@link #MAX_CAP}. */
	CappedBodyHandler {
		if (maxBytes < 0 || maxBytes > MAX_CAP) {
			throw new IllegalArgumentException("A body cap is from 0 to " + MAX_CAP + " bytes: " + maxBytes);
		}
		Objects.requireNonNull(decoder, "decoder");
	}

	@Override
	public HttpResponse.BodySubscriber<T> apply(HttpResponse.ResponseInfo responseInfo) {
		HttpHeaders headers = responseInfo.headers();
		int status = responseInfo.statusCode();
		OptionalLong declared = status == 204 || status == 304 ? OptionalLong.empty() : declaredLength(headers);
		CappedBodySubscriber bytes;
		if (declared.isEmpty() || declared.getAsLong() < 0) {
			bytes = new CappedBodySubscriber(maxBytes, -1);
		}
		else if (declared.getAsLong() > maxBytes) {
			bytes = CappedBodySubscriber.refusing(new BodyTooLargeException(maxBytes, declared.getAsLong(), true));
		}
		else {
			bytes = new CappedBodySubscriber(maxBytes, declared.getAsLong());
		}
		return HttpResponse.BodySubscribers.mapping(bytes, decoder.apply(responseInfo));
	}

	/**
	 * The {@code Content-Length}, when there is one that parses. One that does not is left to the JDK client, which
	 * refuses a malformed length itself; the streamed bytes are capped regardless.
	 */
	private static OptionalLong declaredLength(HttpHeaders headers) {
		try {
			return headers.firstValueAsLong("content-length");
		}
		catch (NumberFormatException malformed) {
			return OptionalLong.empty();
		}
	}

	/**
	 * {@code bytes} decoded exactly as {@code BodyHandlers.ofString()} decodes this response: the JDK's own subscriber,
	 * fed the capped bytes, so the charset is the one the JDK picks from the {@code Content-Type} (UTF-8 if none).
	 *
	 * @param responseInfo the response
	 * @param bytes        its body, already capped
	 * @return the text
	 */
	static String decodeAsOfString(HttpResponse.ResponseInfo responseInfo, byte[] bytes) {
		HttpResponse.BodySubscriber<String> jdk = HttpResponse.BodyHandlers.ofString().apply(responseInfo);
		jdk.onSubscribe(new Flow.Subscription() {
			@Override
			public void request(long n) {
				// Everything is delivered at once below.
			}

			@Override
			public void cancel() {
				// Nothing to stop.
			}
		});
		jdk.onNext(List.of(ByteBuffer.wrap(bytes)));
		jdk.onComplete();
		return jdk.getBody().toCompletableFuture().join();
	}
}
