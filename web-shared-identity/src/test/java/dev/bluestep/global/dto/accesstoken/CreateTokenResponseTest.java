package dev.bluestep.global.dto.accesstoken;

import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import dev.bluestep.global.dto.ToStringRedaction;

/** Pins that a created token's plaintext never reaches a log line through {@code toString}. */
class CreateTokenResponseTest {

	@Test
	void printsWithoutTheRawToken() {
		CreateTokenResponse created = new CreateTokenResponse("bst_plaintext-secret", 1L, 2L, "enc", "alias",
				new String[] {"read", "write"}, null, null, new byte[] {1, 2});

		ToStringRedaction.assertRedacts(created, "rawtoken", "bst_plaintext-secret");
		assertTrue(created.toString().contains("scopes=[read, write]"), created.toString());
	}
}
