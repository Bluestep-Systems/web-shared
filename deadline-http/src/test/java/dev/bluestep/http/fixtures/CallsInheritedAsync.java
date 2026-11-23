package dev.bluestep.http.fixtures;

import java.util.concurrent.CompletableFuture;

/** An executor-less async method a {@code CompletableFuture} subclass inherits, called on the subclass. */
public final class CallsInheritedAsync {

	private CallsInheritedAsync() {
	}

	/** Inherits every async method; the call below compiles to an invoke on this owner, not on CompletableFuture. */
	static final class OwnFuture<T> extends CompletableFuture<T> {
	}

	static CompletableFuture<String> violate(OwnFuture<String> future) {
		return future.thenApplyAsync(value -> value);
	}
}
