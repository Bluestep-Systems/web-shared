package dev.bluestep.global.dto.usage;

import java.time.Duration;

/**
 * The width of one bucket in a {@link UsageSeriesResponse}: the uniform grain web-global folds its
 * minute, hour and day rows up to, whatever grain each stretch of the range is stored at.
 *
 * <p>Buckets are UTC-aligned: a bucket starts on a whole multiple of {@link #width()} since the
 * epoch, which for both grains is the start of a UTC hour or UTC day. Serialized under its constant
 * name ({@code HOUR}, {@code DAY}), as the series endpoint's {@code grain} parameter spells it.</p>
 */
public enum UsageGrain {

	/** One UTC hour per bucket. */
	HOUR {
		@Override
		public Duration width() {
			return Duration.ofHours(1);
		}
	},

	/** One UTC day per bucket. */
	DAY {
		@Override
		public Duration width() {
			return Duration.ofDays(1);
		}
	};

	/**
	 * How long one bucket of this grain spans.
	 *
	 * @return the bucket width, a whole number of hours
	 */
	public abstract Duration width();
}
