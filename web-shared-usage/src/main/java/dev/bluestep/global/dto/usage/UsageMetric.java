package dev.bluestep.global.dto.usage;

/**
 * Every usage figure a tenant can be shown, by name: the vocabulary the usage chart's metric picker
 * offers and that usage thresholds key on.
 *
 * <p>Each constant says its unit and how it behaves over a window, because a threshold on it has to
 * know: {@link #aggregation()} is that behaviour, and {@link #perCategory()} says whether a
 * {@link UsageCategory} applies. Consumers read both from here rather than each writing its own
 * switch over the constants.</p>
 *
 * <p>Thread CPU time and allocated bytes are deliberately absent. Producers report them
 * ({@link UsageWindow#cpuMillis()}, {@link UsageWindow#allocBytes()}), but they are internal metrics,
 * never shown to a tenant or priced, so they have no name here.</p>
 *
 * <p>Serialized under its constant name.</p>
 */
public enum UsageMetric {

	/** Completed requests, a count. {@link UsageSeriesPoint#hits()} summed over the window. */
	HITS {
		@Override
		public Aggregation aggregation() {
			return Aggregation.CUMULATIVE;
		}

		@Override
		public boolean perCategory() {
			return true;
		}

		@Override
		public ContractRelease introducedIn() {
			return ContractRelease.V5_2_0;
		}
	},

	/**
	 * Wall-clock time spent serving requests, in milliseconds. {@link UsageSeriesPoint#pageMillis()}
	 * summed over the window.
	 */
	PAGE_MILLIS {
		@Override
		public Aggregation aggregation() {
			return Aggregation.CUMULATIVE;
		}

		@Override
		public boolean perCategory() {
			return true;
		}

		@Override
		public ContractRelease introducedIn() {
			return ContractRelease.V5_2_0;
		}
	},

	/**
	 * Mean wall-clock time per request, in milliseconds. Derived, not stored: {@link #PAGE_MILLIS}
	 * divided by {@link #HITS} over the same window, and undefined for a window with no hits.
	 */
	PAGE_MILLIS_AVG {
		@Override
		public Aggregation aggregation() {
			return Aggregation.AVERAGE;
		}

		@Override
		public boolean perCategory() {
			return true;
		}

		@Override
		public ContractRelease introducedIn() {
			return ContractRelease.V5_2_0;
		}
	},

	/**
	 * The slowest single request's wall-clock time, in milliseconds.
	 * {@link UsageSeriesPoint#pageMillisMax()} taken as the largest over the window.
	 */
	PAGE_MILLIS_PEAK {
		@Override
		public Aggregation aggregation() {
			return Aggregation.PEAK;
		}

		@Override
		public boolean perCategory() {
			return true;
		}

		@Override
		public ContractRelease introducedIn() {
			return ContractRelease.V5_2_0;
		}
	},

	/**
	 * Time spent in database calls while serving requests, in milliseconds, as the request's own
	 * threads measured it. {@link UsageSeriesPoint#dbMillis()} summed over the window.
	 */
	DB_MILLIS {
		@Override
		public Aggregation aggregation() {
			return Aggregation.CUMULATIVE;
		}

		@Override
		public boolean perCategory() {
			return true;
		}

		@Override
		public ContractRelease introducedIn() {
			return ContractRelease.V5_2_0;
		}
	},

	/** Request body bytes received. {@link UsageSeriesPoint#requestBytes()} summed over the window. */
	REQUEST_BYTES {
		@Override
		public Aggregation aggregation() {
			return Aggregation.CUMULATIVE;
		}

		@Override
		public boolean perCategory() {
			return true;
		}

		@Override
		public ContractRelease introducedIn() {
			return ContractRelease.V5_2_0;
		}
	},

	/** Response bytes sent. {@link UsageSeriesPoint#responseBytes()} summed over the window. */
	RESPONSE_BYTES {
		@Override
		public Aggregation aggregation() {
			return Aggregation.CUMULATIVE;
		}

		@Override
		public boolean perCategory() {
			return true;
		}

		@Override
		public ContractRelease introducedIn() {
			return ContractRelease.V5_2_0;
		}
	},

	/**
	 * The tenant's database size, in bytes: the {@code db} storage meter, read from the latest
	 * storage sample.
	 */
	STORAGE_DB_BYTES {
		@Override
		public Aggregation aggregation() {
			return Aggregation.LEVEL;
		}

		@Override
		public boolean perCategory() {
			return false;
		}

		@Override
		public ContractRelease introducedIn() {
			return ContractRelease.V5_2_0;
		}
	},

	/**
	 * The tenant's stored files, in bytes, as the tenant sees them: the {@code file_logical} storage
	 * meter, read from the latest storage sample.
	 */
	STORAGE_FILE_BYTES {
		@Override
		public Aggregation aggregation() {
			return Aggregation.LEVEL;
		}

		@Override
		public boolean perCategory() {
			return false;
		}

		@Override
		public ContractRelease introducedIn() {
			return ContractRelease.V5_2_0;
		}
	};

	/**
	 * How this metric behaves over a window: what a limit on it means and which windows suit it.
	 *
	 * @return the metric's aggregation
	 */
	public abstract Aggregation aggregation();

	/**
	 * Whether this metric is reported per {@link UsageCategory}. The request metrics are; the
	 * storage levels belong to no category, so naming one alongside them is incoherent.
	 *
	 * @return {@code true} for a metric a category narrows
	 */
	public abstract boolean perCategory();

	/**
	 * The release that introduced this metric: a caller whose contract tag is older cannot bind it, so
	 * web-global leaves rows that name it out of that caller's answer. Not on the wire.
	 *
	 * @return the release this constant arrived in
	 */
	public abstract ContractRelease introducedIn();

	/** How a {@link UsageMetric} behaves over a window. Serialized under its constant name. */
	public enum Aggregation {

		/** Adds up across the window, so a limit on it is a budget for the window. */
		CUMULATIVE,

		/** One cumulative metric over another across the window; two windows' averages do not add. */
		AVERAGE,

		/** The largest single value anywhere in the window; not summed. */
		PEAK,

		/** A reading at a moment; it does not accumulate over a window at all. */
		LEVEL
	}
}
