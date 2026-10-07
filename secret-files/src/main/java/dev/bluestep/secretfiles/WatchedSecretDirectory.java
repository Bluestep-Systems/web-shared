package dev.bluestep.secretfiles;

import static java.nio.charset.StandardCharsets.UTF_8;
import static java.nio.file.StandardWatchEventKinds.ENTRY_CREATE;
import static java.nio.file.StandardWatchEventKinds.ENTRY_DELETE;
import static java.nio.file.StandardWatchEventKinds.ENTRY_MODIFY;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.lang.System.Logger.Level;
import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CodingErrorAction;
import java.nio.file.ClosedWatchServiceException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.WatchKey;
import java.nio.file.WatchService;
import java.time.Duration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.SortedSet;
import java.util.TreeSet;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;

/**
 * A {@link SecretDirectory} that follows a Kubernetes Secret volume as the kubelet updates it.
 *
 * <h2>What the kubelet does</h2>
 *
 * <p>A Secret volume is never rewritten in place. Its visible files are symlinks
 * {@code KEY -> ..data/KEY}; {@code ..data} is itself a symlink to a timestamped directory such as
 * {@code ..2026_10_07_16_00_00.123456789} holding the real files. An update writes a complete new
 * timestamped directory, points a temporary symlink at it, renames that over {@code ..data} — one
 * atomic step that switches every key at once — then adds visible links for new keys, removes those
 * for deleted keys, and deletes the old timestamped directory.</p>
 *
 * <h2>What this class does with that</h2>
 *
 * <ul>
 *   <li><b>One generation per read.</b> {@code ..data} is resolved once to its timestamped directory,
 *       every key is read from that directory, and the read is kept only if {@code ..data} still names
 *       it afterwards; otherwise it is discarded and done again ({@link PinnedGeneration}). A swap
 *       landing mid-read therefore never yields a snapshot holding keys from two versions. A directory
 *       with no {@code ..data} (a plain directory, as in local development) is read directly.</li>
 *   <li><b>Keys as Spring Boot's config tree sees them.</b> A key is an entry whose name does not
 *       start with {@code ..} and which resolves to a regular file ({@link PinnedGeneration#keys}): a
 *       single leading dot is part of the key ({@code .token}), while the kubelet's {@code ..data},
 *       {@code ..data_tmp} and timestamped directories are ignored, as is a dangling link. A
 *       subdirectory is refused: {@link #open} throws, and a later read that meets one fails.</li>
 *   <li><b>Two triggers.</b> A {@link WatchService} on the directory sees the swap as events on
 *       {@code ..data} (and an in-place rewrite of a plain file as a modify). A periodic poll
 *       ({@link #DEFAULT_POLL_INTERVAL} unless told otherwise) re-reads regardless, so a missed event
 *       costs at most one interval rather than the update.</li>
 *   <li><b>Bursts coalesce.</b> One swap is several events; the watcher waits for a short quiet
 *       period and then re-reads once.</li>
 *   <li><b>Only real changes notify.</b> Every trigger re-reads the whole directory, and listeners
 *       hear about the result only when its values differ from the published snapshot.</li>
 *   <li><b>A failed read is retried, never published.</b> A read that fails — {@code ..data} kept
 *       moving, or a plain directory's file vanished between listing and reading — is discarded whole
 *       and retried once shortly afterwards; if the retry fails too, the next event or poll tries
 *       again.</li>
 *   <li><b>Values never reach a log.</b> Logging names keys, the directory and exception types;
 *       never a value.</li>
 * </ul>
 *
 * <p>The watcher is a virtual thread, so it never keeps a JVM alive; {@link #close()} stops it.</p>
 */
public final class WatchedSecretDirectory implements SecretDirectory {

	/** How often the directory is re-read when no watch event has arrived to prompt it. */
	public static final Duration DEFAULT_POLL_INTERVAL = Duration.ofSeconds(30);

	/** An event burst is over once this long passes without another event. */
	private static final Duration QUIET_PERIOD = Duration.ofMillis(100);

	/** A directory that never goes quiet is still re-read after this long. */
	private static final Duration MAX_COALESCE = Duration.ofSeconds(2);

	/** How soon a failed read is retried. */
	private static final Duration RETRY_DELAY = Duration.ofMillis(250);

	/** How long {@link #close()} waits for the watcher thread to finish. */
	private static final Duration CLOSE_TIMEOUT = Duration.ofSeconds(5);

	private static final System.Logger LOG = System.getLogger(WatchedSecretDirectory.class.getName());

