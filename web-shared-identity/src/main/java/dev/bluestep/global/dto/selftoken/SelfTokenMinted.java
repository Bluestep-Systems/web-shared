package dev.bluestep.global.dto.selftoken;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.jspecify.annotations.Nullable;

/**
 * A token just minted or rotated (6.5.0): the {@code 201} of {@code POST /self-tokens} and of
 * {@code POST /self-tokens/{id}/rotations}. {@link #token} is the raw {@code b6pt_} secret, and this answer is the
 * only time it is ever returned.
 *
 * @param id        the new token's id
 * @param token     the raw secret, {@code b6pt_…}
 * @param alias     its alias (a rotation keeps the old one)
 * @param scopes    its effective scopes, sorted wire spellings, as {@link SelfTokenView#scopes}
 * @param expiresAt when it expires; absent when it never does
 */
public record SelfTokenMinted(UUID id, String token, String alias, List<String> scopes,
		@Nullable Instant expiresAt) {

	/** Jackson binds an omitted array to null whatever the null-marking says; absent means no scopes. */
	public SelfTokenMinted {
		scopes = scopes == null ? List.of() : List.copyOf(scopes);
	}

	/** {@link #expiresAt}, as an {@code Optional}: empty when the token never expires. */
	public Optional<Instant> optExpiresAt() {
		return Optional.ofNullable(expiresAt);
	}

	/** Every component but {@link #token}, which a log line must never receive. */
	@Override
	public String toString() {
		return "SelfTokenMinted[id=" + id + ", token=<redacted>, alias=" + alias + ", scopes=" + scopes
				+ ", expiresAt=" + expiresAt + "]";
	}
}
