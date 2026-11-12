package dev.bluestep.http;

import java.net.http.HttpResponse;
import java.util.Objects;

/**
 * The one implementation of {@link BufferedBody}: package-private, so only {@code BufferedBody}'s factories make one
 * and no streaming handler can be passed to {@link DeadlineHttp}.
 *
 * @param <T>     the body's type
 * @param handler a JDK handler that reads the whole body before {@code send} returns
 */
record BufferedHandler<T>(HttpResponse.BodyHandler<T> handler) implements BufferedBody<T> {

	/** Requires the handler. */
	BufferedHandler {
		Objects.requireNonNull(handler, "handler");
	}
}