	private final Path dir;
	private final Duration pollInterval;
	private final Optional<WatchService> watchService;
	private final AtomicReference<SecretSnapshot> current;
	private final List<Registration> listeners = new CopyOnWriteArrayList<>();
	private final AtomicBoolean closed = new AtomicBoolean();
	private final AtomicLong reads = new AtomicLong();
	private final Thread watcher;

	private WatchedSecretDirectory(final Path dir, final Duration pollInterval,
			final Optional<WatchService> watchService, final SecretSnapshot initial) {
		this.dir = dir;
		this.pollInterval = pollInterval;
		this.watchService = watchService;
		this.current = new AtomicReference<>(initial);
		this.watcher = Thread.ofVirtual().name("secret-files-watcher " + dir).unstarted(this::watchLoop);
	}

	/**
	 * Reads {@code dir} and starts following it, re-reading at least every
	 * {@link #DEFAULT_POLL_INTERVAL}.
	 *
	 * @param dir the mounted secret directory, such as {@code /var/lib/bluestep/secrets}
	 * @return the open directory, already holding its first snapshot
	 * @throws IllegalArgumentException if {@code dir} does not exist, is not a directory, or holds a
	 *                                  subdirectory; a caller that runs both with and without the mount
	 *                                  checks for it first
	 * @throws UncheckedIOException     if the directory cannot be read or watched
	 */
	public static WatchedSecretDirectory open(final Path dir) {
		return open(dir, DEFAULT_POLL_INTERVAL);
	}

	/**
	 * Reads {@code dir} and starts following it, re-reading at least every {@code pollInterval}.
	 *
	 * @param dir          the mounted secret directory
	 * @param pollInterval the longest a change can go unnoticed if its watch event is missed; positive
	 * @return the open directory, already holding its first snapshot
	 * @throws IllegalArgumentException if {@code dir} does not exist, is not a directory or holds a
	 *                                  subdirectory, or {@code pollInterval} is not positive
	 * @throws UncheckedIOException     if the directory cannot be read or watched
	 */
	public static WatchedSecretDirectory open(final Path dir, final Duration pollInterval) {
		return open(dir, pollInterval, true);
	}

	/**
	 * Test seam: {@code watchEvents = false} leaves the poll as the only trigger, which is how the poll
	 * fallback is shown to work on its own.
	 */
	static WatchedSecretDirectory open(final Path dir, final Duration pollInterval, final boolean watchEvents) {
		Objects.requireNonNull(dir, "dir");
		Objects.requireNonNull(pollInterval, "pollInterval");
		if (!Files.exists(dir)) {
			throw new IllegalArgumentException("Secret directory " + dir + " does not exist");
		}
		if (!Files.isDirectory(dir)) {
			throw new IllegalArgumentException("Secret directory " + dir + " is not a directory");
		}
		if (pollInterval.isNegative() || pollInterval.isZero()) {
			throw new IllegalArgumentException("Poll interval must be positive, was " + pollInterval);
		}
		// Watch BEFORE the first read, so a swap landing between the two is an event rather than a gap.
		final Optional<WatchService> watchService = watchEvents ? Optional.of(watch(dir)) : Optional.empty();
		try {
			final WatchedSecretDirectory opened =
					new WatchedSecretDirectory(dir, pollInterval, watchService, readInitial(dir));
			opened.watcher.start();
			return opened;
		} catch (RuntimeException e) {
			watchService.ifPresent(WatchedSecretDirectory::closeQuietly);
			throw e;
		}
	}

	@Override
	public SecretSnapshot current() {
		return current.get();
	}

	@Override
	public Subscription subscribe(final Consumer<SecretSnapshot> listener) {
		Objects.requireNonNull(listener, "listener");
		if (closed.get()) {
			throw new IllegalStateException("Secret directory " + dir + " is closed");
		}
		final Registration registration = new Registration(listener);
		listeners.add(registration);
		return registration;
	}

	@Override
	public Path path() {
		return dir;
	}

	@Override
	public void close() {
		if (!closed.compareAndSet(false, true)) {
			return;
		}
		for (Registration registration : listeners) {
			registration.active = false;
		}
		listeners.clear();
		// Closing the service wakes a watcher blocked on it; the interrupt wakes one sleeping out a poll.
		watchService.ifPresent(WatchedSecretDirectory::closeQuietly);
		watcher.interrupt();
		if (Thread.currentThread() == watcher) {
			// Closed from a listener: the loop sees the flag as soon as that listener returns.
			return;
		}
		try {
			if (!watcher.join(CLOSE_TIMEOUT)) {
				LOG.log(Level.WARNING, "Watcher for secret directory {0} did not stop within {1}", dir,
						CLOSE_TIMEOUT);
			}
		} catch (InterruptedException e) {
			Thread.currentThread().interrupt();
		}
	}

