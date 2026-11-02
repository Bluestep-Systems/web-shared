package dev.bluestep.global.dto.accesstoken;

import java.time.OffsetDateTime;
import java.util.Arrays;
import java.util.Optional;

import org.jspecify.annotations.Nullable;

/**
 * Response to a successful access-token creation. {@code rawtoken} is the plaintext value
 * and is the only time it will be returned — callers must surface it to the end user
 * immediately. {@code encodedtoken} is the stored hash; subsequent operations
 * ({@code /by-token} lookups, updates, revocations) identify the token by this value.
 * {@code expiresAt} is absent when the token never expires; {@code lastAccessed} is absent
 * on a freshly created token.
 */
public record CreateTokenResponse(
		String rawtoken,
		Long classid,
		Long seqnum,
		String encodedtoken,
		String alias,
		String[] scopes,
		@Nullable OffsetDateTime expiresAt,
		@Nullable OffsetDateTime lastAccessed,
		byte[] flags
) {

	/** {@link #expiresAt}, as an {@code Optional}: empty when the token never expires. */
	public Optional<OffsetDateTime> optExpiresAt() {
		return Optional.ofNullable(expiresAt);
	}

	/** {@link #lastAccessed}, as an {@code Optional}: empty on a freshly created token. */
	public Optional<OffsetDateTime> optLastAccessed() {
		return Optional.ofNullable(lastAccessed);
	}

	/**
	 * Every component but {@link #rawtoken}, the plaintext bearer token, which is replaced by a fixed
	 * placeholder: a response reaching a log line must not hand the token to whoever reads the log. The
	 * arrays print their contents.
	 */
	@Override
	public String toString() {
		return "CreateTokenResponse[rawtoken=<redacted>, classid=" + classid + ", seqnum=" + seqnum
				+ ", encodedtoken=" + encodedtoken + ", alias=" + alias + ", scopes=" + Arrays.toString(scopes)
				+ ", expiresAt=" + expiresAt + ", lastAccessed=" + lastAccessed + ", flags=" + Arrays.toString(flags)
				+ "]";
	}
}
