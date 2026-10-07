/**
 * Secrets read from a mounted directory of files — one file per key — and followed live as the
 * directory changes.
 *
 * <p>Built for a Kubernetes Secret volume, which the kubelet updates with an atomic symlink swap
 * rather than by rewriting files: see {@link dev.bluestep.secretfiles.WatchedSecretDirectory}.
 * {@link dev.bluestep.secretfiles.SecretFiles} is the one-shot lookup for code that runs without a
 * framework. Framework free and dependency free; the Spring Boot adapter lives beside it in
 * {@code dev.bluestep.secretfiles.spring}, compiled against Spring but not depending on it.</p>
 *
 * <p>Null-marked: every type here is non-null unless explicitly annotated
 * {@link org.jspecify.annotations.Nullable}.</p>
 */
@NullMarked
package dev.bluestep.secretfiles;

import org.jspecify.annotations.NullMarked;
