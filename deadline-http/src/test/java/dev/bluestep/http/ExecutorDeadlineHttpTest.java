package dev.bluestep.http;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.IOException;
import java.io.OutputStream;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;

/**
 * {@link ExecutorDeadlineHttp} against a real JDK client and a stub server: the deadline covers the body, a timed-out
 * or interrupted exchange is cancelled, failures keep their cause, and exchanges run on the named virtual threads.
 */
class ExecutorDeadlineHttpTest {

	static final Duration SHORT = Duration.ofMillis(400);
	static final Duration GENEROUS = Duration.ofSeconds(5);

	HttpServer server;
	/** Released when a test ends, so a stalling handler never outlives it. */
	final CountDownLatch release = new CountDownLatch(1);
	/** Released when the stalling handler sees its client go away: the exchange was cancelled, not left running. */
	final CountDownLatch clientGone = new CountDownLatch(1);
	final AtomicInteger stallBodyCalls = new AtomicInteger();
	final HttpClient client = HttpClient.newBuilder().version(HttpClient.Version.HTTP_1_1).build();
	final ExecutorDeadlineHttp http = new ExecutorDeadlineHttp("deadline-http-test-");

	@BeforeEach
	void startStub() throws IOException {
		server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
		server.setExecutor(Executors.newCachedThreadPool(runnable -> {
			Thread thread = new Thread(runnable, "deadline-stub");
			thread.setDaemon(true);
			return thread;
		}));
		server.createContext("/ok", exchange -> answer(exchange, "hello"));
		server.createContext("/stall-headers", exchange -> {
			await(release);
			answer(exchange, "late");
		});
		server.createContext("/stall-body", exchange -> {
			stallBodyCalls.incrementAndGet();
			stallBody(exchange);
		});
		server.start();
	}

	@AfterEach
	void stop() {
		release.countDown();
		server.stop(0);
		http.close();
	}

	private static void answer(HttpExchange exchange, String body) throws IOException {
		byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
		exchange.sendResponseHeaders(200, bytes.length);
		try (OutputStream out = exchange.getResponseBody()) {
			out.write(bytes);
		}
	}

	/** Headers and the first bytes at once, then nothing: the body stalls, and the client's request timeout is done. */
	private void stallBody(HttpExchange exchange) throws IOException {
		exchange.sendResponseHeaders(200, 0);
		OutputStream out = exchange.getResponseBody();
		try {
			while (release.getCount() > 0) {
				out.write('x');
				out.flush();
				release.await(100, TimeUnit.MILLISECONDS);
			}
		}
		catch (IOException gone) {
			clientGone.countDown();
		}
		catch (InterruptedException e) {
			Thread.currentThread().interrupt();
		}
		finally {
			exchange.close();
		}
	}

	private static void await(CountDownLatch latch) {
		try {
			latch.await(30, TimeUnit.SECONDS);
		}
		catch (InterruptedException e) {
			Thread.currentThread().interrupt();
		}
	}

