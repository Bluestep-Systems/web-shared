package dev.bluestep.http.fixtures;

import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.concurrent.CompletableFuture;

/** A direct {@code HttpClient.sendAsync} call. */
public final class CallsSendAsync {

	private CallsSendAsync() {
	}

	static CompletableFuture<HttpResponse<Void>> violate(HttpClient client, HttpRequest request) {
		return client.sendAsync(request, HttpResponse.BodyHandlers.discarding());
	}
}
