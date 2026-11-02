package dev.bluestep.global.testing;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.Test;

/**
 * The published vector set reads back whole and means what its consumers assume. This library has no
 * verifier, so nothing here checks a hash; the consumers do that.
 */
class Argon2idVectorsTest {

	private final List<Argon2idVectors.Vector> vectors = Argon2idVectors.all();

	@Test
	void everyRowReadsAndNamesAreUnique() {
		assertEquals(16, vectors.size(), "a row was lost or added without updating this count");
		final Set<String> names = new HashSet<>();
		for (final Argon2idVectors.Vector vector : vectors) {
			assertTrue(names.add(vector.name()), "duplicate vector name " + vector.name());
		}
	}

	@Test
	void everyStoredFormIsOneWayWithTheMarkerRestored() {
		for (final Argon2idVectors.Vector vector : vectors) {
			assertTrue(vector.storedForm().startsWith("\n2"), vector.name() + " is not a \\n2 stored form");
		}
	}

	@Test
	void bothAnswersAreCovered() {
		assertTrue(vectors.stream().anyMatch(Argon2idVectors.Vector::verifies), "no row verifies");
		assertTrue(vectors.stream().anyMatch(vector -> !vector.verifies()), "no row is refused");
	}

	@Test
	void theNonAsciiPasswordSurvivesTheFileEncoding() {
		final Argon2idVectors.Vector utf8 = vectors.stream()
				.filter(vector -> vector.name().equals("utf8-match"))
				.findFirst()
				.orElseThrow();
		assertEquals("pässwörd-日本語-🔑", utf8.candidate());
	}
}
