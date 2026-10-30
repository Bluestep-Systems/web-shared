package dev.bluestep.global.testing;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * One pinned set of Argon2id stored-credential vectors, for every verifier of a global user's password.
 *
 * <p>Two services verify the same stored form: the monolith, for the rows it still reads, and web-global,
 * which answers the sign-in check (6.1.0). Two implementations of one check drift apart quietly — a cost
 * ceiling raised on one side, a metadata segment parsed differently on the other — and the drift shows up
 * as an account that can sign in through one path and not the other. Each runs these rows in its own
 * build instead, so a disagreement fails a test rather than a sign-in.</p>
 *
 * <p>The rows were produced by the monolith's encoder and are never edited; see the header of
 * {@code argon2id-vectors.tsv} beside this class.</p>
 */
public final class Argon2idVectors {

	private static final String RESOURCE = "argon2id-vectors.tsv";

	private Argon2idVectors() {
	}

	/**
	 * One row.
	 *
	 * @param name       what the row is about, unique in the set
	 * @param candidate  the password offered; empty in the row that pins an empty candidate refused
	 * @param storedForm the whole stored credential, {@code \n2} marker, metadata segment and Argon2id
	 *                   encoded form, as the credential column holds it
	 * @param verifies   whether {@code candidate} is the password behind {@code storedForm} as far as a
	 *                   verifier honouring the cost ceilings is concerned
	 */
	public record Vector(String name, String candidate, String storedForm, boolean verifies) {

		/** The name, so a parameterized test reads by row. */
		@Override
		public String toString() {
			return name;
		}
	}

	/** Every row, in file order. */
	public static List<Vector> all() {
		final InputStream in = Objects.requireNonNull(Argon2idVectors.class.getResourceAsStream(RESOURCE),
				"missing test fixture " + RESOURCE);
		final List<Vector> rows = new ArrayList<>();
		try (BufferedReader reader = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8))) {
			String line;
			while ((line = reader.readLine()) != null) {
				if (line.isBlank() || line.startsWith("#")) {
					continue;
				}
				final String[] columns = line.split("\t", -1);
				if (columns.length != 4) {
					throw new IllegalStateException("malformed vector row: " + columns[0]);
				}
				rows.add(new Vector(columns[0], columns[1], unescapeMarker(columns[2]),
						Boolean.parseBoolean(columns[3])));
			}
		} catch (final IOException ex) {
			throw new UncheckedIOException(ex);
		}
		return List.copyOf(rows);
	}

	/** The leading {@code \n} the file spells as two characters, back to the newline it stands for. */
	private static String unescapeMarker(final String written) {
		return written.startsWith("\\n") ? "\n" + written.substring(2) : written;
	}
}
