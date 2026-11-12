package dev.bluestep.http.fixtures;

import java.util.concurrent.CompletableFuture;
import java.util.function.Function;
import java.util.function.Supplier;

/** The executor-less {@code CompletableFuture.supplyAsync} as a method reference. */
public final class ReferencesSupplyAsync {

	private ReferencesSupplyAsync() {
	}

	static Function<Supplier<String>, CompletableFuture<String>> violate() {
		return CompletableFuture::supplyAsync;
	}
}
