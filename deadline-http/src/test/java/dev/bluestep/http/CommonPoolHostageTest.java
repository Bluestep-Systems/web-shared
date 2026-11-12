package dev.bluestep.http;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.ForkJoinPool;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;

/**
 * An exchange through {@link ExecutorDeadlineHttp} answers promptly while every worker of
 * {@code ForkJoinPool.commonPool()} is held hostage (RG1flake2, ported from web-global's gateway test of the same
 * name): on a two-CPU host the pool has one worker, and a test holding it once starved {@code sendAsync} answers,
 * which the JDK delivers through that pool, into 502s. Holding every worker here is that host at any CPU count. The
 * first test proves the hostage takes: a bare {@code sendAsync} does not complete. The gateway's own per-client
 * hostage tests stay with the gateway.
 */
class CommonPoolHostageTest {

	/** Each call's deadline: a starved call would end here, as a timeout. */
	static final Duration DEADLINE = Duration.ofSeconds(5);
	/** What "promptly" means: well inside the deadline. */
	static final Duration PROMPT = Duration.ofSeconds(2);

	HttpServer server;
	final ExecutorDeadlineHttp http = new ExecutorDeadlineHttp("hostage-http-");
	final CountDownLatch release = new CountDownLatch(1);

	@BeforeEach
	void startStubAndTakeTheCommonPoolHostage() throws Exception {
		server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
		server.setExecutor(Executors.newCachedThreadPool(runnable -> {
			Thread thread = new Thread(runnable, "hostage-stub");
			thread.setDaemon(true);
			return thread;
		}));
		server.createContext("/", CommonPoolHostageTest::answer);
		server.start();
		// A latch wait is not a managed block, so the pool adds no worker in its place: every worker is held.
		int workers = ForkJoinPool.getCommonPoolParallelism();
		CountDownLatch taken = new CountDownLatch(workers);
		for (int i = 0; i < workers; i++) {
			ForkJoinPool.commonPool().execute(() -> {
				taken.countDown();
				try {
					release.await(30, TimeUnit.SECONDS);
				}
				catch (InterruptedException e) {
					Thread.currentThread().interrupt();
				}
			});
		}
		assertThat(taken.await(10, TimeUnit.SECONDS)).as("all %d common-pool workers held", workers).isTrue();
	}

	@AfterEach
	void releaseAndStop() {
		release.countDown();
		server.stop(0);
		http.close();
	}

	private static void answer(HttpExchange exchange) throws IOException {
		exchange.getRequestBody().readAllBytes();
		byte[] bytes = "{\"ok\":true}".getBytes(StandardCharsets.UTF_8);
		exchange.getResponseHeaders().set("Content-Type", "application/json");
		exchange.sendResponseHeaders(200, bytes.length);
		try (OutputStream out = exchange.getResponseBody()) {
			out.write(bytes);
		}
	}

	private HttpRequest request() {
		return HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + server.getAddress().getPort() + "/peer")).build();
	}

	@Test
	void theHostageTakesSoABareSendAsyncDoesNotComplete() throws Exception {
		CompletableFuture<HttpResponse<String>> bare = HttpClient.newHttpClient().sendAsync(request(),
			HttpResponse.BodyHandlers.ofString());
		try {
			Thread.sleep(1_000);
			assertThat(bare).as("the stub answers at once, but sendAsync's answer waits for a common-pool worker")
				.isNotDone();
		}
		finally {
			release.countDown();
		}
		assertThat(bare.get(5, TimeUnit.SECONDS).statusCode()).as("and arrives once the pool is free").isEqualTo(200);
	}

	@Test
	void anExchangeThroughDeadlineHttpAnswersPromptly() throws Exception {
		HttpClient client = HttpClient.newBuilder().version(HttpClient.Version.HTTP_1_1).build();
		long started = System.nanoTime();
		HttpResponse<String> response = http.send(client, request(), BufferedBody.ofString(), DEADLINE);
		assertThat(response.statusCode()).isEqualTo(200);
		assertThat(response.body()).isEqualTo("{\"ok\":true}");
		assertThat(Duration.ofNanos(System.nanoTime() - started)).isLessThan(PROMPT);
	}

	@Test
	void manyConcurrentExchangesAnswerPromptly() throws Exception {
		HttpClient client = HttpClient.newBuilder().version(HttpClient.Version.HTTP_1_1).build();
		int callers = 16;
		CountDownLatch done = new CountDownLatch(callers);
		long started = System.nanoTime();
		for (int i = 0; i < callers; i++) {
			Thread.ofPlatform().daemon().start(() -> {
				try {
					if (http.send(client, request(), BufferedBody.ofString(), DEADLINE).statusCode() == 200) {
						done.countDown();
					}
				}
				catch (Exception e) {
					Thread.currentThread().interrupt();
				}
			});
		}
		assertThat(done.await(DEADLINE.toMillis(), TimeUnit.MILLISECONDS)).isTrue();
		assertThat(Duration.ofNanos(System.nanoTime() - started)).isLessThan(PROMPT.multipliedBy(2));
	}
}
