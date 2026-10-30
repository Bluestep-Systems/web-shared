package dev.bluestep.global.dto.tenant;

import java.util.Optional;

import org.jspecify.annotations.Nullable;

/**
 * How a tenant's users sign in: its sign-in type (the monolith's {@code global.logintype} row for the
 * tenant, folded into the tenant) and the password-policy columns beside it.
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
		@Nullable String loginUrl,
		@Nullable String logoutUrl,
		@Nullable Integer passwordGraceDays,
		@Nullable Double maxLoginAttempts,
		String rememberPassword) {

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

	/** {@link #passwordGraceDays}, as an {@code Optional}. */
	public Optional<Integer> optPasswordGraceDays() {
		return Optional.ofNullable(passwordGraceDays);
	}

	/** {@link #maxLoginAttempts}, as an {@code Optional}. */
	public Optional<Double> optMaxLoginAttempts() {
		return Optional.ofNullable(maxLoginAttempts);
	}
}
