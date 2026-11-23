package dev.bluestep.contract;

import java.util.Comparator;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.jspecify.annotations.Nullable;

/**
 * A contract tag: a semver triple naming one version of a service's contract, ordered numerically
 * ({@code 6.10.0} after {@code 6.9.0}). web-global's tags are its {@code ContractVersion} constants (the web-shared
 * artifact version); web's harness tags are versions of {@code harness-contract}.
 *
 * <p>Strict, so that one tag has one spelling: three dot-separated decimal numbers without leading zeros, each at most
 * nine digits. {@code 6.5}, {@code v6.5.0}, {@code 06.5.0} and {@code 6.5.0-rc1} are not tags.</p>
 *
 * @param major the first number
 * @param minor the second number
 * @param patch the third number
 */
public record ContractTag(int major, int minor, int patch) implements Comparable<ContractTag> {

	/** The spelling, as a regular expression over the whole value. */
	public static final String PATTERN = "(0|[1-9][0-9]{0,8})\\.(0|[1-9][0-9]{0,8})\\.(0|[1-9][0-9]{0,8})";

	private static final Pattern TAG = Pattern.compile(PATTERN);

	private static final Comparator<ContractTag> ORDER = Comparator.comparingInt(ContractTag::major)
		.thenComparingInt(ContractTag::minor).thenComparingInt(ContractTag::patch);

	/** Holds each number to being non-negative. */
	public ContractTag {
		if (major < 0 || minor < 0 || patch < 0) {
			throw new IllegalArgumentException("A contract tag's numbers are non-negative: " + major + "." + minor + "."
				+ patch);
		}
	}

	/**
	 * The tag {@code text} spells.
	 *
	 * @param text the candidate, or null
	 * @return the tag, or empty when {@code text} is not one
	 */
	public static Optional<ContractTag> parse(@Nullable String text) {
		if (text == null) {
			return Optional.empty();
		}
		Matcher matched = TAG.matcher(text);
		if (!matched.matches()) {
			return Optional.empty();
		}
		return Optional.of(new ContractTag(Integer.parseInt(matched.group(1)), Integer.parseInt(matched.group(2)),
			Integer.parseInt(matched.group(3))));
	}

	/**
	 * The tag {@code text} spells, for a tag written in code.
	 *
	 * @param text the tag
	 * @return the tag
	 * @throws IllegalArgumentException when {@code text} is not a tag
	 */
	public static ContractTag of(String text) {
		return parse(text).orElseThrow(() -> new IllegalArgumentException("Not a contract tag (x.y.z): " + text));
	}

	/**
	 * Orders two tags written as strings, as {@link Integer#compare} does.
	 *
	 * @throws IllegalArgumentException when either is not a tag: every tag compared in code is a declared one, so a
	 *                                  malformed one is a coding error
	 */
	public static int compare(String left, String right) {
		return of(left).compareTo(of(right));
	}

	@Override
	public int compareTo(ContractTag other) {
		return ORDER.compare(this, other);
	}

	/**
	 * Whether this tag is newer than or the same as {@code other}.
	 *
	 * @param other the tag compared against
	 * @return whether this is at or above it
	 */
	public boolean isAtLeast(ContractTag other) {
		return compareTo(other) >= 0;
	}

	/** The tag as it is written: {@code major.minor.patch}. */
	@Override
	public String toString() {
		return major + "." + minor + "." + patch;
	}
}