	/**
	 * Test seam: how many reads of the directory have completed, so a test can wait for the watcher to
	 * have looked at a change before asserting that nobody was told about it.
	 */
	long reads() {
		return reads.get();
	}

	/** Test seam: whether the watcher thread is still running. */
	boolean isWatching() {
		return watcher.isAlive();
	}

	@Override
	public String toString() {
		return "WatchedSecretDirectory[" + dir + "]";
	}

	private void watchLoop() {
		final long pollNanos = pollInterval.toNanos();
		long nextPollAt = System.nanoTime() + pollNanos;
		// Set by a failed read: the next read is the retry, and it is due no later than retryAt.
		boolean retrying = false;
		long retryAt = 0;
		try {
			while (!closed.get()) {
				final long deadline = retrying && retryAt - nextPollAt < 0 ? retryAt : nextPollAt;
				final boolean eventSeen = awaitEvents(deadline - System.nanoTime());
				if (closed.get()) {
					return;
				}
				final long now = System.nanoTime();
				final boolean pollDue = now - nextPollAt >= 0;
				final boolean retryDue = retrying && now - retryAt >= 0;
				if (!eventSeen && !pollDue && !retryDue) {
					continue;
				}
				if (pollDue) {
					nextPollAt = now + pollNanos;
				}
				if (reload()) {
					retrying = false;
				} else if (retrying) {
					retrying = false;
					LOG.log(Level.WARNING, "Secret directory {0} could not be read twice running; keeping the "
							+ "current snapshot until the next change or poll", dir);
				} else {
					retrying = true;
					retryAt = System.nanoTime() + RETRY_DELAY.toNanos();
				}
			}
		} catch (InterruptedException | ClosedWatchServiceException e) {
			// close() — the only thing that interrupts this thread or closes its watch service.
		} catch (RuntimeException e) {
			LOG.log(Level.ERROR, "Watcher for secret directory " + dir + " stopped unexpectedly ("
					+ ExceptionTypes.of(e) + "); the current snapshot will no longer be updated");
		}
	}

	/**
	 * Waits up to {@code waitNanos} for a watch event and, if one comes, keeps draining until the burst
	 * it belongs to goes quiet.
	 *
	 * @return whether any event arrived
	 */
	private boolean awaitEvents(final long waitNanos) throws InterruptedException {
		if (watchService.isEmpty()) {
			if (waitNanos > 0) {
				TimeUnit.NANOSECONDS.sleep(waitNanos);
			}
			return false;
		}
		final WatchService service = watchService.get();
		final WatchKey first = waitNanos > 0 ? service.poll(waitNanos, TimeUnit.NANOSECONDS) : service.poll();
		if (first == null) {
			return false;
		}
		drain(first);
		final long giveUpAt = System.nanoTime() + MAX_COALESCE.toNanos();
		while (System.nanoTime() - giveUpAt < 0) {
			final WatchKey next = service.poll(QUIET_PERIOD.toNanos(), TimeUnit.NANOSECONDS);
			if (next == null) {
				break;
			}
			drain(next);
		}
		return true;
	}

	private void drain(final WatchKey key) {
		// The events themselves are not inspected: whatever changed, the answer is to re-read everything.
		key.pollEvents();
		if (!key.reset()) {
			LOG.log(Level.WARNING, "Watch on secret directory {0} is no longer valid; relying on the poll "
					+ "every {1}", dir, pollInterval);
		}
	}

	/**
	 * Reads the directory and publishes the result if it changed.
	 *
	 * @return false if the read failed and nothing was published
	 */
	private boolean reload() {
		final SecretSnapshot next;
		try {
			next = read(dir);
		} catch (IOException e) {
			LOG.log(Level.DEBUG, "Reading secret directory {0} failed ({1}); probably caught mid-swap", dir,
					e.getClass().getSimpleName());
			return false;
		} catch (IllegalArgumentException e) {
			// A subdirectory appeared since open() refused one; the message names the directory only.
			LOG.log(Level.WARNING, () -> e.getMessage() + " Keeping the current snapshot.");
			return false;
		}
		reads.incrementAndGet();
		publish(next);
		return true;
	}

	private void publish(final SecretSnapshot next) {
		final SecretSnapshot previous = current.get();
		if (next.equals(previous)) {
			return;
		}
		current.set(next);
		LOG.log(Level.INFO, () -> "Secrets in " + dir + " changed: " + describeChange(previous, next));
		for (Registration registration : listeners) {
			registration.deliver(next);
		}
	}

