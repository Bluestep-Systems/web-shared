package dev.bluestep.global.dto.tenant;

import java.util.Optional;
import java.util.OptionalDouble;
import java.util.OptionalInt;

import org.jspecify.annotations.Nullable;

import dev.bluestep.global.dto.constraints.CodePointSize;

import jakarta.validation.constraints.NotNull;

/**
 * How a tenant's users sign in: its sign-in type (the monolith's {@code global.logintype} row for the
 * tenant, folded into the tenant) and the password-policy columns beside it.
 *
 * <p>From 6.0.0 it is also written, inside {@link TenantSettings}. The string bounds are the widths of
 * the columns behind them, and {@code rememberPassword} is required because its column is
 * {@code NOT NULL}; Bean Validation applies them only where a request marks this {@code @Valid}.</p>
 *
 * @param loginType         the monolith's {@code LoginType} name ({@code ONE_STEP},
 *                          {@code TENANT_OAUTH2} ...), verbatim; absent for a tenant with no row,
 *                          which the monolith treats as its default
 * @param loginData         that type's settings, the stored {@code jsonb} as JSON text, verbatim.
 *                          Only ever present with a type.
 * @param loginUrl          {@code loginurl}
 * @param logoutUrl         {@code logouturl}
 * @param passwordGraceDays {@code passwordgracedays}
 * @param maxLoginAttempts  {@code maxloginattempts}, a {@code double precision} column
 * @param rememberPassword  {@code rememberpassword}, the monolith's policy name, verbatim
 */
public record TenantSignIn(
		@Nullable String loginType,
		@Nullable String loginData,
		@CodePointSize(max = 255) @Nullable String loginUrl,
		@CodePointSize(max = 250) @Nullable String logoutUrl,
		@Nullable Integer passwordGraceDays,
		@Nullable Double maxLoginAttempts,
		@NotNull @CodePointSize(max = 50) String rememberPassword) {

	/** {@link #loginType}, as an {@code Optional}. */
	public Optional<String> optLoginType() {
		return Optional.ofNullable(loginType);
	}

	/** {@link #loginData}, as an {@code Optional}. */
	public Optional<String> optLoginData() {
		return Optional.ofNullable(loginData);
	}

	/** {@link #loginUrl}, as an {@code Optional}. */
	public Optional<String> optLoginUrl() {
		return Optional.ofNullable(loginUrl);
	}

	/** {@link #logoutUrl}, as an {@code Optional}. */
	public Optional<String> optLogoutUrl() {
		return Optional.ofNullable(logoutUrl);
	}

	/** {@link #passwordGraceDays}, as an {@code OptionalInt}. */
	public OptionalInt optPasswordGraceDays() {
		return passwordGraceDays == null ? OptionalInt.empty() : OptionalInt.of(passwordGraceDays);
	}

	/** {@link #maxLoginAttempts}, as an {@code OptionalDouble}. */
	public OptionalDouble optMaxLoginAttempts() {
		return maxLoginAttempts == null ? OptionalDouble.empty() : OptionalDouble.of(maxLoginAttempts);
	}
}
