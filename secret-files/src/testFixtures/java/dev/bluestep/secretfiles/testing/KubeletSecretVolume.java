package dev.bluestep.secretfiles.testing;

import static java.nio.charset.StandardCharsets.UTF_8;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Comparator;
import java.util.HashSet;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Stream;

/**
 * A directory laid out, and updated, the way the kubelet's atomic writer lays out and updates a Secret
 * volume — so the code under test meets the real sequence of symlinks and renames, not a simplification
 * of it.
 *
 * <pre>
 * KEY        -&gt; ..data/KEY
 * ..data     -&gt; ..2026_10_07_16_00_00.000000001
 * ..2026_10_07_16_00_00.000000001/KEY   (the real file)
 * </pre>
 *
 * <p>{@link #swap} replays an update in the kubelet's order: write a complete new timestamped
 * directory, point {@code ..data_tmp} at it, rename that over {@code ..data} atomically, add visible
 * links for new keys, remove visible links for dropped keys, delete the old timestamped directory.</p>
 *
 * <p>{@link #beginSwap} stops that sequence just after the rename, leaving the update half done — the
 * window in which a link for a dropped key dangles — and {@link PendingSwap#complete()} finishes it. That
 * is how a test holds a reader inside the window the kubelet normally closes in milliseconds.</p>
 *
 * <p>Not thread-safe: one test thread drives a volume.</p>
 */
public final class KubeletSecretVolume {

	private static final String DATA = "..data";
	private static final String DATA_TMP = "..data_tmp";

	private final Path root;
	private int generation;
	private Set<String> visible = Set.of();
	private boolean pending;

	private KubeletSecretVolume(final Path root) {
		this.root = root;
	}

	/**
	 * Lays out a volume holding {@code initial} in {@code root}.
	 *
	 * @param root    an existing, empty directory, typically a JUnit {@code @TempDir}
	 * @param initial every key mapped to its file's contents, written exactly (UTF-8, nothing added)
	 * @return the volume
	 * @throws IOException if the directory cannot be written
	 */
	public static KubeletSecretVolume create(final Path root, final Map<String, String> initial) throws IOException {
		final KubeletSecretVolume volume = new KubeletSecretVolume(root);
		volume.swap(initial);
		return volume;
	}

	/**
	 * The volume's directory: the path a consumer mounts, imports or opens.
	 *
	 * @return the directory given to {@link #create}
	 */
	public Path root() {
		return root;
	}

	/**
	 * Replaces the volume's whole contents with {@code next}, exactly as a Secret update would.
	 *
	 * @param next every key the Secret now holds, mapped to its contents
	 * @throws IOException if the directory cannot be written
	 * @throws IllegalStateException if a {@link #beginSwap begun} swap has not been completed
	 */
	public void swap(final Map<String, String> next) throws IOException {
		beginSwap(next).complete();
	}

	/**
	 * Performs an update up to and including the atomic {@code ..data} rename and the links for added
	 * keys, and stops there: links for dropped keys still exist and now dangle, and the previous
	 * timestamped directory is still on disk.
	 *
	 * @param next every key the Secret now holds, mapped to its contents
	 * @return the half-done update, which {@link PendingSwap#complete()} finishes
	 * @throws IOException if the directory cannot be written
	 * @throws IllegalStateException if an earlier begun swap has not been completed
	 */
	public PendingSwap beginSwap(final Map<String, String> next) throws IOException {
		if (pending) {
			throw new IllegalStateException("The previous swap of " + root + " has not been completed");
		}
		final Path data = root.resolve(DATA);
		final Optional<Path> previous = Files.isSymbolicLink(data)
				? Optional.of(root.resolve(Files.readSymbolicLink(data)))
				: Optional.empty();

		generation++;
		final String stamp = String.format("..2026_10_07_16_00_%02d.%09d", generation % 60, generation);
		final Path stamped = Files.createDirectory(root.resolve(stamp));
		for (Map.Entry<String, String> entry : next.entrySet()) {
			Files.writeString(stamped.resolve(entry.getKey()), entry.getValue(), UTF_8);
		}

		final Path tmp = root.resolve(DATA_TMP);
		Files.createSymbolicLink(tmp, Path.of(stamp));
		Files.move(tmp, data, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);

		for (String key : next.keySet()) {
			if (!visible.contains(key)) {
				Files.createSymbolicLink(root.resolve(key), Path.of(DATA, key));
			}
		}
		pending = true;
		return new PendingSwap(Set.copyOf(next.keySet()), previous);
	}

	/**
	 * An update stopped just after its {@code ..data} rename; see {@link #beginSwap}.
	 */
	public final class PendingSwap {

		private final Set<String> keys;
		private final Optional<Path> previous;

		private PendingSwap(final Set<String> keys, final Optional<Path> previous) {
			this.keys = keys;
			this.previous = previous;
		}

		/**
		 * Finishes the update: removes the links for dropped keys and deletes the previous timestamped
		 * directory.
		 *
		 * @throws IOException if the directory cannot be written
		 * @throws IllegalStateException if this swap was already completed
		 */
		public void complete() throws IOException {
			if (!pending) {
				throw new IllegalStateException("This swap of " + root + " was already completed");
			}
			final Set<String> dropped = new HashSet<>(visible);
			dropped.removeAll(keys);
			for (String key : dropped) {
				Files.delete(root.resolve(key));
			}
			visible = keys;
			if (previous.isPresent()) {
				deleteTree(previous.get());
			}
			pending = false;
		}
	}

	private static void deleteTree(final Path dir) throws IOException {
		try (Stream<Path> walk = Files.walk(dir)) {
			for (Path path : walk.sorted(Comparator.reverseOrder()).toList()) {
				Files.delete(path);
			}
		}
	}
}
