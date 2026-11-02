package dev.bluestep.global.dto.ai;

import java.util.Optional;
import java.util.OptionalInt;

import org.jspecify.annotations.Nullable;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Preflight authorization result. The spend budget is enforced server-side and not
 * echoed here; {@code maxIterations} is the per-turn loop guard the caller honors.
 *
 * <p>The two optional components are complementary: an authorized result carries a
 * {@code maxIterations} and no {@code denialCode}; a denied result carries a
 * {@code denialCode} and no {@code maxIterations}.</p>
 */
public record AiPreflightResponse(
		@JsonProperty("a") boolean authorized,
		@JsonProperty("t") String trackingId,
		@JsonProperty("d") @Nullable AiDenialCode denialCode,
		@JsonProperty("i") @Nullable Integer maxIterations) {

	/** {@link #denialCode}, as an {@code Optional}: present exactly when the call was denied. */
	public Optional<AiDenialCode> optDenialCode() {
		return Optional.ofNullable(denialCode);
	}

	/** {@link #maxIterations}, as an {@code OptionalInt}: present exactly when the call was authorized. */
	public OptionalInt optMaxIterations() {
		return maxIterations == null ? OptionalInt.empty() : OptionalInt.of(maxIterations);
	}
}
