package dev.bluestep.secretfiles;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTimeoutPreemptively;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Map;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.locks.LockSupport;
import java.util.function.BooleanSupplier;
import java.util.logging.Handler;
import java.util.logging.LogRecord;
import java.util.logging.Logger;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import dev.bluestep.secretfiles.testing.KubeletSecretVolume;

/**
 * {@link WatchedSecretDirectory} against a directory laid out and updated exactly as the kubelet does it
 * ({@link KubeletSecretVolume}).
 *
 * <p>Every wait is bounded. A positive wait — a notification that should come — gives up after
 * {@link #TIMEOUT}, far longer than the watcher needs, so a slow machine is slow rather than red. The
 * "nothing should arrive" checks never rely on a sleep being long enough: they either wait for the
 * watcher's own read counter to move past the change, or follow the quiet change with a loud one and
 * assert the loud one is the first thing heard.</p>
 *
 * <p>Unless a test says otherwise, the poll interval is the 30-second default, so anything noticed
 * within {@link #TIMEOUT} was noticed through the watch service.</p>
 */
class WatchedSecretDirectoryTest {

	private static final Duration TIMEOUT = Duration.ofSeconds(10);

	@TempDir
	Path tmp;

	@Test
	void theInitialSnapshotReadsTheKeysOfTheCurrentGeneration() throws IOException {
		final KubeletSecretVolume volume = KubeletSecretVolume.create(tmp,
				Map.of("DB_PASSWORD", "pw", "API_TOKEN", "tok", ".token", "dot"));
		// Not keys: the generation is read, not the visible links, so a link left dangling by a removed key
		// (what one looks like for the instant before the kubelet deletes it) is never even seen.
		Files.createSymbolicLink(tmp.resolve("GHOST"), Path.of("..data", "GHOST"));

		try (WatchedSecretDirectory dir = WatchedSecretDirectory.open(volume.root())) {
			assertEquals(Map.of("DB_PASSWORD", "pw", "API_TOKEN", "tok", ".token", "dot"), dir.current().values(),
					"a single-dot key is a key, as it is to Boot's config tree; ..data and the generation are not");
			assertEquals(tmp, dir.path());
		}
	}

	@Test
	void aPlainDirectoryIsReadDirectlyWithBootsKeyRule() throws IOException {
		Files.writeString(tmp.resolve("KEY"), "k");
		Files.writeString(tmp.resolve(".token"), "dot");
		Files.writeString(tmp.resolve("..hidden"), "kubelet-style internal, not a key");
		Files.createSymbolicLink(tmp.resolve("GHOST"), tmp.resolve("absent"));

		try (WatchedSecretDirectory dir = WatchedSecretDirectory.open(tmp)) {
			assertEquals(Map.of("KEY", "k", ".token", "dot"), dir.current().values());
		}
	}

	@Test
	void aSubdirectoryIsRefusedAtOpen() throws IOException {
		Files.writeString(tmp.resolve("KEY"), "k");
		Files.createDirectory(tmp.resolve("nested"));
		Files.writeString(tmp.resolve("nested").resolve("inner"), "Boot would read this as nested.inner");

		final IllegalArgumentException refused =
				assertThrows(IllegalArgumentException.class, () -> WatchedSecretDirectory.open(tmp));
		assertTrue(refused.getMessage().contains(tmp.toString()), refused.getMessage());
		assertTrue(refused.getMessage().contains("nested"), refused.getMessage());
		assertTrue(refused.getMessage().contains("files only"), refused.getMessage());
	}

	@Test
	void aSubdirectoryAppearingLaterDoesNotStopTheWatcher() throws Exception {
		Files.writeString(tmp.resolve("TOKEN"), "v1");
		try (WatchedSecretDirectory dir = WatchedSecretDirectory.open(tmp, Duration.ofMillis(200))) {
			final BlockingQueue<SecretSnapshot> heard = recorder(dir);
			final Path nested = Files.createDirectory(tmp.resolve("nested"));
			Files.writeString(tmp.resolve("TOKEN"), "v2");
			// Several polls refuse the directory; none may publish, and the watcher must survive them.
			Thread.sleep(1000);
			assertNull(heard.poll(), "a read through the subdirectory was published");
			assertTrue(dir.isWatching(), "the refused read stopped the watcher");

			Files.delete(nested);
			SecretSnapshot latest = next(heard);
			while (!latest.get("TOKEN").equals(Optional.of("v2"))) {
				latest = next(heard);
			}
		}
	}

