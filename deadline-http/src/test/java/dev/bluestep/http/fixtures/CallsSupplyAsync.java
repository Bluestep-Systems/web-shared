package dev.bluestep.http.fixtures;

import java.util.concurrent.CompletableFuture;

/** A direct, executor-less {@code CompletableFuture.supplyAsync} call. */
public final class CallsSupplyAsync {

	private CallsSupplyAsync() {
	}

	static CompletableFuture<String> violate() {
		return CompletableFuture.supplyAsync(() -> "on the common pool");
	}
}
