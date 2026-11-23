package dev.bluestep.global.dto.usage.threshold;

import dev.bluestep.global.dto.usage.ContractRelease;

/**
 * What became of a {@link UsageNotification} web's dispatcher handled. Either way web-global stops
 * offering it; a notice that may yet be sent is not acknowledged at all.
 *
 * <p>Serialized under its constant name. Closed: see the package javadoc for how a later outcome
 * reaches only callers that can bind it.</p>
 */
public enum UsageNotificationOutcome {

	/** The notice was sent to its recipient. */
	DELIVERED {
		@Override
		public ContractRelease introducedIn() {
			return ContractRelease.V5_3_0;
		}
	},

	/**
	 * The notice can never be sent, for instance because the recipient was deleted or has no email
	 * address; web-global keeps it as unsent instead of offering it again.
	 */
	UNDELIVERABLE {
		@Override
		public ContractRelease introducedIn() {
			return ContractRelease.V5_3_0;
		}
	};

	/**
	 * The release that introduced this outcome: web-global accepts it from callers on that tag or
	 * later. Not on the wire.
	 *
	 * @return the release this constant arrived in
	 */
	public abstract ContractRelease introducedIn();
}