	@Test
	void aRotationOfASingleDotKeyIsNotified() throws Exception {
		final KubeletSecretVolume volume = KubeletSecretVolume.create(tmp, Map.of("KEY", "k", ".token", "one"));
		try (WatchedSecretDirectory dir = WatchedSecretDirectory.open(tmp)) {
			final BlockingQueue<SecretSnapshot> heard = recorder(dir);
			volume.swap(Map.of("KEY", "k", ".token", "two"));
			assertEquals(Optional.of("two"), next(heard).get(".token"));
		}
	}

	@Test
	void aSwapBetweenTwoReadsNeverYieldsAMixedSnapshot() throws IOException {
		final KubeletSecretVolume volume = KubeletSecretVolume.create(tmp, Map.of("A", "old", "B", "old"));
		final AtomicInteger swaps = new AtomicInteger();
		// The kubelet's rename lands after the first key was read; the old generation stays on disk, so a
		// read pinned to it would still succeed — and must still be discarded, since ..data moved.
		final SecretSnapshot read = WatchedSecretDirectory.read(tmp, key -> {
			if (swaps.getAndIncrement() == 0) {
				try {
					volume.beginSwap(Map.of("A", "new", "B", "new"));
				} catch (IOException e) {
					throw new UncheckedIOException(e);
				}
			}
		});
		assertEquals(Map.of("A", "new", "B", "new"), read.values(),
				"one generation, and the one ..data names once the read is done");
	}

	@Test
	void aSwapThatDeletesThePinnedGenerationMidReadIsRetried() throws IOException {
		final KubeletSecretVolume volume = KubeletSecretVolume.create(tmp, Map.of("A", "old", "B", "old"));
		final AtomicInteger swaps = new AtomicInteger();
		// The whole update lands after the first key was read: the generation being read is deleted.
		final SecretSnapshot read = WatchedSecretDirectory.read(tmp, key -> {
			if (swaps.getAndIncrement() == 0) {
				try {
					volume.swap(Map.of("A", "new", "B", "new"));
				} catch (IOException e) {
					throw new UncheckedIOException(e);
				}
			}
		});
		assertEquals(Map.of("A", "new", "B", "new"), read.values());
	}

	@Test
	void aReadThatDataKeepsMovingUnderGivesUpAfterBoundedAttempts() throws IOException {
		final KubeletSecretVolume volume = KubeletSecretVolume.create(tmp, Map.of("A", "0", "B", "0"));
		final AtomicInteger swaps = new AtomicInteger();
		final IOException refused = assertTimeoutPreemptively(TIMEOUT, () -> assertThrows(IOException.class,
				() -> WatchedSecretDirectory.read(tmp, key -> {
					try {
						volume.swap(Map.of("A", String.valueOf(swaps.incrementAndGet()), "B", "x"));
					} catch (IOException e) {
						throw new UncheckedIOException(e);
					}
				})));
		assertTrue(refused.getMessage().contains("moved during each of " + PinnedGeneration.MAX_ATTEMPTS),
				refused.getMessage());
	}

	@Test
	void valuesAreTheExactFileContents() throws IOException {
		KubeletSecretVolume.create(tmp, Map.of("PADDED", "  s3cret \n", "EMPTY", ""));
		try (WatchedSecretDirectory dir = WatchedSecretDirectory.open(tmp)) {
			assertEquals(Optional.of("  s3cret \n"), dir.current().get("PADDED"));
			assertEquals(Optional.of(""), dir.current().get("EMPTY"));
		}
	}

