package dev.bluestep.secretfiles;

import java.util.Map;
import java.util.Optional;
import java.util.TreeSet;

/**
 * One consistent read of a secret directory: every visible key mapped to its file's contents.
 *
 * <p>Values are the file's bytes decoded as UTF-8, exactly — nothing is trimmed. A Secret created
 * from a file written with {@code echo} carries a trailing newline, and whether that newline is part
 * of the secret is the consumer's call, so a consumer that wants it gone strips it itself.</p>
 *
 * <p>{@link #toString()} names keys only. The record's generated {@code toString} would print every
 * value, and a snapshot's string form is exactly what ends up in a log line.</p>
 *
 * @param values every key mapped to its value; copied, so later changes to the argument do not show
 *               through. Neither keys nor values may be null.
 */
public record SecretSnapshot(Map<String, String> values) {

	/**
	 * Copies {@code values} into an immutable map.
	 *
	 * @throws NullPointerException if the map, or any key or value in it, is null
	 */
	public SecretSnapshot {
		values = Map.copyOf(values);
	}

	/**
	 * The value stored under {@code key}.
	 *
	 * @param key the file name the value was read from
	 * @return the value, or empty when the directory held no such key at the time of the read
	 */
	public Optional<String> get(final String key) {
		return Optional.ofNullable(values.get(key));
	}

	/**
	 * Names the keys, sorted, and never the values.
	 *
	 * @return {@code SecretSnapshot[KEY_A, KEY_B]}
	 */
	@Override
	public String toString() {
		return "SecretSnapshot" + new TreeSet<>(values.keySet());
	}
}
