/**
 * Test fixtures for code that reads a Kubernetes Secret volume: {@link
 * dev.bluestep.secretfiles.testing.KubeletSecretVolume} lays a directory out, and updates it, the way
 * the kubelet does.
 *
 * <p>Published as the test-fixtures variant of {@code dev.bluestep:secret-files}; a consumer's tests
 * depend on it with {@code testImplementation(testFixtures('dev.bluestep:secret-files:<version>'))}.</p>
 *
 * <p>Null-marked: every type here is non-null unless explicitly annotated
 * {@link org.jspecify.annotations.Nullable}.</p>
 */
@NullMarked
package dev.bluestep.secretfiles.testing;

import org.jspecify.annotations.NullMarked;
