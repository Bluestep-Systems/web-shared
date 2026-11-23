/**
 * Outbound HTTP exchanges with a deadline on the whole exchange ({@link dev.bluestep.http.DeadlineHttp}), run on an
 * executor of the consumer's own and never on {@code ForkJoinPool.commonPool()}.
 *
 * <p>Null-marked: every type in this package is non-null unless explicitly annotated
 * {@link org.jspecify.annotations.Nullable}. NullAway runs with {@code OnlyNullMarked}, so this annotation is what
 * opts the package into analysis.</p>
 */
@NullMarked
package dev.bluestep.http;

import org.jspecify.annotations.NullMarked;
