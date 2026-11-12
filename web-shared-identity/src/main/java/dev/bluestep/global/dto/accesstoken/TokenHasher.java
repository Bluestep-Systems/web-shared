package dev.bluestep.global.dto.accesstoken;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

/**
 * Canonical hash used for access tokens. The web-global service persists only the hash
 * (the {@code encodedtoken} column) and looks up tokens by hash; callers that hold a raw
 * {@code b6pt_}-prefixed token must hash it before handing it to web-global. Using a single
 * definition here keeps both sides in lockstep.
 */
public final class TokenHasher {

	private TokenHasher() {}

	/**
	 * Returns the lowercase hex SHA-512 of the given raw token. A slow KDF is unnecessary
	 * because generated raw tokens carry 192 bits of entropy, which defeats brute-force
	 * and rainbow-table attacks on fast hashes.
	 */
	public static String sha512Hex(String raw) {
		byte[] digest;
		try {
			digest = MessageDigest.getInstance("SHA-512")
					.digest(raw.getBytes(StandardCharsets.UTF_8));
		} catch (NoSuchAlgorithmException e) {
			throw new IllegalStateException("SHA-512 unavailable", e);
		}
		StringBuilder sb = new StringBuilder(digest.length * 2);
		for (byte b : digest) {
			sb.append(Character.forDigit((b >> 4) & 0xf, 16));
			sb.append(Character.forDigit(b & 0xf, 16));
		}
		return sb.toString();
	}
}