	@Test
	void aSwapWithAChangedValueNotifiesOnceWithTheNewSnapshot() throws Exception {
		final KubeletSecretVolume volume = KubeletSecretVolume.create(tmp, Map.of("DB_PASSWORD", "one"));
		try (WatchedSecretDirectory dir = WatchedSecretDirectory.open(tmp)) {
			final BlockingQueue<SecretSnapshot> heard = recorder(dir);

			volume.swap(Map.of("DB_PASSWORD", "two"));
			final SecretSnapshot second = next(heard);
			assertEquals(Map.of("DB_PASSWORD", "two"), second.values());
			assertSame(second, dir.current());

			// Once: the next thing heard is the NEXT change, not a repeat of the last one.
			volume.swap(Map.of("DB_PASSWORD", "three"));
			assertEquals(Map.of("DB_PASSWORD", "three"), next(heard).values());
			awaitReadsSettled(dir);
			assertNull(heard.poll(), "a single swap was reported more than once");
		}
	}

	@Test
	void aSwapWithIdenticalValuesDoesNotNotify() throws Exception {
		final KubeletSecretVolume volume = KubeletSecretVolume.create(tmp, Map.of("DB_PASSWORD", "same"));
		try (WatchedSecretDirectory dir = WatchedSecretDirectory.open(tmp)) {
			final BlockingQueue<SecretSnapshot> heard = recorder(dir);
			final SecretSnapshot before = dir.current();

			final long readsBefore = dir.reads();
			volume.swap(Map.of("DB_PASSWORD", "same"));
			// The watcher has now looked at the identical swap; had it notified, that came first.
			await(() -> dir.reads() > readsBefore, "the watcher never re-read after the identical swap");

			volume.swap(Map.of("DB_PASSWORD", "different"));
			assertEquals(Map.of("DB_PASSWORD", "different"), next(heard).values(),
					"the first notification must be the real change, not the identical swap");
			assertNotEquals(before, dir.current());
		}
	}

	@Test
	void addedAndRemovedKeysAreReflected() throws Exception {
		final KubeletSecretVolume volume = KubeletSecretVolume.create(tmp, Map.of("KEEP", "k", "DROP", "d"));
		try (WatchedSecretDirectory dir = WatchedSecretDirectory.open(tmp)) {
			final BlockingQueue<SecretSnapshot> heard = recorder(dir);

			volume.swap(Map.of("KEEP", "k", "ADDED", "a"));
			final SecretSnapshot after = next(heard);
			assertEquals(Map.of("KEEP", "k", "ADDED", "a"), after.values());
			assertEquals(Optional.empty(), dir.current().get("DROP"));
		}
	}

	@Test
	void aThrowingListenerDoesNotStopTheOthersOrTheWatcher() throws Exception {
		final KubeletSecretVolume volume = KubeletSecretVolume.create(tmp, Map.of("TOKEN", "v1"));
		try (WatchedSecretDirectory dir = WatchedSecretDirectory.open(tmp)) {
			final AtomicInteger throwerCalls = new AtomicInteger();
			// Subscribed FIRST, so it runs before the healthy listener on every delivery.
			dir.subscribe(snapshot -> {
				throwerCalls.incrementAndGet();
				// Quotes the value, as a careless listener's exception (or a binder's) can.
				throw new IllegalStateException("listener failure under test, value "
						+ snapshot.get("TOKEN").orElse(""), new IllegalArgumentException("cause quoting v2-secret"));
			});
			final BlockingQueue<SecretSnapshot> heard = recorder(dir);
			final List<LogRecord> logged = new CopyOnWriteArrayList<>();
			final Handler capture = new Handler() {
				@Override
				public void publish(final LogRecord record) {
					logged.add(record);
				}

				@Override
				public void flush() {
				}

				@Override
				public void close() {
				}
			};
			final Logger jul = Logger.getLogger(WatchedSecretDirectory.class.getName());
			jul.addHandler(capture);
			try {
				volume.swap(Map.of("TOKEN", "v2-secret"));
				assertEquals(Optional.of("v2-secret"), next(heard).get("TOKEN"));

				// The watcher survived the exception: a later change is still read and delivered to both.
				volume.swap(Map.of("TOKEN", "v3"));
				assertEquals(Optional.of("v3"), next(heard).get("TOKEN"));
				assertEquals(2, throwerCalls.get());
				assertTrue(dir.isWatching());
			} finally {
				jul.removeHandler(capture);
			}
			final List<LogRecord> failures = logged.stream()
					.filter(record -> String.valueOf(record.getMessage()).contains("failed handling")).toList();
			assertEquals(2, failures.size(), "each failure is logged");
			for (LogRecord record : failures) {
				assertNull(record.getThrown(), "no stack trace: it prints every cause's message");
				assertTrue(record.getMessage().contains(
						"java.lang.IllegalStateException <- java.lang.IllegalArgumentException"), record.getMessage());
				assertFalse(record.getMessage().contains("v2-secret"), "the value reached the log");
				assertFalse(record.getMessage().contains("listener failure under test"), "the message reached the log");
			}
		}
	}

