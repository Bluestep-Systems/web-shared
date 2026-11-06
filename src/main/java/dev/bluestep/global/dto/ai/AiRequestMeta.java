package dev.bluestep.global.dto.ai;

import java.util.Optional;
import java.util.OptionalLong;

import org.jspecify.annotations.Nullable;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

/**
 * What the caller observed about one provider API request of a turn: when it ran, which model answered, the
 * provider's id for it, and whether it finished cleanly.
 *
 * <p>Carried in {@link AiCompletionRequest#requestMeta()}, one element per element of
 * {@link AiCompletionRequest#requestUnitAmounts()}, in the same order. It is what lets the metering side place each
 * request's amounts in the hour the provider billed them and compare them with the provider's own usage report: the
 * turn's row alone says when the turn was authorized, not when each request's usage was final, and names the model
 * the caller asked for, not the one that answered.</p>
 *
 * <p><b>Every check is a constraint, none is thrown.</b> A request whose metadata is wrong still consumed what its
 * amounts say. Binding must therefore succeed whatever these values are, so the metering side can record the amounts
 * and mark the metadata invalid instead of refusing the whole report. That includes a missing or null value: every
 * component is boxed and {@code @Nullable} so Jackson binds an element that lacks {@code "t"}, {@code "f"},
 * {@code "m"} or {@code "p"} (Jackson 3 refuses a missing primitive), and {@code @NotNull} reports it instead.
 * After validation passes every component but {@code providerRequestId} is present; before it, read them through
 * the {@code optX()} accessors. A clock that stepped backwards between {@code t} and {@code f} is a fact about the caller's host, not a reason to lose the usage.</p>
 *
 * @param startedAt          epoch milliseconds when the request was sent to the provider; required, positive
 * @param finishedAt         epoch milliseconds when the request's usage was final; required, positive, not before
 *                           {@code startedAt}
 * @param model              the model the provider reported answering with (its response's {@code model}), not the
 *                           one requested; at most {@link #MAX_MODEL_LENGTH} characters
 * @param providerRequestId  the provider's id for the request (Anthropic's {@code request-id} header, else the
 *                           message id; OpenAI's {@code x-request-id}), absent when the provider gave none; at most
 *                           {@link #MAX_PROVIDER_REQUEST_ID_LENGTH} characters
 * @param partial            true when the request did not finish cleanly (the stream broke, an error event arrived, the
 *                           attempt was abandoned) and its amounts are what was observed before that
 */
public record AiRequestMeta(
		@JsonProperty("t") @NotNull @Positive @Nullable Long startedAt,
		@JsonProperty("f") @NotNull @Positive @Nullable Long finishedAt,
		@JsonProperty("m") @NotBlank @Size(max = MAX_MODEL_LENGTH) @Nullable String model,
		@JsonProperty("id") @Size(max = MAX_PROVIDER_REQUEST_ID_LENGTH) @Nullable String providerRequestId,
		@JsonProperty("p") @NotNull @Nullable Boolean partial) {

	/**
	 * An element with nothing in it: what a JSON {@code null} element of {@code "rm"} binds as, so the list never holds a
	 * null and validation reports each missing field.
	 */
	public static final AiRequestMeta ABSENT = new AiRequestMeta(null, null, null, null, null);

	/** The longest model name accepted; matches the width web-global stores it in. */
	public static final int MAX_MODEL_LENGTH = 100;

	/** The longest provider request id accepted; far above any provider's id format. */
	public static final int MAX_PROVIDER_REQUEST_ID_LENGTH = 200;

	/**
	 * Whether the request finished no earlier than it started.
	 *
	 * <p>A bean-validation rule, not a JSON property: {@code @JsonIgnore} keeps it off the wire.</p>
	 *
	 * @return true when {@code finishedAt >= startedAt}, or when either is missing (which {@code @NotNull} reports)
	 */
	@JsonIgnore
	@AssertTrue(message = "finishedAt must not be before startedAt")
	public boolean isFinishedNotBeforeStarted() {
		Long started = startedAt;
		Long finished = finishedAt;
		return started == null || finished == null || finished >= started;
	}

	/** {@link #startedAt}, empty when the caller omitted it (a violation). */
	public OptionalLong optStartedAt() {
		return startedAt == null ? OptionalLong.empty() : OptionalLong.of(startedAt);
	}

	/** {@link #finishedAt}, empty when the caller omitted it (a violation). */
	public OptionalLong optFinishedAt() {
		return finishedAt == null ? OptionalLong.empty() : OptionalLong.of(finishedAt);
	}

	/** {@link #model}, empty when the caller omitted it (a violation). */
	public Optional<String> optModel() {
		return Optional.ofNullable(model);
	}

	/** {@link #partial}, empty when the caller omitted it (a violation). */
	public Optional<Boolean> optPartial() {
		return Optional.ofNullable(partial);
	}

	/** {@link #providerRequestId}, as an {@code Optional}: empty when the provider gave none. */
	public Optional<String> optProviderRequestId() {
		return Optional.ofNullable(providerRequestId);
	}
}
