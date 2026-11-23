package dev.bluestep.global.dto.usage;

import java.util.Comparator;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * A release of this artifact, as a contract tag names it: {@code 5.3.0} in {@code /api/v5.3.0/}.
 *
 * <p>What a growable vocabulary's {@code introducedIn()} returns, so web-global can tell which of
 * the constants it holds a caller's tag can bind: a constant binds under every tag at or after the
 * release that introduced it. web-global always runs the newest artifact, so the constants it reads
 * this from include every one a later caller could be sent.</p>
 *
 * <p>Ordered by number, so {@code 5.10.0} follows {@code 5.9.0}.</p>
 *
 * @param major the major number; not negative
 * @param minor the minor number; not negative
 * @param patch the patch number; not negative
 */
public record ContractRelease(int major, int minor, int patch) implements Comparable<ContractRelease> {

	/** The release that added {@link UsageMetric} and {@link UsageCategory}. */
	public static final ContractRelease V5_2_0 = new ContractRelease(5, 2, 0);

	/** The release that added the usage threshold vocabulary. */
	public static final ContractRelease V5_3_0 = new ContractRelease(5, 3, 0);

	private static final Pattern TAG = Pattern.compile("(\\d+)\\.(\\d+)\\.(\\d+)");

	private static final Comparator<ContractRelease> ORDER = Comparator.comparingInt(ContractRelease::major)
			.thenComparingInt(ContractRelease::minor)
			.thenComparingInt(ContractRelease::patch);

	/**
	 * Refuses a negative number.
	 *
	 * @throws IllegalArgumentException when any part is negative
	 */
	public ContractRelease {
		if (major < 0 || minor < 0 || patch < 0) {
			throw new IllegalArgumentException("release " + major + "." + minor + "." + patch
					+ " has a negative part");
		}
	}

	/**
	 * Reads a tag as a contract path spells it.
	 *
	 * @param tag {@code major.minor.patch}, without the {@code v}
	 * @return the release
	 * @throws IllegalArgumentException when {@code tag} is not three dot-separated numbers
	 */
	public static ContractRelease parse(final String tag) {
		final Matcher matched = TAG.matcher(tag);
		if (!matched.matches()) {
			throw new IllegalArgumentException("\"" + tag + "\" is not a major.minor.patch tag");
		}
		return new ContractRelease(Integer.parseInt(matched.group(1)), Integer.parseInt(matched.group(2)),
				Integer.parseInt(matched.group(3)));
	}

	/**
	 * Orders releases by number.
	 *
	 * @param other the release to compare with
	 * @return negative, zero or positive as this release is before, the same as, or after {@code other}
	 */
	@Override
	public int compareTo(final ContractRelease other) {
		return ORDER.compare(this, other);
	}

	/**
	 * Whether a caller on this release's tag can bind something introduced in {@code introduced}.
	 *
	 * @param introduced the release a constant or rule arrived in
	 * @return {@code true} when this release is that one or later
	 */
	public boolean includes(final ContractRelease introduced) {
		return compareTo(introduced) >= 0;
	}

	/** The tag as a contract path spells it after the {@code v}: {@code 5.3.0}. */
	@Override
	public String toString() {
		return major + "." + minor + "." + patch;
	}
}