	@Test
	void closingASubscriptionStopsItsNotifications() throws Exception {
		final KubeletSecretVolume volume = KubeletSecretVolume.create(tmp, Map.of("TOKEN", "v1"));
		try (WatchedSecretDirectory dir = WatchedSecretDirectory.open(tmp)) {
			final BlockingQueue<SecretSnapshot> closedOne = new LinkedBlockingQueue<>();
			final SecretDirectory.Subscription subscription = dir.subscribe(closedOne::add);
			// Subscribed after, so it is delivered to after: once it has heard, the closed one would have.
			final BlockingQueue<SecretSnapshot> openOne = recorder(dir);

			subscription.close();
			subscription.close();
			volume.swap(Map.of("TOKEN", "v2"));

			assertEquals(Optional.of("v2"), next(openOne).get("TOKEN"));
			assertNull(closedOne.poll(), "a closed subscription was still notified");
		}
	}

	@Test
	void closeStopsTheWatcherThread() throws Exception {
		final KubeletSecretVolume volume = KubeletSecretVolume.create(tmp, Map.of("TOKEN", "v1"));
		final WatchedSecretDirectory dir = WatchedSecretDirectory.open(tmp);
		assertTrue(dir.isWatching());

		dir.close();
		assertFalse(dir.isWatching(), "close() returned with the watcher thread still running");
		dir.close();

		assertThrows(IllegalStateException.class, () -> dir.subscribe(snapshot -> { }));
		volume.swap(Map.of("TOKEN", "v2"));
		assertEquals(Optional.of("v1"), dir.current().get("TOKEN"), "a closed directory kept reading");
	}

	@Test
	void closingFromInsideAListenerStopsTheWatcher() throws Exception {
		final KubeletSecretVolume volume = KubeletSecretVolume.create(tmp, Map.of("TOKEN", "v1"));
		final WatchedSecretDirectory dir = WatchedSecretDirectory.open(tmp);
		try {
			dir.subscribe(snapshot -> dir.close());
			volume.swap(Map.of("TOKEN", "v2"));
			await(() -> !dir.isWatching(), "closing from the watcher thread did not stop it");
		} finally {
			dir.close();
		}
	}

	@Test
	void thePollAloneDetectsAChange() throws Exception {
		final KubeletSecretVolume volume = KubeletSecretVolume.create(tmp, Map.of("TOKEN", "v1"));
		// No watch service at all: the poll is the only way this change can be seen.
		try (WatchedSecretDirectory dir = WatchedSecretDirectory.open(tmp, Duration.ofMillis(200), false)) {
			final BlockingQueue<SecretSnapshot> heard = recorder(dir);
			volume.swap(Map.of("TOKEN", "v2"));
			assertEquals(Optional.of("v2"), next(heard).get("TOKEN"));
		}
	}

