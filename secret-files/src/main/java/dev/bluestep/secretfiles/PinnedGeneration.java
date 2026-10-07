package dev.bluestep.secretfiles;

import java.io.IOException;
import java.nio.file.DirectoryIteratorException;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.util.Collections;
import java.util.Objects;
import java.util.Optional;
import java.util.SortedSet;
import java.util.TreeSet;

/**
 * Reads a secret directory at exactly one kubelet generation, so a read can never mix the values of
 * two Secret versions.
 *
 * <p>The kubelet switches a Secret volume from one version to the next by renaming a symlink,
 * {@code ..data}, from one timestamped directory to another (see {@link WatchedSecretDirectory}).
 * Reading key after key through the live {@code ..data} link lets that rename land between two reads,
 * and the result then holds some keys from each version. {@link #read} instead resolves {@code ..data}
 * once to the timestamped directory it names, hands that directory to the reader, and afterwards checks
 * that {@code ..data} still names it. If it moved, the read is discarded and done again, up to
 * {@value #MAX_ATTEMPTS} times. A directory with no {@code ..data} link (a plain directory, as in local
 * development) is handed to the reader as it is.</p>
 *
 * <p>{@link #keys} is the one rule for which entries of a directory are keys, shared by every reader
 * in this library.</p>
 */
public final class PinnedGeneration {

	/** The kubelet's link to the timestamped directory holding the Secret's current version. */
	public static final String DATA_LINK = "..data";

	/** How many times a read is attempted while {@code ..data} keeps moving under it. */
	public static final int MAX_ATTEMPTS = 5;

	private PinnedGeneration() {
	}

	/**
	 * Reads one generation of a secret directory.
	 *
	 * @param <T>       what the reader produces
	 * @param directory the mounted secret directory
	 * @param reader    reads everything it needs from the directory it is given: the timestamped
	 *                  directory {@code ..data} names, or {@code directory} itself where there is no
	 *                  {@code ..data}. It must capture what it reads, since the kubelet deletes a
	 *                  timestamped directory once it is superseded.
	 * @return what the reader produced from a generation that was still current when it finished
	 * @throws IOException if {@code ..data} cannot be resolved, moved during every one of
	 *                     {@value #MAX_ATTEMPTS} attempts, or the reader failed while it had not moved
	 */
	public static <T> T read(final Path directory, final GenerationReader<T> reader) throws IOException {
		Objects.requireNonNull(directory, "directory");
		Objects.requireNonNull(reader, "reader");
		for (int attempt = 1; attempt <= MAX_ATTEMPTS; attempt++) {
			final Optional<Path> pinned = generation(directory);
			if (pinned.isEmpty()) {
				return reader.read(directory);
			}
			final T result;
			try {
				result = reader.read(pinned.get());
			} catch (IOException | RuntimeException e) {
				// A superseded generation is deleted; failing on it is the swap, not a fault.
				if (pinned.equals(generation(directory))) {
					throw e;
				}
				continue;
			}
			if (pinned.equals(generation(directory))) {
				return result;
			}
		}
		throw new IOException(DATA_LINK + " in secret directory " + directory + " moved during each of "
				+ MAX_ATTEMPTS + " reads");
	}

	/**
	 * The keys in one directory, by the rule every reader here shares: an entry whose name does not
	 * start with {@code ..} and which resolves, following symlinks, to a regular file. A single leading
	 * dot is part of a key ({@code .token} is a key, as it is to Spring Boot's config tree); the
	 * kubelet's own entries ({@code ..data}, {@code ..data_tmp}, the timestamped directories) all start
	 * with two. A link that dangles is not a key.
	 *
	 * @param directory a timestamped generation directory, or a plain secret directory
	 * @return the key names, sorted
	 * @throws IOException              if the directory cannot be listed
	 * @throws IllegalArgumentException if the directory holds a subdirectory: a Secret volume never does,
	 *                                  and Spring Boot's config tree would read one as dotted keys that
	 *                                  this library would not follow
	 */
	public static SortedSet<String> keys(final Path directory) throws IOException {
		final SortedSet<String> keys = new TreeSet<>();
		try (DirectoryStream<Path> entries = Files.newDirectoryStream(directory)) {
			for (Path entry : entries) {
				final String name = String.valueOf(entry.getFileName());
				if (name.startsWith("..")) {
					continue;
				}
				if (Files.isDirectory(entry)) {
					throw new IllegalArgumentException("Secret directory " + directory + " holds the directory "
							+ name + ", but a Kubernetes Secret volume holds files only. Mount the Secret whole, "
							+ "without subdirectories, or remove the directory.");
				}
				if (Files.isRegularFile(entry)) {
					keys.add(name);
				}
			}
		} catch (DirectoryIteratorException e) {
			throw e.getCause();
		}
		return Collections.unmodifiableSortedSet(keys);
	}

	/**
	 * The timestamped directory {@code ..data} names now, or empty where there is no {@code ..data} link.
	 */
	private static Optional<Path> generation(final Path directory) throws IOException {
		final Path data = directory.resolve(DATA_LINK);
		if (!Files.isSymbolicLink(data)) {
			return Optional.empty();
		}
		try {
			return Optional.of(directory.resolve(Files.readSymbolicLink(data)).normalize());
		} catch (NoSuchFileException e) {
			// Removed between the check and the read: no volume renames it away, so treat it as absent.
			return Optional.empty();
		}
	}

	/**
	 * Reads one generation directory.
	 *
	 * @param <T> what it produces
	 */
	@FunctionalInterface
	public interface GenerationReader<T> {

		/**
		 * Reads {@code generation}, capturing everything it needs.
		 *
		 * @param generation the directory to read the keys from
		 * @return what was read
		 * @throws IOException if a read fails; retried if {@code ..data} has moved since it was pinned
		 */
		T read(Path generation) throws IOException;
	}
}
