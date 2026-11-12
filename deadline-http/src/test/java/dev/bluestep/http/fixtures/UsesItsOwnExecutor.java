package dev.bluestep.http.fixtures;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;

/** What the rules allow: async work on an executor the caller names. */
public final class UsesItsOwnExecutor {

	private UsesItsOwnExecutor() {
	}

	static CompletableFuture<String> allowed(Executor executor) {
		return CompletableFuture.supplyAsync(() -> "on the caller's executor", executor)
			.thenApplyAsync(value -> value, executor);
	}
}
