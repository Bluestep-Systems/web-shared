package dev.bluestep.secretfiles;

import java.nio.file.Path;
import java.util.function.Consumer;

/**
 * A directory of secret files, one file per key, whose latest contents are always available and whose
 * changes can be subscribed to.
 *
 * <p>This is the seam a framework adapter builds on: a Spring service, for instance, reads
 * {@link #current()} once at startup to bind its properties and {@link #subscribe subscribes} to
 * rebind them when the mount changes.</p>
 */
public interface SecretDirectory extends AutoCloseable {

	/**
	 * The most recently published snapshot. Never blocks and never reads the disk.
	 *
	 * @return the latest snapshot; after {@link #close()}, the last one published before it
	 */
	SecretSnapshot current();

	/**
	 * Registers {@code listener} to receive every later snapshot whose values differ from the one
	 * before it. A reload that finds the same values notifies nobody.
	 *
	 * <p>The listener is NOT called with the present snapshot. Subscribe first and then read
	 * {@link #current()}: a change landing between the two is then seen at least once rather than
	 * missed.</p>
	 *
	 * <p>Listeners run one after another, in subscription order, on the directory's watcher thread,
	 * so a slow listener delays both the others and the next reload. One that throws is logged and
	 * skipped; the rest are still notified. The log names the exception's type and its causes' types
	 * ({@link ExceptionTypes}), never a message or stack trace, which could quote a value.</p>
	 *
	 * @param listener receives each changed snapshot
	 * @return a handle whose {@link Subscription#close()} stops further notifications
	 * @throws IllegalStateException if this directory is already closed
	 */
	Subscription subscribe(Consumer<SecretSnapshot> listener);

	/**
	 * The directory being read.
	 *
	 * @return the path given when this directory was opened
	 */
	Path path();

	/**
	 * Stops watching the directory. Idempotent. Later changes on disk are no longer read and no
	 * listener is called again.
	 */
	@Override
	void close();

	/**
	 * A registered listener. Closing it unregisters the listener; closing it again does nothing.
	 */
	interface Subscription extends AutoCloseable {

		/**
		 * Unregisters the listener. A delivery the watcher thread had already begun may still complete;
		 * no later snapshot reaches the listener.
		 */
		@Override
		void close();
	}
}
