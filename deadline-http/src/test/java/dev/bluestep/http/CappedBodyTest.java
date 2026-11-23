package dev.bluestep.http;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.catchThrowable;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketException;
import java.net.SocketTimeoutException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpHeaders;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executors;
import java.util.concurrent.Flow;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;

/**
 * The capped bodies ({@link BufferedBody#ofByteArray(long)}, {@link BufferedBody#ofString(long)}) against a real JDK
 * client and a stub server: a body at or under the cap is read whole, a declared length over it is refused before any
 * body is read, a streamed body is refused the moment it passes it (and the exchange cancelled), the breach is a
 * {@link BodyTooLargeException} thrown as itself, and the deadline still applies.
 */
class CappedBodyTest {

	static final int CAP = 100;
	static final Duration SHORT = Duration.ofMillis(400);
	static final Duration GENEROUS = Duration.ofSeconds(5);

	HttpServer server;
	/** Released when a test ends, so a stalling handler never outlives it. */
	final CountDownLatch release = new CountDownLatch(1);
	/** Released when an endless body's handler sees its client go away: the exchange was cancelled. */
	final CountDownLatch clientGone = new CountDownLatch(1);
	/** The bytes the endless body's handler managed to write. */
	final AtomicLong endlessWritten = new AtomicLong();
	final HttpClient client = HttpClient.newBuilder().version(HttpClient.Version.HTTP_1_1).build();
	final ExecutorDeadlineHttp http = new ExecutorDeadlineHttp("capped-body-test-");

	@BeforeEach
	void startStub() throws IOException {
		server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
		server.setExecutor(Executors.newCachedThreadPool(runnable -> {
			Thread thread = new Thread(runnable, "capped-stub");
			thread.setDaemon(true);
			return thread;
		}));
		// /fixed?n: n bytes with their Content-Length.
		server.createContext("/fixed", exchange -> {
			byte[] bytes = bytes(count(exchange));
			exchange.sendResponseHeaders(200, bytes.length == 0 ? -1 : bytes.length);
			try (OutputStream out = exchange.getResponseBody()) {
				out.write(bytes);
			}
		});
		// /chunked?n: n bytes, chunked (no Content-Length), flushed ten bytes at a time.
		server.createContext("/chunked", exchange -> {
			byte[] bytes = bytes(count(exchange));
			exchange.sendResponseHeaders(200, 0);
			try (OutputStream out = exchange.getResponseBody()) {
				for (int at = 0; at < bytes.length; at += 10) {
					out.write(bytes, at, Math.min(10, bytes.length - at));
					out.flush();
				}
			}
		});
		// A chunked body that never ends.
		server.createContext("/endless", exchange -> {
			exchange.sendResponseHeaders(200, 0);
			OutputStream out = exchange.getResponseBody();
			byte[] chunk = bytes(64);
			try {
				while (release.getCount() > 0) {
					out.write(chunk);
					out.flush();
					endlessWritten.addAndGet(chunk.length);
				}
			}
			catch (IOException gone) {
				clientGone.countDown();
			}
			finally {
				exchange.close();
			}
		});
		// Headers and a first byte, then a stalled body.
		server.createContext("/stall-body", exchange -> {
			exchange.sendResponseHeaders(200, 0);
			OutputStream out = exchange.getResponseBody();
			try {
				out.write('x');
				out.flush();
				release.await(30, TimeUnit.SECONDS);
			}
			catch (InterruptedException e) {
				Thread.currentThread().interrupt();
			}
			catch (IOException gone) {
				// the client is gone
			}
			finally {
				exchange.close();
			}
		});
		// "é" in ISO-8859-1 (one byte), naming its charset.
		server.createContext("/latin1", exchange -> {
			byte[] bytes = "café".getBytes(StandardCharsets.ISO_8859_1);
			exchange.getResponseHeaders().add("Content-Type", "text/plain; charset=\"ISO-8859-1\"");
			exchange.sendResponseHeaders(200, bytes.length);
			try (OutputStream out = exchange.getResponseBody()) {
				out.write(bytes);
			}
		});
		server.start();
	}

	@AfterEach
	void stop() {
		release.countDown();
		server.stop(0);
		http.close();
	}

	private static int count(HttpExchange exchange) {
		String query = exchange.getRequestURI().getQuery();
		return query == null ? 0 : Integer.parseInt(query);
	}

