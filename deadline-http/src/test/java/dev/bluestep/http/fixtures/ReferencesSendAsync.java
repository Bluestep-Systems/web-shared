package dev.bluestep.http.fixtures;

import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.concurrent.CompletableFuture;
import java.util.function.BiFunction;

/** {@code HttpClient.sendAsync} as a method reference. */
public final class ReferencesSendAsync {

	private ReferencesSendAsync() {
	}

	static BiFunction<HttpRequest, HttpResponse.BodyHandler<Void>, CompletableFuture<HttpResponse<Void>>> violate(
			HttpClient client) {
		return client::sendAsync;
	}
}