	private static String describeChange(final SecretSnapshot before, final SecretSnapshot after) {
		final SortedSet<String> added = new TreeSet<>(after.values().keySet());
		added.removeAll(before.values().keySet());
		final SortedSet<String> removed = new TreeSet<>(before.values().keySet());
		removed.removeAll(after.values().keySet());
		final SortedSet<String> changed = new TreeSet<>();
		for (Map.Entry<String, String> entry : after.values().entrySet()) {
			final Optional<String> old = before.get(entry.getKey());
			if (old.isPresent() && !old.get().equals(entry.getValue())) {
				changed.add(entry.getKey());
			}
		}
		return "added " + added + ", removed " + removed + ", changed " + changed;
	}

	private static SecretSnapshot readInitial(final Path dir) {
		try {
			return read(dir);
		} catch (IOException first) {
			// Opened mid-swap: the kubelet finishes in milliseconds, so one immediate retry is enough.
			try {
				return read(dir);
			} catch (IOException second) {
				second.addSuppressed(first);
				throw new UncheckedIOException("Could not read secret directory " + dir, second);
			}
		}
	}

	/**
	 * Reads every key of the generation {@code ..data} names, or of {@code dir} itself where there is no
	 * {@code ..data} (see {@link PinnedGeneration}).
	 *
	 * @throws IOException              if the listing fails, a listed file cannot be read while
	 *                                  {@code ..data} stayed put, or {@code ..data} kept moving; the
	 *                                  caller discards the whole read
	 * @throws IllegalArgumentException if the directory holds a subdirectory
	 */
	static SecretSnapshot read(final Path dir) throws IOException {
		return read(dir, key -> { });
	}

	/**
	 * Test seam: {@code afterEachKey} runs after each key's file is read, so a test can swap the volume
	 * between two reads.
	 */
	static SecretSnapshot read(final Path dir, final Consumer<String> afterEachKey) throws IOException {
		return PinnedGeneration.read(dir, generation -> {
			final Map<String, String> values = new HashMap<>();
			for (String key : PinnedGeneration.keys(generation)) {
				final byte[] bytes = Files.readAllBytes(generation.resolve(key));
				afterEachKey.accept(key);
				decode(dir, key, bytes).ifPresent(value -> values.put(key, value));
			}
			return new SecretSnapshot(values);
		});
	}

	/**
	 * Decodes strictly. A file that is not UTF-8 is left out — and said so, by key — rather than handed
	 * on with replacement characters standing in for the bytes a credential check would need.
	 */
	private static Optional<String> decode(final Path dir, final String key, final byte[] bytes) {
		try {
			return Optional.of(UTF_8.newDecoder()
					.onMalformedInput(CodingErrorAction.REPORT)
					.onUnmappableCharacter(CodingErrorAction.REPORT)
					.decode(ByteBuffer.wrap(bytes))
					.toString());
		} catch (CharacterCodingException e) {
			LOG.log(Level.WARNING, "Secret {0} in {1} is not valid UTF-8 and is left out of the snapshot", key,
					dir);
			return Optional.empty();
		}
	}

	private static WatchService watch(final Path dir) {
		final WatchService service;
		try {
			service = dir.getFileSystem().newWatchService();
		} catch (IOException e) {
			throw new UncheckedIOException("Could not watch secret directory " + dir, e);
		}
		try {
			dir.register(service, ENTRY_CREATE, ENTRY_DELETE, ENTRY_MODIFY);
			return service;
		} catch (IOException e) {
			closeQuietly(service);
			throw new UncheckedIOException("Could not watch secret directory " + dir, e);
		}
	}

	private static void closeQuietly(final WatchService service) {
		try {
			service.close();
		} catch (IOException e) {
			LOG.log(Level.DEBUG, () -> "Closing a secret directory watch failed (" + ExceptionTypes.of(e) + ")");
		}
	}

	/** One subscribed listener. */
	private final class Registration implements Subscription {

		private final Consumer<SecretSnapshot> listener;
		private volatile boolean active = true;

		Registration(final Consumer<SecretSnapshot> listener) {
			this.listener = listener;
		}

		void deliver(final SecretSnapshot snapshot) {
			if (!active) {
				return;
			}
			try {
				listener.accept(snapshot);
			} catch (Exception e) {
				final Set<String> keys = new TreeSet<>(snapshot.values().keySet());
				LOG.log(Level.WARNING, () -> "A listener on secret directory " + dir + " failed handling the "
						+ "update to keys " + keys + " (" + ExceptionTypes.of(e) + "); the other listeners and the "
						+ "watcher carry on");
			}
		}

		@Override
		public void close() {
			active = false;
			listeners.remove(this);
		}
	}
}
