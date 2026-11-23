package dev.bluestep.global.dto.consolefleet;

import java.time.Instant;
import java.util.Objects;
import java.util.Optional;
import java.util.regex.Pattern;

import org.jspecify.annotations.Nullable;

import dev.bluestep.global.dto.fleet.NamespaceName;

/**
 * One namespace of the fleet as web-global last checked it, for the Console (from 6.21.0): its
 * {@code global.fleet_namespace} row and what its web answered.
 *
 * @param name        the namespace's name, as stored (a name that is not a DNS label is listed, never asked, and so
 *                    {@link FleetReadiness#UNREACHABLE})
 * @param label       its display label; absent where it has none
 * @param legacy      the row's {@code legacy} switch
 * @param isPrivate   the row's private mark (legacy {@code flags} bit 0)
 * @param modified    a domain or tenant in the namespace changed since its ingresses were last rebuilt (legacy
 *                    {@code flags} bit 1)
 * @param build       what its web's {@code GET /appinfo/version} answered, trimmed ({@code 26.11.09.06}); absent when
 *                    it did not answer in time, answered anything but a short version string
 *                    ({@link #BUILD_PATTERN}), or the namespace is {@link FleetReadiness#UNREACHABLE}
 * @param readiness   what its web's {@code GET /readyz} said
 * @param failedCheck for {@link FleetReadiness#NOT_READY}, which check failed, when its {@code /healthz} answered;
 *                    absent otherwise
 * @param checkedAt   when web-global sent this namespace's {@code /readyz} (or decided not to ask)
 */
public record ConsoleFleetNamespace(
		NamespaceName name,
		@Nullable String label,
		boolean legacy,
		boolean isPrivate,
		boolean modified,
		@Nullable String build,
		FleetReadiness readiness,
		@Nullable FleetCheck failedCheck,
		Instant checkedAt) {

	/** The longest build string carried. */
	public static final int MAX_BUILD_LENGTH = 40;

	/**
	 * A build string's shape (whole value): a letter or digit, then letters, digits, {@code .}, {@code _}, {@code +} or
	 * {@code -}, at most {@value #MAX_BUILD_LENGTH} in all. A release ({@code 26.11.09.06}) and web's unset default
	 * ({@code dev}) both match; prose, markup and anything with a space do not.
	 */
	public static final String BUILD_PATTERN = "[0-9A-Za-z][0-9A-Za-z._+-]{0," + (MAX_BUILD_LENGTH - 1) + "}";

	private static final Pattern BUILD = Pattern.compile(BUILD_PATTERN);

	/** Requires the name, readiness and time, and refuses a build that is not a short version string. */
	public ConsoleFleetNamespace {
		Objects.requireNonNull(name, "name");
		Objects.requireNonNull(readiness, "readiness");
		Objects.requireNonNull(checkedAt, "checkedAt");
		if (build != null && !isBuild(build)) {
			throw new IllegalArgumentException("A build is a short version string");
		}
	}

	/**
	 * Whether {@code value} is a build string this shape carries, as given (untrimmed).
	 *
	 * @param value a candidate
	 * @return {@code true} when it matches {@link #BUILD_PATTERN}
	 */
	public static boolean isBuild(String value) {
		return BUILD.matcher(value).matches();
	}

	/** {@link #label}, as an {@code Optional}. */
	public Optional<String> optLabel() {
		return Optional.ofNullable(label);
	}

	/** {@link #build}, as an {@code Optional}. */
	public Optional<String> optBuild() {
		return Optional.ofNullable(build);
	}

	/** {@link #failedCheck}, as an {@code Optional}. */
	public Optional<FleetCheck> optFailedCheck() {
		return Optional.ofNullable(failedCheck);
	}
}
