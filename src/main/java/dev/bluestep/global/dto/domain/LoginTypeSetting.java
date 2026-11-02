package dev.bluestep.global.dto.domain;

import java.util.Optional;

import org.jspecify.annotations.Nullable;

import jakarta.validation.constraints.NotNull;

/**
 * A domain's own sign-in type: the monolith's {@code global.logintype} row for the domain, folded into
 * the domain.
 *
 * <p>{@code data} stays JSON text, as {@code TenantSignIn.loginData} does: its shape depends on the
 * type, and only the monolith interprets it. web-global stores it as {@code jsonb}, so text that is not
 * JSON is refused there with a {@code 400}.</p>
 *
 * @param type the kind of sign-in
 * @param data that kind's settings as JSON text, or absent for none
 */
public record LoginTypeSetting(
		@NotNull LoginTypeName type,
		@Nullable String data) {

	/** {@link #data}, as an {@code Optional}. */
	public Optional<String> optData() {
		return Optional.ofNullable(data);
	}
}
