package dev.bluestep.http;

import java.io.IOException;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Objects;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/**
 * {@link DeadlineHttp} as a blocking {@code HttpClient.send} on an executor of its own, waited for with the deadline.
 * A blocking {@code send} never touches the common pool (only {@code sendAsync} hops onto it), and a cancelled one is
 * interrupted, which the JDK client turns into cancelling the exchange.
 *
 * <p>The executor is one virtual thread per exchange, named by the consumer's prefix and a number
 * ({@code gateway-http-0}, {@code gateway-http-1}, …; virtual threads are always daemon threads). Virtual, not a
 * platform pool: each exchange's thread spends its life parked waiting for the peer, which is what virtual threads are
 * for, and they are scheduled on the JVM's own virtual-thread scheduler, not the common pool. Their number is bounded
 * by the callers, not by a pool size that could reject or queue: every exchange is held by a caller thread (a servlet
 * request, a scheduled job) that waits for it at most its deadline and then cancels it, so at most one exchange per
 * waiting caller is ever running. Closing it (a Spring bean's destroy method, on context close) interrupts whatever is
 * still running and refuses new exchanges.</p>
 *
 * <p>One instance per application is the intent: expose it as a {@link DeadlineHttp} bean, closed on shutdown.</p>
 */
public final class ExecutorDeadlineHttp implements DeadlineHttp, AutoCloseable {

	private final ExecutorService executor;

	/**
	 * An executor of one virtual thread per exchange, each named {@code threadPrefix} followed by a number.
	 *
	 * @param threadPrefix what each exchange's thread name starts with, such as {@code "gateway-http-"}; not blank, so
	 *                     a thread dump says whose exchange it is
	 */
	public ExecutorDeadlineHttp(String threadPrefix) {
		this(Executors.newThreadPerTaskExecutor(Thread.ofVirtual().name(requirePrefix(threadPrefix), 0).factory()));
	}

	ExecutorDeadlineHttp(ExecutorService executor) {
		this.executor = Objects.requireNonNull(executor, "executor");
	}

	private static String requirePrefix(String threadPrefix) {
		Objects.requireNonNull(threadPrefix, "threadPrefix");
		if (threadPrefix.isBlank()) {
			throw new IllegalArgumentException("threadPrefix must not be blank");
		}
		return threadPrefix;
	}

	@Override
	public <T> HttpResponse<T> send(HttpClient client, HttpRequest request, BufferedBody<T> body, Duration deadline)
			throws IOException, InterruptedException, TimeoutException {
		Objects.requireNonNull(client, "client");
		Objects.requireNonNull(request, "request");
		Objects.requireNonNull(body, "body");
		// Before anything is submitted: an exchange started with a deadline that cannot be waited for would run
		// unbounded and uncancelled.
		long nanos = deadlineNanos(deadline);
		HttpResponse.BodyHandler<T> handler = body.handler();
		Future<HttpResponse<T>> exchange;
		try {
			exchange = executor.submit(() -> client.send(request, handler));
		}
		catch (RejectedExecutionException e) {
			throw new IOException("The HTTP executor is shut down", e);
		}
		try {
			return exchange.get(nanos, TimeUnit.NANOSECONDS);
		}
		catch (TimeoutException | InterruptedException e) {
			exchange.cancel(true);
			throw e;
		}
		catch (ExecutionException e) {
			Throwable cause = Objects.requireNonNullElse(e.getCause(), e);
			if (cause instanceof IOException io) {
				throw io;
			}
			if (cause instanceof Error error) {
				throw error;
			}
			// What sendAsync's future failed with too: every failure of the exchange is the peer being unreachable to
			// the callers, so one exception type carries it, the cause kept.
			throw new IOException("The HTTP exchange failed: " + cause, cause);
		}
	}

	private static long deadlineNanos(Duration deadline) {
		Objects.requireNonNull(deadline, "deadline");
		if (deadline.isNegative() || deadline.isZero()) {
			throw new IllegalArgumentException("A deadline is positive: " + deadline);
		}
		try {
			return deadline.toNanos();
		}
		catch (ArithmeticException e) {
			throw new IllegalArgumentException("A deadline fits in a long of nanoseconds: " + deadline, e);
		}
	}

	/** Interrupts the exchanges still running and refuses new ones. */
	@Override
	public void close() {
		executor.shutdownNow();
	}
}
