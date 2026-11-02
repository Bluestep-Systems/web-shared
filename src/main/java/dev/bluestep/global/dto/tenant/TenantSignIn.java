package dev.bluestep.global.dto.tenant;

import java.util.Optional;
import java.util.OptionalDouble;
import java.util.OptionalInt;

import org.jspecify.annotations.Nullable;

import dev.bluestep.global.dto.constraints.CodePointSize;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;

/**
 * How a tenant's users sign in: its sign-in type (the monolith's {@code global.logintype} row for the
 * tenant, folded into the tenant) and the password-policy columns beside it.
 *
 * <p>From 6.0.0 it is also written, inside {@link TenantSettings}. The string bounds are the widths of
 * the columns behind them, {@code rememberPassword} is required because its column is {@code NOT NULL},
 * and {@code loginType} must be one of the monolith's {@code LoginTypeRecord.Type} names
 * ({@link #LOGIN_TYPE_NAMES}): any other would be stored, and every monolith build would then fail
 * to load the tenant. Bean Validation applies them only where a request marks this {@code @Valid},
 * so a read carries the stored spelling whatever it is.</p>
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
		@Pattern(regexp = TenantSignIn.LOGIN_TYPE_NAMES) @Nullable String loginType,
		@Nullable String loginData,
		@CodePointSize(max = 255) @Nullable String loginUrl,
		@CodePointSize(max = 250) @Nullable String logoutUrl,
		@Nullable Integer passwordGraceDays,
		@Nullable Double maxLoginAttempts,
		@NotNull @CodePointSize(max = 50) String rememberPassword) {

	/**
	 * The sign-in type names a write accepts, as a regular expression: the monolith's
	 * {@code LoginTypeRecord.Type} constants, the same names as
	 * {@link dev.bluestep.global.dto.domain.LoginTypeName}. Case matters; the monolith reads the stored
	 * value with {@code valueOf}.
	 */
	public static final String LOGIN_TYPE_NAMES = "TENANT_OAUTH2|GENERAL_OAUTH2|ONE_STEP|TWO_STEP|CUSTOM";

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