	private HttpRequest get(String path) {
		return HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + server.getAddress().getPort() + path))
			.timeout(GENEROUS).build();
	}

	@Test
	void answersWithinTheDeadline() throws Exception {
		HttpResponse<String> response = http.send(client, get("/ok"), BufferedBody.ofString(), GENEROUS);
		assertThat(response.statusCode()).isEqualTo(200);
		assertThat(response.body()).isEqualTo("hello");
	}

	@Test
	void theDeadlineCoversABodyThatStallsAfterTheHeaders() {
		long started = System.nanoTime();
		assertThatThrownBy(() -> http.send(client, get("/stall-body"), BufferedBody.ofString(), SHORT))
			.isInstanceOf(TimeoutException.class);
		assertThat(Duration.ofNanos(System.nanoTime() - started)).isLessThan(Duration.ofSeconds(3));
	}

	@Test
	void aTimedOutExchangeIsCancelledNotLeftRunning() throws Exception {
		assertThatThrownBy(() -> http.send(client, get("/stall-body"), BufferedBody.ofString(), SHORT))
			.isInstanceOf(TimeoutException.class);
		assertThat(clientGone.await(10, TimeUnit.SECONDS)).as("the stub saw the client abort the exchange").isTrue();
	}

	@Test
	void theDeadlineCoversStalledHeadersToo() {
		assertThatThrownBy(() -> http.send(client, get("/stall-headers"), BufferedBody.ofString(), SHORT))
			.isInstanceOf(TimeoutException.class);
	}

	@Test
	void anInterruptedCallerGetsInterruptedExceptionAndTheExchangeIsCancelled() throws Exception {
		AtomicReference<Throwable> thrown = new AtomicReference<>();
		Thread caller = Thread.ofPlatform().start(() -> {
			try {
				http.send(client, get("/stall-body"), BufferedBody.ofString(), GENEROUS);
			}
			catch (Throwable t) {
				thrown.set(t);
			}
		});
		Thread.sleep(300);
		caller.interrupt();
		caller.join(5_000);
		assertThat(caller.isAlive()).isFalse();
		assertThat(thrown.get()).isInstanceOf(InterruptedException.class);
		assertThat(clientGone.await(10, TimeUnit.SECONDS)).as("the stub saw the client abort the exchange").isTrue();
	}

	@Test
	void anIoFailureIsRethrownAsItself() throws IOException {
		int closedPort;
		try (ServerSocket socket = new ServerSocket(0)) {
			closedPort = socket.getLocalPort();
		}
		HttpRequest refused = HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + closedPort + "/")).build();
		assertThatThrownBy(() -> http.send(client, refused, BufferedBody.ofString(), GENEROUS))
			.isInstanceOf(java.net.ConnectException.class);
	}

	@Test
	void anyOtherFailureIsAnIoExceptionWithItsCause() {
		IllegalStateException broken = new IllegalStateException("client broke");
		HttpClient failing = new RecordingHttpClient(client, new AtomicReference<>(), broken);
		assertThatThrownBy(() -> http.send(failing, get("/ok"), BufferedBody.ofString(), GENEROUS))
			.isInstanceOf(IOException.class).hasCause(broken);
	}

	@Test
	void anErrorIsRethrownAsItself() {
		AssertionError fatal = new AssertionError("fatal");
		HttpClient failing = new RecordingHttpClient(client, new AtomicReference<>(), fatal);
		assertThatThrownBy(() -> http.send(failing, get("/ok"), BufferedBody.ofString(), GENEROUS))
			.isSameAs(fatal);
	}

	@Test
	void theSendRunsOnAVirtualThreadNamedByThePrefix() throws Exception {
		AtomicReference<Thread> sender = new AtomicReference<>();
		HttpClient recording = new RecordingHttpClient(client, sender, null);
		http.send(recording, get("/ok"), BufferedBody.ofString(), GENEROUS);
		assertThat(sender.get().isVirtual()).isTrue();
		assertThat(sender.get().getName()).startsWith("deadline-http-test-");
		assertThat(sender.get()).isNotSameAs(Thread.currentThread());
	}

	@Test
	void aClosedInstanceRefusesWithAnIoException() {
		ExecutorDeadlineHttp closed = new ExecutorDeadlineHttp("closed-");
		closed.close();
		assertThatThrownBy(() -> closed.send(client, get("/ok"), BufferedBody.ofString(), GENEROUS))
			.isInstanceOf(IOException.class).hasMessageContaining("shut down");
	}

	@Test
	void closingInterruptsAnExchangeStillRunning() throws Exception {
		AtomicReference<Throwable> thrown = new AtomicReference<>();
		Thread caller = Thread.ofPlatform().start(() -> {
			try {
				http.send(client, get("/stall-body"), BufferedBody.ofString(), GENEROUS);
			}
			catch (Throwable t) {
				thrown.set(t);
			}
		});
		Thread.sleep(300);
		http.close();
		caller.join(5_000);
		assertThat(caller.isAlive()).isFalse();
		assertThat(thrown.get()).isInstanceOf(IOException.class);
	}

	@Test
	void argumentsAreRequired() {
		HttpRequest request = get("/ok");
		assertThatThrownBy(() -> new ExecutorDeadlineHttp(" ")).isInstanceOf(IllegalArgumentException.class);
		assertThatThrownBy(() -> http.send(client, request, BufferedBody.ofString(), null))
			.isInstanceOf(NullPointerException.class).hasMessage("deadline");
		assertThatThrownBy(() -> http.send(client, request, null, GENEROUS))
			.isInstanceOf(NullPointerException.class).hasMessage("body");
	}

	@Test
	void aDeadlineThatCannotBeWaitedForIsRefusedBeforeAnythingIsSent() {
		AtomicInteger submitted = new AtomicInteger();
		ExecutorService counting = Executors.newThreadPerTaskExecutor(runnable -> {
			submitted.incrementAndGet();
			return Thread.ofVirtual().unstarted(runnable);
		});
		try (ExecutorDeadlineHttp guarded = new ExecutorDeadlineHttp(counting)) {
			for (Duration bad : List.of(Duration.ofSeconds(Long.MAX_VALUE), Duration.ofDays(106_752 * 2L), Duration.ZERO,
					Duration.ofMillis(-1))) {
				assertThatThrownBy(() -> guarded.send(client, get("/stall-body"), BufferedBody.ofString(), bad))
					.as(bad.toString()).isInstanceOf(IllegalArgumentException.class);
			}
		}
		assertThat(submitted).as("no exchange was started").hasValue(0);
		assertThat(stallBodyCalls).as("the peer saw no request").hasValue(0);
	}

	@Test
	void everyBufferedBodyReadsTheWholeBody() throws Exception {
		assertThat(http.send(client, get("/ok"), BufferedBody.ofString(StandardCharsets.UTF_8), GENEROUS).body())
			.isEqualTo("hello");
		assertThat(http.send(client, get("/ok"), BufferedBody.ofByteArray(), GENEROUS).body())
			.isEqualTo("hello".getBytes(StandardCharsets.UTF_8));
		assertThat(http.send(client, get("/ok"), BufferedBody.discarding(), GENEROUS).statusCode()).isEqualTo(200);
		assertThatThrownBy(() -> http.send(client, get("/stall-body"), BufferedBody.ofByteArray(), SHORT))
			.isInstanceOf(TimeoutException.class);
		assertThatThrownBy(() -> http.send(client, get("/stall-body"), BufferedBody.discarding(), SHORT))
			.as("discarding still reads the body to its end").isInstanceOf(TimeoutException.class);
	}

	@Test
	void noStreamingHandlerCanBePassed() throws Exception {
		// The compile-time guarantee, pinned: send takes a BufferedBody, whose only implementation is package-private
		// and made only by BufferedBody's factories.
		assertThat(DeadlineHttp.class.getMethod("send", HttpClient.class, HttpRequest.class, BufferedBody.class,
			Duration.class)).isNotNull();
		assertThat(BufferedBody.class.isSealed()).isTrue();
		assertThat(BufferedBody.class.getPermittedSubclasses()).containsExactly(BufferedHandler.class);
		assertThat(Modifier.isPublic(BufferedHandler.class.getModifiers())).isFalse();
		for (Method method : DeadlineHttp.class.getMethods()) {
			assertThat(method.getParameterTypes()).as(method.toString()).doesNotContain(HttpResponse.BodyHandler.class);
		}
	}
}