	@Test
	void anInPlaceRewriteOfAPlainFileIsDetected() throws Exception {
		// Not every mount is a kubelet one: a plain directory rewritten in place (local development, a
		// sidecar) must be followed too, through the watch service's modify events.
		Files.writeString(tmp.resolve("TOKEN"), "v1");
		try (WatchedSecretDirectory dir = WatchedSecretDirectory.open(tmp)) {
			final BlockingQueue<SecretSnapshot> heard = recorder(dir);
			Files.writeString(tmp.resolve("TOKEN"), "v2");
			// An in-place write is not atomic, so a read may land mid-write; what must arrive is the end state.
			SecretSnapshot latest = next(heard);
			while (!latest.get("TOKEN").equals(Optional.of("v2"))) {
				latest = next(heard);
			}
			assertEquals(Map.of("TOKEN", "v2"), dir.current().values());
		}
	}

	@Test
	void aFileThatIsNotUtf8IsLeftOut() throws IOException {
		Files.write(tmp.resolve("BINARY"), new byte[] {(byte) 0xff, (byte) 0xfe, 0x00});
		Files.writeString(tmp.resolve("TEXT"), "fine");
		try (WatchedSecretDirectory dir = WatchedSecretDirectory.open(tmp)) {
			assertEquals(Map.of("TEXT", "fine"), dir.current().values());
		}
	}

	@Test
	void openRejectsAMissingDirectory() {
		final IllegalArgumentException refused = assertThrows(IllegalArgumentException.class,
				() -> WatchedSecretDirectory.open(tmp.resolve("absent")));
		assertTrue(refused.getMessage().contains("does not exist"), refused.getMessage());
	}

	@Test
	void openRejectsARegularFile() throws IOException {
		final Path file = Files.writeString(tmp.resolve("file"), "x");
		final IllegalArgumentException refused =
				assertThrows(IllegalArgumentException.class, () -> WatchedSecretDirectory.open(file));
		assertTrue(refused.getMessage().contains("not a directory"), refused.getMessage());
	}

	@Test
	void openRejectsANonPositivePollInterval() {
		assertThrows(IllegalArgumentException.class, () -> WatchedSecretDirectory.open(tmp, Duration.ZERO));
		assertThrows(IllegalArgumentException.class, () -> WatchedSecretDirectory.open(tmp, Duration.ofSeconds(-1)));
	}

	private static BlockingQueue<SecretSnapshot> recorder(final SecretDirectory dir) {
		final BlockingQueue<SecretSnapshot> heard = new LinkedBlockingQueue<>();
		dir.subscribe(heard::add);
		return heard;
	}

	private static SecretSnapshot next(final BlockingQueue<SecretSnapshot> heard) throws InterruptedException {
		final SecretSnapshot snapshot = heard.poll(TIMEOUT.toMillis(), TimeUnit.MILLISECONDS);
		assertNotNull(snapshot, "no notification within " + TIMEOUT);
		return snapshot;
	}

	/**
	 * Waits until the watcher has gone a whole coalescing window without reading again — so every event
	 * the last change produced has been acted on, and anything it was going to deliver has been.
	 */
	private static void awaitReadsSettled(final WatchedSecretDirectory dir) {
		final long deadline = System.nanoTime() + TIMEOUT.toNanos();
		long seen = dir.reads();
		long stableSince = System.nanoTime();
		while (System.nanoTime() - stableSince < Duration.ofMillis(500).toNanos()) {
			if (System.nanoTime() - deadline > 0) {
				fail("the watcher kept re-reading for " + TIMEOUT);
			}
			LockSupport.parkNanos(Duration.ofMillis(10).toNanos());
			final long now = dir.reads();
			if (now != seen) {
				seen = now;
				stableSince = System.nanoTime();
			}
		}
	}

	private static void await(final BooleanSupplier condition, final String failure) {
		final long deadline = System.nanoTime() + TIMEOUT.toNanos();
		while (!condition.getAsBoolean()) {
			if (System.nanoTime() - deadline > 0) {
				fail(failure + " (waited " + TIMEOUT + ")");
			}
			LockSupport.parkNanos(Duration.ofMillis(10).toNanos());
		}
	}
}
