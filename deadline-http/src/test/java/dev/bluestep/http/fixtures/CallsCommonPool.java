package dev.bluestep.http.fixtures;

import java.util.concurrent.ForkJoinPool;

/** A direct {@code ForkJoinPool.commonPool()} call. */
public final class CallsCommonPool {

	private CallsCommonPool() {
	}

	static ForkJoinPool violate() {
		return ForkJoinPool.commonPool();
	}
}
