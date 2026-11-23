package dev.bluestep.global.dto.selftoken;

import com.fasterxml.jackson.annotation.JsonCreator;

/**
 * How a mint's lifetime is chosen (6.5.0). The gateway's JSON tells "{@code expiresAt} absent" from
 * "{@code expiresAt: null}", which a nullable record component cannot carry, so it resolves the two into this.
 *
 * <p>No {@code UNKNOWN} default, on purpose: the app is this enum's only reader, and an expiry kind it does not
 * know must be refused (400), never read as {@link #DEFAULT} or {@link #NEVER}. A later kind arrives under a
 * later tag.</p>
 *
 * <p><b>Names only.</b> Jackson otherwise binds an enum from its ordinal, both {@code 1} and {@code "1"}, which
 * would read a malformed request as {@link #NEVER}: a permanent token. {@link #fromWire} accepts exactly the
 * constant names, case-sensitive, and refuses everything else.</p>
 */
public enum SelfTokenExpiry {
	/** {@code expiresAt} absent: 90 days from now, capped at the presenting token's own expiry. */
	DEFAULT,
	/** {@code expiresAt: null}: never expires. Refused when the presenting token itself expires. */
	NEVER,
	/** {@code expiresAt: <instant>}: then, which must be in the future and not after the presenting token's. */
	AT;

	/**
	 * The kind a wire value names, by exact constant name. A number, a quoted number, another case or an unknown
	 * name is refused, so it fails to bind.
	 *
	 * @throws IllegalArgumentException for anything but a constant's name
	 */
	@JsonCreator(mode = JsonCreator.Mode.DELEGATING)
	public static SelfTokenExpiry fromWire(String name) {
		for (SelfTokenExpiry kind : values()) {
			if (kind.name().equals(name)) {
				return kind;
			}
		}
		throw new IllegalArgumentException("not an expiry kind: " + name);
	}
}
