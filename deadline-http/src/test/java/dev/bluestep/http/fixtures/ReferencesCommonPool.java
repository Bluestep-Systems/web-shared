package dev.bluestep.http.fixtures;

import java.util.concurrent.ForkJoinPool;
import java.util.function.Supplier;

/** {@code ForkJoinPool.commonPool} as a method reference. */
public final class ReferencesCommonPool {

	private ReferencesCommonPool() {
	}

	static Supplier<ForkJoinPool> violate() {
		return ForkJoinPool::commonPool;
	}
}
