package dev.bluestep.http;

import java.io.IOException;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CopyOnWriteArrayList;
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
			if (handler instanceof CappedBodyHandler<T>) {
				exchange = executor.submit(() -> sendCapped(client, request, handler));
			}
			else {
				exchange = executor.submit(() -> client.send(request, handler));
			}
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
				throw tooLarge(io);
			}
			if (cause instanceof Error error) {
				throw error;
			}
			// What sendAsync's future failed with too: every failure of the exchange is the peer being unreachable to
			// the callers, so one exception type carries it, the cause kept.
			throw new IOException("The HTTP exchange failed: " + cause, cause);
		}
	}

	/**
	 * A capped body's exchange, which keeps the subscribers it hands the client (one per response, so a redirect makes
	 * two) and, when the send fails, throws a breach any of them recorded. The breach cannot be left to the exception
	 * the client throws: on HTTP/2 the client's cancellation can complete the exchange (with {@code IOException:
	 * Stream N cancelled}) before it has looked at the body's future, and the breach is then nowhere in the cause
	 * chain. The subscriber completes its body with the breach before it cancels, so it is always there to find.
	 */
	private static <T> HttpResponse<T> sendCapped(HttpClient client, HttpRequest request,
			HttpResponse.BodyHandler<T> handler) throws IOException, InterruptedException {
		List<HttpResponse.BodySubscriber<T>> issued = new CopyOnWriteArrayList<>();
		HttpResponse.BodyHandler<T> watched = info -> {
			HttpResponse.BodySubscriber<T> subscriber = handler.apply(info);
			issued.add(subscriber);
			return subscriber;
		};
		try {
			return client.send(request, watched);
		}
		catch (IOException failure) {
			for (HttpResponse.BodySubscriber<T> subscriber : issued) {
				CompletableFuture<T> read = subscriber.getBody().toCompletableFuture();
				if (read.isCompletedExceptionally() && tooLarge(read.exceptionNow()) instanceof BodyTooLargeException breach) {
					throw breach;
				}
			}
			throw failure;
		}
	}

	/**
	 * A capped body's {@link BodyTooLargeException} as itself: the JDK client's {@code send} rethrows a body's failure
	 * as a new {@code IOException} with the original as its cause, which would hide the type callers map. Any other
	 * failure is returned unchanged.
	 */
	private static IOException tooLarge(IOException failure) {
		return tooLarge((Throwable) failure) instanceof BodyTooLargeException breach ? breach : failure;
	}

	/** The {@link BodyTooLargeException} in {@code failure}'s cause chain, or {@code failure}. */
	private static Throwable tooLarge(Throwable failure) {
		Throwable at = failure;
		for (int depth = 0; at != null && depth < 8; depth++) {
			if (at instanceof BodyTooLargeException tooLarge) {
				return tooLarge;
			}
			at = at.getCause();
		}
		return failure;
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
