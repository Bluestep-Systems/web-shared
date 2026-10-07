package dev.bluestep.secretfiles;

import static java.nio.charset.StandardCharsets.UTF_8;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Function;
import java.util.regex.Pattern;

import org.jspecify.annotations.Nullable;

/**
 * One-shot secret lookup for entry points that run without Spring — CLI mains, {@code runclass}
 * utilities, a migration job's own JDBC connection: the file named after the key in the mounted secret
 * directory if there is one, otherwise the environment variable of the same name.
 *
 * <p>A file's contents are returned exactly, decoded as UTF-8: nothing is trimmed, a trailing newline
 * included, just as the environment variable the file replaces carried the Secret's exact bytes. The
 * Spring side serves the mount directory the same way ({@code ExactSecretTreePostProcessor}), so a
 * utility and the application it runs beside see one value for one file. An environment variable is
 * used as it is.</p>
 *
 * <p>The directory is {@link #DEFAULT_DIRECTORY} unless the system property {@value #DIRECTORY_PROPERTY}
 * or, failing that, the environment variable {@value #DIRECTORY_ENVIRONMENT_VARIABLE} names another — the
 * same setting the Spring side reads through its {@code Environment}, so one override moves both.</p>
 *
 * <p>Nothing here caches or watches: each call reads the disk once. A long-running process that must
 * follow a rotation opens a {@link WatchedSecretDirectory} instead. Nothing here logs, and no exception
 * message carries a value.</p>
 */
public final class SecretFiles {

	/** Where the charts mount the pod's Secret volume. */
	public static final Path DEFAULT_DIRECTORY = Path.of("/var/lib/bluestep/secrets");

	/**
	 * The property naming a different mount directory. A system property here; in a Spring application
	 * the same name is read from the {@code Environment}, where the environment variable
	 * {@value #DIRECTORY_ENVIRONMENT_VARIABLE} also binds to it.
	 */
	public static final String DIRECTORY_PROPERTY = "bluestep.secrets.directory";

	/** The environment variable naming a different mount directory, when the system property is unset. */
	public static final String DIRECTORY_ENVIRONMENT_VARIABLE = "BLUESTEP_SECRETS_DIRECTORY";

	/** What Kubernetes accepts as a Secret data key. */
	private static final Pattern KEY = Pattern.compile("[-._a-zA-Z0-9]+");

	/** Reads of a file caught mid-swap before the lookup gives up. */
	private static final int READ_ATTEMPTS = 3;

	/** Pause between those reads: the kubelet finishes a swap's link changes in milliseconds. */
	private static final Duration READ_RETRY_DELAY = Duration.ofMillis(100);

	private SecretFiles() {
	}

	/**
	 * The mount directory in force: {@value #DIRECTORY_PROPERTY}, else
	 * {@value #DIRECTORY_ENVIRONMENT_VARIABLE}, else {@link #DEFAULT_DIRECTORY}. A blank setting counts as
	 * unset.
	 *
	 * @return the directory {@link #get} reads; it need not exist
	 */
	public static Path directory() {
		return directory(System.getProperty(DIRECTORY_PROPERTY), System::getenv);
	}

	/**
	 * The secret {@code key}: the exact contents of the file {@code key} in {@link #directory()} if that
	 * file exists, otherwise the environment variable {@code key}.
	 *
	 * @param key a Kubernetes Secret key, such as {@code B6P_DB_PASSWORD}
	 * @return the value, or empty when neither the directory nor the environment has one
	 * @throws IllegalArgumentException if {@code key} is not a Secret key (empty, outside
	 *                                  {@code [-._a-zA-Z0-9]}, or starting with {@code ..}, which names
	 *                                  the kubelet's own entries)
	 * @throws UncheckedIOException     if the file exists but cannot be read, even after a short retry
	 *                                  for a read caught mid-swap
	 */
	public static Optional<String> get(final String key) {
		return get(directory(), System::getenv, key);
	}

	/**
	 * {@link #get}, refusing a missing or blank value.
	 *
	 * @param key a Kubernetes Secret key
	 * @return the value; never blank
	 * @throws IllegalStateException    naming the key and the directory (never a value) when there is no
	 *                                  value or it is blank
	 * @throws IllegalArgumentException if {@code key} is not a Secret key
	 * @throws UncheckedIOException     if the file exists but cannot be read
	 */
	public static String require(final String key) {
		return require(directory(), System::getenv, key);
	}

	/** Test seam for {@link #directory()}. */
	static Path directory(final @Nullable String property, final Function<String, @Nullable String> environment) {
		if (property != null && !property.isBlank()) {
			return Path.of(property);
		}
		final String variable = environment.apply(DIRECTORY_ENVIRONMENT_VARIABLE);
		if (variable != null && !variable.isBlank()) {
			return Path.of(variable);
		}
		return DEFAULT_DIRECTORY;
	}

	/** Test seam for {@link #require(String)}. */
	static String require(final Path directory, final Function<String, @Nullable String> environment,
			final String key) {
		return get(directory, environment, key)
				.filter(value -> !value.isBlank())
				.orElseThrow(() -> new IllegalStateException("Required secret " + key + " is neither a file in "
						+ directory + " nor a non-blank environment variable"));
	}

	/** Test seam for {@link #get(String)}. */
	static Optional<String> get(final Path directory, final Function<String, @Nullable String> environment,
			final String key) {
		Objects.requireNonNull(key, "key");
		if (!KEY.matcher(key).matches() || key.startsWith("..")) {
			throw new IllegalArgumentException("Not a secret key: '" + key + "'");
		}
		final Path file = directory.resolve(key);
		IOException failure = null;
		for (int attempt = 1; attempt <= READ_ATTEMPTS; attempt++) {
			// NOFOLLOW: a visible link left dangling mid-swap still counts as present, and is retried
			// rather than silently answered from the environment.
			if (!Files.exists(file, LinkOption.NOFOLLOW_LINKS)) {
				return Optional.ofNullable(environment.apply(key));
			}
			try {
				return Optional.of(new String(Files.readAllBytes(file), UTF_8));
			} catch (IOException e) {
				failure = e;
			}
			if (attempt < READ_ATTEMPTS && !pause()) {
				break;
			}
		}
		throw new UncheckedIOException("Secret " + key + " exists in " + directory + " but could not be read",
				Objects.requireNonNull(failure));
	}

	/** @return false if interrupted, in which case the interrupt is restored and the lookup gives up */
	private static boolean pause() {
		try {
			Thread.sleep(READ_RETRY_DELAY);
			return true;
		} catch (InterruptedException e) {
			Thread.currentThread().interrupt();
			return false;
		}
	}
}
