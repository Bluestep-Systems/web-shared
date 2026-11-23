package dev.bluestep.global.dto;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.reflect.RecordComponent;

/**
 * Checks a record's hand-written {@code toString} that redacts a secret component: the secret's value is
 * absent, the component prints as {@code <redacted>}, and every other component is still named, so a
 * component added later and forgotten in the hand-written list fails the test.
 */
public final class ToStringRedaction {

	private ToStringRedaction() {
	}

	/**
	 * Asserts that {@code record}'s {@code toString} names each of its components and hides {@code secret}.
	 *
	 * @param record    the record to print
	 * @param component the name of the redacted component
	 * @param secret    the value it carries, which must not appear
	 */
	public static void assertRedacts(Record record, String component, String secret) {
		String printed = record.toString();
		assertFalse(printed.contains(secret), printed);
		assertTrue(printed.startsWith(record.getClass().getSimpleName() + "["), printed);
		assertTrue(printed.contains(component + "=<redacted>"), printed);
		for (RecordComponent each : record.getClass().getRecordComponents()) {
			assertTrue(printed.contains(each.getName() + "="), each.getName() + " missing from " + printed);
		}
	}
}
