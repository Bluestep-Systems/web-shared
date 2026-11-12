package dev.bluestep.http;

import java.io.IOException;
import java.net.Authenticator;
import java.net.CookieHandler;
import java.net.ProxySelector;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.concurrent.atomic.AtomicReference;

import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLParameters;

/**
 * A client that records the thread its blocking {@code send} ran on, then delegates, or throws {@code failure} instead.
 * Its {@code sendAsync} refuses: {@link ExecutorDeadlineHttp} must never reach it.
 */
final class RecordingHttpClient extends HttpClient {

	private final HttpClient delegate;
	private final AtomicReference<Thread> sender;
	private final Throwable failure;

	RecordingHttpClient(HttpClient delegate, AtomicReference<Thread> sender, Throwable failure) {
		this.delegate = delegate;
		this.sender = sender;
		this.failure = failure;
	}

	@Override
	public <T> HttpResponse<T> send(HttpRequest request, HttpResponse.BodyHandler<T> handler)
			throws IOException, InterruptedException {
		sender.set(Thread.currentThread());
		if (failure instanceof RuntimeException runtime) {
			throw runtime;
		}
		if (failure instanceof Error error) {
			throw error;
		}
		return delegate.send(request, handler);
	}

	@Override
	public <T> CompletableFuture<HttpResponse<T>> sendAsync(HttpRequest request, HttpResponse.BodyHandler<T> handler) {
		throw new AssertionError("sendAsync must never be called");
	}

	@Override
	public <T> CompletableFuture<HttpResponse<T>> sendAsync(HttpRequest request, HttpResponse.BodyHandler<T> handler,
			HttpResponse.PushPromiseHandler<T> pushPromiseHandler) {
		throw new AssertionError("sendAsync must never be called");
	}

	@Override
	public Optional<CookieHandler> cookieHandler() {
		return delegate.cookieHandler();
	}

	@Override
	public Optional<Duration> connectTimeout() {
		return delegate.connectTimeout();
	}

	@Override
	public Redirect followRedirects() {
		return delegate.followRedirects();
	}

	@Override
	public Optional<ProxySelector> proxy() {
		return delegate.proxy();
	}

	@Override
	public SSLContext sslContext() {
		return delegate.sslContext();
	}

	@Override
	public SSLParameters sslParameters() {
		return delegate.sslParameters();
	}

	@Override
	public Optional<Authenticator> authenticator() {
		return delegate.authenticator();
	}

	@Override
	public Version version() {
		return delegate.version();
	}

	@Override
	public Optional<Executor> executor() {
		return delegate.executor();
	}
}