	/** {@code n} bytes, numbered, so a body read whole can be told from one read in part. */
	private static byte[] bytes(int n) {
		byte[] bytes = new byte[n];
		for (int i = 0; i < n; i++) {
			bytes[i] = (byte) ('a' + i % 26);
		}
		return bytes;
	}

	private HttpRequest get(String path) {
		return HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + server.getAddress().getPort() + path))
			.timeout(GENEROUS).build();
	}

	private byte[] read(String path, long cap) throws Exception {
		return http.send(client, get(path), BufferedBody.ofByteArray(cap), GENEROUS).body();
	}

	private BodyTooLargeException refused(String path, long cap) {
		Throwable thrown = catchThrowable(() -> read(path, cap));
		assertThat(thrown).as(path).isExactlyInstanceOf(BodyTooLargeException.class);
		return (BodyTooLargeException) thrown;
	}

	@Test
	void aBodyUnderTheCapIsReadWhole() throws Exception {
		assertThat(read("/fixed?50", CAP)).isEqualTo(bytes(50));
		assertThat(read("/chunked?50", CAP)).isEqualTo(bytes(50));
	}

	@Test
	void aBodyExactlyAtTheCapIsReadWhole() throws Exception {
		assertThat(read("/fixed?" + CAP, CAP)).isEqualTo(bytes(CAP));
		assertThat(read("/chunked?" + CAP, CAP)).as("ten chunks, the last reaching the cap").isEqualTo(bytes(CAP));
	}

	@Test
	void aDeclaredLengthOneByteOverIsRefused() {
		BodyTooLargeException refusal = refused("/fixed?" + (CAP + 1), CAP);
		assertThat(refusal.declared()).isTrue();
		assertThat(refusal.size()).isEqualTo(CAP + 1);
		assertThat(refusal.maxBytes()).isEqualTo(CAP);
		assertThat(refusal).hasMessageContaining("declares a body of 101 bytes");
	}

	@Test
	void aDeclaredLengthOverTheCapIsRefusedBeforeAnyOfTheBodyIsRead() throws Exception {
		// A raw socket, not the HttpServer stub: HttpServer holds a fixed-length response's headers back until the
		// first body write, and here the peer must send its headers and then none of the body it declares.
		CountDownLatch connectionClosed = new CountDownLatch(1);
		try (ServerSocket peer = new ServerSocket(0, 1, InetAddress.getLoopbackAddress())) {
			Thread.ofVirtual().start(() -> {
				try (Socket socket = peer.accept()) {
					InputStream in = socket.getInputStream();
					readRequestHead(in);
					socket.getOutputStream().write(
						"HTTP/1.1 200 OK\r\nContent-Length: 10000000\r\n\r\n".getBytes(StandardCharsets.US_ASCII));
					socket.getOutputStream().flush();
					socket.setSoTimeout(10_000);
					try {
						if (in.read() < 0) {
							connectionClosed.countDown();
						}
					}
					catch (SocketTimeoutException stillOpen) {
						// the client left the connection open: connectionClosed stays up and the test fails
					}
					catch (SocketException reset) {
						connectionClosed.countDown();
					}
				}
				catch (IOException e) {
					// the stub failed before the client could answer; the send's assertions report it
				}
			});
			HttpRequest request = HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + peer.getLocalPort() + "/"))
				.timeout(GENEROUS).build();
			long started = System.nanoTime();
			Throwable thrown = catchThrowable(() -> http.send(client, request, BufferedBody.ofByteArray(CAP), GENEROUS));
			assertThat(thrown).isExactlyInstanceOf(BodyTooLargeException.class);
			BodyTooLargeException refusal = (BodyTooLargeException) thrown;
			assertThat(refusal.declared()).isTrue();
			assertThat(refusal.size()).isEqualTo(10_000_000);
			assertThat(Duration.ofNanos(System.nanoTime() - started))
				.as("refused on the headers, with no body sent, well before the deadline").isLessThan(Duration.ofSeconds(3));
			assertThat(connectionClosed.await(10, TimeUnit.SECONDS)).as("the client closed the connection").isTrue();
		}
	}

	/** Reads up to the blank line that ends a request's head. */
	private static void readRequestHead(InputStream in) throws IOException {
		int matched = 0;
		byte[] end = {'\r', '\n', '\r', '\n'};
		while (matched < end.length) {
			int b = in.read();
			if (b < 0) {
				throw new IOException("the request ended early");
			}
			matched = b == end[matched] ? matched + 1 : (b == '\r' ? 1 : 0);
		}
	}

	@Test
	void aStreamedBodyOneByteOverIsRefused() {
		BodyTooLargeException refusal = refused("/chunked?" + (CAP + 1), CAP);
		assertThat(refusal.declared()).isFalse();
		assertThat(refusal.size()).as("the cap, then the one-byte chunk that passed it").isEqualTo(CAP + 1);
		assertThat(refusal).hasMessageContaining("passed the cap of 100 bytes");
	}

	@Test
	void anEndlessChunkedBodyIsRefusedAtTheCapAndTheExchangeCancelled() throws Exception {
		long started = System.nanoTime();
		BodyTooLargeException refusal = refused("/endless", 1_000);
		assertThat(refusal.declared()).isFalse();
		assertThat(refusal.size()).isGreaterThan(1_000);
		assertThat(Duration.ofNanos(System.nanoTime() - started))
			.as("refused at the cap, not at the deadline").isLessThan(Duration.ofSeconds(3));
		assertThat(clientGone.await(10, TimeUnit.SECONDS)).as("the stub saw the client abort the exchange").isTrue();
	}

	@Test
	void anEmptyBodyIsReadUnderAnyCap() throws Exception {
		assertThat(read("/fixed?0", CAP)).isEmpty();
		assertThat(read("/chunked?0", CAP)).isEmpty();
		assertThat(read("/fixed?0", 0)).as("a cap of zero takes only an empty body").isEmpty();
		assertThat(refused("/chunked?1", 0).size()).isEqualTo(1);
	}

	@Test
	void theDeadlineStillAppliesWhileCapped() {
		long started = System.nanoTime();
		assertThatThrownBy(() -> http.send(client, get("/stall-body"), BufferedBody.ofByteArray(CAP), SHORT))
			.isInstanceOf(TimeoutException.class);
		assertThatThrownBy(() -> http.send(client, get("/stall-body"), BufferedBody.ofString(CAP), SHORT))
			.isInstanceOf(TimeoutException.class);
		assertThat(Duration.ofNanos(System.nanoTime() - started)).isLessThan(Duration.ofSeconds(5));
	}

	@Test
	void theStringTwinsCapAndDecode() throws Exception {
		assertThat(http.send(client, get("/latin1"), BufferedBody.ofString(CAP), GENEROUS).body())
			.as("the response's charset").isEqualTo("café");
		assertThat(http.send(client, get("/fixed?26"), BufferedBody.ofString(StandardCharsets.US_ASCII, CAP), GENEROUS)
			.body()).isEqualTo("abcdefghijklmnopqrstuvwxyz");
		assertThatThrownBy(() -> http.send(client, get("/chunked?" + (CAP + 1)), BufferedBody.ofString(CAP), GENEROUS))
			.isExactlyInstanceOf(BodyTooLargeException.class);
		assertThatThrownBy(() -> http.send(client, get("/fixed?" + (CAP + 1)),
			BufferedBody.ofString(StandardCharsets.UTF_8, CAP), GENEROUS)).isExactlyInstanceOf(BodyTooLargeException.class);
	}

	@Test
	void theBreachIsAnIoExceptionSoExistingCallersStillCatchIt() {
		assertThat(IOException.class).isAssignableFrom(BodyTooLargeException.class);
	}

	@Test
	void aCapOutsideWhatAnArrayHoldsIsRefused() {
		assertThatThrownBy(() -> BufferedBody.ofByteArray(-1)).isInstanceOf(IllegalArgumentException.class);
		assertThatThrownBy(() -> BufferedBody.ofString(Integer.MAX_VALUE)).isInstanceOf(IllegalArgumentException.class);
		assertThatThrownBy(() -> BufferedBody.ofString(null, CAP)).isInstanceOf(NullPointerException.class);
		assertThat(BufferedBody.ofByteArray(Integer.MAX_VALUE - 8)).isNotNull();
	}

	@Test
	void theUncappedBodiesStillTakeAnythingWithinTheDeadline() throws Exception {
		assertThat(http.send(client, get("/chunked?5000"), BufferedBody.ofByteArray(), GENEROUS).body())
			.isEqualTo(bytes(5000));
	}

	@Test
	void theSubscriberNeverCopiesTheChunkThatPassesTheCapAndIgnoresWhatFollows() throws Exception {
		RecordingSubscription subscription = new RecordingSubscription();
		CappedBodySubscriber subscriber = new CappedBodySubscriber(10, -1);
		subscriber.onSubscribe(subscription);
		assertThat(subscription.requested).as("one chunk at a time").hasValue(1);
		subscriber.onNext(List.of(ByteBuffer.wrap(bytes(6))));
		assertThat(subscription.requested).hasValue(2);
		ByteBuffer passing = ByteBuffer.wrap(bytes(5));
		subscriber.onNext(List.of(passing));
		assertThat(passing.remaining()).as("the breaching chunk was not read").isEqualTo(5);
		assertThat(subscription.cancelled).isTrue();
		assertThat(subscription.requested).as("nothing more asked for").hasValue(2);
		subscriber.onNext(List.of(ByteBuffer.wrap(bytes(1))));
		subscriber.onError(new IOException("after the cancel"));
		subscriber.onComplete();
		assertThatThrownBy(() -> subscriber.getBody().toCompletableFuture().get())
			.isInstanceOf(ExecutionException.class).cause().isInstanceOf(BodyTooLargeException.class)
			.hasMessageContaining("11 bytes received");
	}

	@Test
	void aRefusingSubscriberCancelsWithoutAskingForAnything() {
		RecordingSubscription subscription = new RecordingSubscription();
		CappedBodySubscriber subscriber = CappedBodySubscriber.refusing(new BodyTooLargeException(10, 11, true));
		assertThat(subscriber.getBody().toCompletableFuture()).isCompletedExceptionally();
		subscriber.onSubscribe(subscription);
		assertThat(subscription.cancelled).isTrue();
		assertThat(subscription.requested).hasValue(0);
	}

	@Test
	void manyChunksAreJoinedInOrder() throws Exception {
		CappedBodySubscriber subscriber = new CappedBodySubscriber(30, -1);
		subscriber.onSubscribe(new RecordingSubscription());
		byte[] all = bytes(30);
		subscriber.onNext(List.of(ByteBuffer.wrap(all, 0, 10), ByteBuffer.wrap(all, 10, 5)));
		subscriber.onNext(List.of());
		subscriber.onNext(List.of(ByteBuffer.wrap(all, 15, 15)));
		subscriber.onComplete();
		assertThat(subscriber.getBody().toCompletableFuture().get()).isEqualTo(all);
	}

	@Test
	void aNoBodyStatusIsNotRefusedOnItsContentLength() throws Exception {
		CappedBodyHandler<byte[]> handler = new CappedBodyHandler<>(10, response -> bytes -> bytes);
		HttpHeaders big = HttpHeaders.of(Map.of("Content-Length", List.of("1000")), (name, value) -> true);
		for (int status : new int[] {204, 304}) {
			HttpResponse.BodySubscriber<byte[]> subscriber = handler.apply(new Info(status, big));
			subscriber.onSubscribe(new RecordingSubscription());
			subscriber.onComplete();
			assertThat(subscriber.getBody().toCompletableFuture().get()).as("status " + status).isEmpty();
		}
		HttpResponse.BodySubscriber<byte[]> ok = handler.apply(new Info(200, big));
		assertThat(ok.getBody().toCompletableFuture()).isCompletedExceptionally();
	}

	@Test
	void theStringTwinDecodesExactlyAsOfStringDoes() throws Exception {
		byte[] latin1 = "caf\u00e9".getBytes(StandardCharsets.ISO_8859_1);
		for (String contentType : List.of("text/plain; charset=ISO-8859-1", "text/plain;CHARSET=\"ISO-8859-1\"",
				"text/plain; foo=\"bar;charset=UTF-8\"; charset=ISO-8859-1", "text/plain", "text/plain; charset=no-such",
				"")) {
			HttpHeaders headers = HttpHeaders.of(contentType.isEmpty() ? Map.of() : Map.of("Content-Type", List.of(contentType)),
				(name, value) -> true);
			Info info = new Info(200, headers);
			assertThat(CappedBodyHandler.decodeAsOfString(info, latin1)).as(contentType)
				.isEqualTo(jdkOfString(info, latin1));
		}
	}

	private static String jdkOfString(HttpResponse.ResponseInfo info, byte[] bytes) throws Exception {
		HttpResponse.BodySubscriber<String> jdk = HttpResponse.BodyHandlers.ofString().apply(info);
		jdk.onSubscribe(new RecordingSubscription());
		jdk.onNext(List.of(ByteBuffer.wrap(bytes)));
		jdk.onComplete();
		return jdk.getBody().toCompletableFuture().get();
	}

	@Test
	void manyTinyChunksAreHeldInFixedBlocks() throws Exception {
		int size = 3 * CappedBodySubscriber.BLOCK + 7;
		CappedBodySubscriber subscriber = new CappedBodySubscriber(1_000_000, -1);
		subscriber.onSubscribe(new RecordingSubscription());
		byte[] all = bytes(size);
		for (int i = 0; i < size; i++) {
			subscriber.onNext(List.of(ByteBuffer.wrap(all, i, 1)));
		}
		assertThat(subscriber.blocksHeld()).as("one block per BLOCK bytes, not one per chunk").isEqualTo(4);
		subscriber.onComplete();
		assertThat(subscriber.getBody().toCompletableFuture().get()).isEqualTo(all);
	}

	@Test
	void aDeclaredLengthIsReadIntoOneArrayAndCannotBeOverrun() throws Exception {
		CappedBodySubscriber subscriber = new CappedBodySubscriber(100, 20);
		subscriber.onSubscribe(new RecordingSubscription());
		byte[] all = bytes(20);
		subscriber.onNext(List.of(ByteBuffer.wrap(all, 0, 1), ByteBuffer.wrap(all, 1, 9)));
		subscriber.onNext(List.of(ByteBuffer.wrap(all, 10, 10)));
		assertThat(subscriber.blocksHeld()).isZero();
		subscriber.onComplete();
		assertThat(subscriber.getBody().toCompletableFuture().get()).isEqualTo(all);

		RecordingSubscription subscription = new RecordingSubscription();
		CappedBodySubscriber overrun = new CappedBodySubscriber(100, 5);
		overrun.onSubscribe(subscription);
		overrun.onNext(List.of(ByteBuffer.wrap(bytes(6))));
		assertThat(subscription.cancelled).isTrue();
		assertThatThrownBy(() -> overrun.getBody().toCompletableFuture().get()).cause()
			.isInstanceOf(java.net.ProtocolException.class);
	}

	@Test
	void aBreachIsThrownEvenWhenTheClientReportsOnlyItsCancellation() {
		// What HTTP/2 can do: the client's cancellation completes the exchange with "Stream N cancelled" before it
		// has looked at the body's future, so the breach is nowhere in the cause chain of what send throws.
		HttpClient http2Like = new RecordingHttpClient(client, new AtomicReference<>(), null) {
			@Override
			public <T> HttpResponse<T> send(HttpRequest request, HttpResponse.BodyHandler<T> handler) throws IOException {
				HttpResponse.BodySubscriber<T> subscriber = handler.apply(new Info(200, HttpHeaders.of(Map.of(),
					(name, value) -> true)));
				subscriber.onSubscribe(new RecordingSubscription());
				subscriber.onNext(List.of(ByteBuffer.wrap(bytes(CAP + 1))));
				throw new IOException("Stream 1 cancelled");
			}
		};
		assertThatThrownBy(() -> http.send(http2Like, get("/fixed?1"), BufferedBody.ofByteArray(CAP), GENEROUS))
			.isExactlyInstanceOf(BodyTooLargeException.class);
		assertThatThrownBy(() -> http.send(http2Like, get("/fixed?1"), BufferedBody.ofString(CAP), GENEROUS))
			.isExactlyInstanceOf(BodyTooLargeException.class);
		assertThatThrownBy(() -> http.send(http2Like, get("/fixed?1"), BufferedBody.ofByteArray(), GENEROUS))
			.as("an uncapped body's failure is untouched").isExactlyInstanceOf(IOException.class)
			.hasMessage("Stream 1 cancelled");
	}

	/** Counts what the subscriber asks for and whether it cancelled. */
	static final class RecordingSubscription implements Flow.Subscription {
		final AtomicLong requested = new AtomicLong();
		volatile boolean cancelled;

		@Override
		public void request(long n) {
			requested.addAndGet(n);
		}

		@Override
		public void cancel() {
			cancelled = true;
		}
	}

	/** A response's status and headers. */
	record Info(int statusCode, HttpHeaders headers) implements HttpResponse.ResponseInfo {
		@Override
		public HttpClient.Version version() {
			return HttpClient.Version.HTTP_1_1;
		}
	}
}
