package dev.bluestep.http;

import java.io.IOException;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.concurrent.TimeoutException;

/**
 * One outbound HTTP exchange bounded as a whole, body included: {@link HttpRequest#timeout} bounds a {@code send}
 * only up to the response headers, so a peer that sends headers and then stalls the body would hold the caller for
 * as long as it likes.
 *
 * <p>Never {@code HttpClient.sendAsync}: the JDK completes the future that returns through
 * {@code ForkJoinPool.commonPool()} ({@code HttpClientImpl} hops onto the {@code CompletableFuture} default executor),
 * so any task blocking the common pool (on a two-CPU host it has a single worker) stalls every such call until its
 * deadline. A test holder did exactly that to web-global's gateway on CI, 2026-11-11 (RG1flake2). The test fixture
 * {@code dev.bluestep.http.testing.CommonPoolRules} keeps {@code sendAsync} and the executor-less
 * {@code CompletableFuture} async methods out of a consumer's main code.</p>
 *
 * <p>The body is a {@link BufferedBody}, never a JDK {@code BodyHandler}: a streaming handler lets {@code send} return
 * at the headers, and the body read afterwards is outside any deadline. The type makes that a compile error.</p>
 */
public interface DeadlineHttp {

	/**
	 * Sends {@code request} on {@code client} and reads its whole body as {@code body} says, all within
	 * {@code deadline}. Past
	 * the deadline the exchange is cancelled (its thread interrupted, which aborts the JDK client's exchange), so
	 * nothing keeps running for an answer nobody waits for.
	 *
	 * @param deadline positive, and at most about 292 years (what fits in a {@code long} of nanoseconds)
	 * @throws IllegalArgumentException the deadline is zero, negative or too long; nothing was sent
	 * @throws TimeoutException     past the deadline; the exchange has been cancelled
	 * @throws BodyTooLargeException {@code body} is capped ({@code BufferedBody.ofByteArray(long)},
	 *                              {@code ofString(long)}) and the response's body is larger, declared or streamed;
	 *                              the exchange has been cancelled. An {@code IOException}, thrown as itself
	 * @throws IOException          the exchange failed (an {@code HttpTimeoutException} for the request's own timeout),
	 *                              or failed in any other way, the cause kept
	 * @throws InterruptedException the calling thread was interrupted while waiting; the exchange has been cancelled
	 */
	<T> HttpResponse<T> send(HttpClient client, HttpRequest request, BufferedBody<T> body, Duration deadline) throws IOException, InterruptedException, TimeoutException;
}
