package dev.bluestep.secretfiles.spring;

import java.nio.file.Path;

/**
 * Published by {@link ConfigTreeSecretsReloader} once a mounted secret directory has changed and its
 * config-tree property source has been replaced, so {@code Environment#getProperty} already answers
 * with the new values.
 *
 * <p>Carries no values, on purpose: a listener re-reads the properties it owns from the
 * {@code Environment}, so the usual precedence still applies (an environment variable or command-line
 * argument outranks the file of the same name), and no secret travels through the event system, where
 * a debugging listener could print it.</p>
 *
 * <p>Listen with {@code @EventListener}. Delivery is synchronous, on the directory's watcher thread (or
 * the starting thread, for the catch-up at startup), one listener after another, so keep listeners
 * quick. Each {@code @EventListener} method whose declared event is exactly this type is isolated by
 * {@link SecretsReloadedListenerFactory}: one that throws is logged and the rest are still told. A
 * listener registered some other way — an {@code ApplicationListener} for
 * {@code PayloadApplicationEvent}, or an {@code @EventListener} on a supertype such as {@code Object} —
 * gets Spring's default delivery, where a throw stops the listeners after it.</p>
 *
 * @param directory the mounted directory whose contents changed, as the config-tree import names it
 */
public record SecretsReloadedEvent(Path directory) {
}
