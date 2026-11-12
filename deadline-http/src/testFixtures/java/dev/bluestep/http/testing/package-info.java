/**
 * Test fixtures of {@code dev.bluestep:deadline-http}: {@link dev.bluestep.http.testing.CommonPoolRules}, the ArchUnit
 * rules that keep a consumer's main code off {@code ForkJoinPool.commonPool()}. A consumer's tests take them with
 * {@code testImplementation(testFixtures('dev.bluestep:deadline-http:<version>'))}.
 *
 * <p>Null-marked: every type in this package is non-null unless explicitly annotated
 * {@link org.jspecify.annotations.Nullable}.</p>
 */
@NullMarked
package dev.bluestep.http.testing;

import org.jspecify.annotations.NullMarked;
