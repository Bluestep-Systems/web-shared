package dev.bluestep.global.dto.usage.threshold;

/**
 * Where a {@link UsageThresholdAction.Notify} sends its notice.
 *
 * <p>{@link #EMAIL} and {@link #IN_APP} reach the tenant's own users, named as recipients, and web
 * sends them: web-global queues a {@link UsageNotification} and web's dispatcher delivers it.
 * {@link #OPS} reaches Bluestep's operators through web-global's own alerting, names nobody, and is
 * never queued for web.</p>
 *
 * <p>Serialized under its constant name. Closed: see the package javadoc for how a later channel
 * reaches only consumers that know it.</p>
 */
public enum NotifyChannel {

	/** An email to each recipient's address, from the application's address. */
	EMAIL {
		@Override
		public boolean namesRecipients() {
			return true;
		}
	},

	/** An in-app alert to each recipient. */
	IN_APP {
		@Override
		public boolean namesRecipients() {
			return true;
		}
	},

	/** Bluestep's operators, through web-global's alerting; no recipients. */
	OPS {
		@Override
		public boolean namesRecipients() {
			return false;
		}
	};

	/**
	 * Whether this channel delivers to named tenant users, and so is sent by web's dispatcher. A
	 * channel that does not names nobody and is never queued as a {@link UsageNotification}.
	 *
	 * @return {@code true} for {@link #EMAIL} and {@link #IN_APP}
	 */
	public abstract boolean namesRecipients();
}
