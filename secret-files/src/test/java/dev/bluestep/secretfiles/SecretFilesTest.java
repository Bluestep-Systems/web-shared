package dev.bluestep.secretfiles;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;

import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import dev.bluestep.secretfiles.testing.KubeletSecretVolume;

/**
 * {@link SecretFiles}: file first, then the environment, each value exactly as stored. The
 * environment is a map the test controls, through the package-private seams the public methods
 * delegate to.
 */
class SecretFilesTest {

	@TempDir
	Path tmp;

	private static Function<String, @Nullable String> env(final Map<String, String> values) {
		return values::get;
	}

	@Test
	void aFileInTheMountWinsOverTheEnvironment() throws IOException {
		KubeletSecretVolume.create(tmp, Map.of("DB_PASSWORD", "from-file\n"));

		assertEquals(Optional.of("from-file\n"),
				SecretFiles.get(tmp, env(Map.of("DB_PASSWORD", "from-env")), "DB_PASSWORD"));
	}

	@Test
	void theEnvironmentIsTheFallbackWhenThereIsNoFile() throws IOException {
		KubeletSecretVolume.create(tmp, Map.of("OTHER", "x"));

		assertEquals(Optional.of("from-env"),
				SecretFiles.get(tmp, env(Map.of("DB_PASSWORD", "from-env")), "DB_PASSWORD"));
		assertEquals(Optional.of("from-env"),
				SecretFiles.get(tmp.resolve("not-mounted"), env(Map.of("DB_PASSWORD", "from-env")), "DB_PASSWORD"),
				"a directory that does not exist is no file");
		assertEquals(Optional.empty(), SecretFiles.get(tmp, env(Map.of()), "DB_PASSWORD"));
	}

	@Test
	void valuesAreTheExactFileContentsTrailingNewlineIncluded() throws IOException {
		Files.writeString(tmp.resolve("LF"), "v\n", UTF_8);
		Files.writeString(tmp.resolve("CRLF"), "v\r\n", UTF_8);
		Files.writeString(tmp.resolve("TWO_LINES"), "a\nb\n", UTF_8);
		Files.writeString(tmp.resolve("TWO_NEWLINES"), "v\n\n", UTF_8);
		Files.writeString(tmp.resolve("SPACES"), " v ", UTF_8);
		final Function<String, @Nullable String> none = env(Map.of());

		assertEquals(Optional.of("v\n"), SecretFiles.get(tmp, none, "LF"),
				"a lone trailing newline is kept, as an env var kept it");
		assertEquals(Optional.of("v\r\n"), SecretFiles.get(tmp, none, "CRLF"));
		assertEquals(Optional.of("a\nb\n"), SecretFiles.get(tmp, none, "TWO_LINES"), "multi-line is left alone");
		assertEquals(Optional.of("v\n\n"), SecretFiles.get(tmp, none, "TWO_NEWLINES"));
		assertEquals(Optional.of(" v "), SecretFiles.get(tmp, none, "SPACES"), "nothing is trimmed");
	}

	@Test
	void anEnvironmentValueIsUsedAsItIs() {
		assertEquals(Optional.of("v\n"), SecretFiles.get(tmp, env(Map.of("K", "v\n")), "K"));
	}

	@Test
	void requireRefusesMissingAndBlankNamingTheKeyNotAValue() throws IOException {
		Files.writeString(tmp.resolve("BLANK"), "  \n", UTF_8);

		final IllegalStateException missing = assertThrows(IllegalStateException.class,
				() -> SecretFiles.require(tmp, env(Map.of()), "DB_PASSWORD"));
		assertTrue(missing.getMessage().contains("DB_PASSWORD"));
		assertTrue(missing.getMessage().contains(tmp.toString()));
		assertThrows(IllegalStateException.class, () -> SecretFiles.require(tmp, env(Map.of()), "BLANK"));
		assertEquals("ok", SecretFiles.require(tmp, env(Map.of("K", "ok")), "K"));
	}

	@Test
	void aKeyThatIsNotASecretKeyIsRefused() {
		for (String key : new String[] {"", "../etc/passwd", "a/b", "..data", "with space"}) {
			assertThrows(IllegalArgumentException.class, () -> SecretFiles.get(tmp, env(Map.of()), key), key);
		}
	}

	@Test
	void aLinkStillDanglingAfterTheRetryIsAnErrorNotAFallback() throws IOException {
		Files.createSymbolicLink(tmp.resolve("DB_PASSWORD"), Path.of("..data", "DB_PASSWORD"));

		assertThrows(UncheckedIOException.class,
				() -> SecretFiles.get(tmp, env(Map.of("DB_PASSWORD", "from-env")), "DB_PASSWORD"));
	}

	@Test
	void theDirectoryComesFromThePropertyThenTheEnvironmentThenTheDefault() {
		assertEquals(Path.of("/from/property"),
				SecretFiles.directory("/from/property", env(Map.of(SecretFiles.DIRECTORY_ENVIRONMENT_VARIABLE, "/env"))));
		assertEquals(Path.of("/env"),
				SecretFiles.directory(" ", env(Map.of(SecretFiles.DIRECTORY_ENVIRONMENT_VARIABLE, "/env"))));
		assertEquals(SecretFiles.DEFAULT_DIRECTORY, SecretFiles.directory(null, env(Map.of())));
	}

	@Test
	void thePublicLookupReadsTheDirectoryNamedByTheSystemProperty() throws IOException {
		KubeletSecretVolume.create(tmp, Map.of("SECRET_FILES_TEST_KEY", "from-file\n"));
		final String previous = System.getProperty(SecretFiles.DIRECTORY_PROPERTY);
		System.setProperty(SecretFiles.DIRECTORY_PROPERTY, tmp.toString());
		try {
			assertEquals(tmp, SecretFiles.directory());
			assertEquals("from-file\n", SecretFiles.require("SECRET_FILES_TEST_KEY"), "exactly, as the env var was");
			assertFalse(SecretFiles.get("SECRET_FILES_TEST_ABSENT_KEY").isPresent());
		} finally {
			if (previous == null) {
				System.clearProperty(SecretFiles.DIRECTORY_PROPERTY);
			} else {
				System.setProperty(SecretFiles.DIRECTORY_PROPERTY, previous);
			}
		}
	}
}
