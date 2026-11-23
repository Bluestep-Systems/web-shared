package dev.bluestep.secretfiles;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

import org.junit.jupiter.api.Test;

/**
 * The snapshot value type: a defensive copy, an Optional lookup, and a string form that never prints a
 * value.
 */
class SecretSnapshotTest {

	@Test
	void getAnswersPresentAndAbsentKeys() {
		final SecretSnapshot snapshot = new SecretSnapshot(Map.of("DB_PASSWORD", "pw"));
		assertEquals(Optional.of("pw"), snapshot.get("DB_PASSWORD"));
		assertEquals(Optional.empty(), snapshot.get("MISSING"));
	}

	@Test
	void theSnapshotIsACopyOfItsArgument() {
		final Map<String, String> source = new HashMap<>(Map.of("A", "1"));
		final SecretSnapshot snapshot = new SecretSnapshot(source);
		source.put("A", "2");
		source.put("B", "3");
		assertEquals(Map.of("A", "1"), snapshot.values());
		assertThrows(UnsupportedOperationException.class, () -> snapshot.values().put("C", "4"));
	}

	@Test
	void toStringNamesKeysButNeverValues() {
		final String rendered = new SecretSnapshot(Map.of("B_KEY", "value-two", "A_KEY", "value-one")).toString();
		assertEquals("SecretSnapshot[A_KEY, B_KEY]", rendered);
		assertFalse(rendered.contains("value-"), rendered);
	}

	@Test
	void equalityIsByValues() {
		assertEquals(new SecretSnapshot(Map.of("A", "1")), new SecretSnapshot(new HashMap<>(Map.of("A", "1"))));
		assertNotEquals(new SecretSnapshot(Map.of("A", "1")), new SecretSnapshot(Map.of("A", "1\n")));
	}

	@Test
	void aNullValueIsRefused() {
		final Map<String, String> withNull = new HashMap<>();
		withNull.put("A", null);
		assertThrows(NullPointerException.class, () -> new SecretSnapshot(withNull));
	}
}
