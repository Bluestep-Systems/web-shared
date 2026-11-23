package dev.bluestep.http.fixtures;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;

/** {@code CompletableFuture.defaultExecutor()}, the common pool by another name. */
public final class CallsDefaultExecutor {

	private CallsDefaultExecutor() {
	}

	static Executor violate(CompletableFuture<?> future) {
		return future.defaultExecutor();
	}
}
