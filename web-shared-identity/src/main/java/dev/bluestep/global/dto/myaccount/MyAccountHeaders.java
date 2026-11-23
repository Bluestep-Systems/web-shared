package dev.bluestep.global.dto.myaccount;

import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import dev.bluestep.global.dto.tenantaccess.GlobalUserKey;

/**
 * The header that names whose account a My Account call is for (from 6.14.0), and its one spelling, so both sides
 * write and read it alike. A constants holder, so no interface.
 *
 * <p>The gateway sets it from the browser session alone: never from a token, a path, a body or another header the
 * client sends.</p>
 */
public final class MyAccountHeaders {

	/** {@code classid:seqnum} of the signed-in global user the call acts for. */
	public static final String ACTING_GLOBAL_USER_HEADER = "B6P-Acting-Global-User";

	/** Two decimal longs, the second may be negative; nothing else (no space, no sign on the classid, no padding). */
	private static final Pattern FORM = Pattern.compile("([1-9][0-9]{0,18}):(-?[1-9][0-9]{0,18})");

	private MyAccountHeaders() {
	}

	/** {@code user} as the header carries it: {@code 222222:-42}. */
	public static String value(GlobalUserKey user) {
		return user.classid() + ":" + user.seqnum();
	}

	/**
	 * The key a header value spells, exactly as {@link #value} writes it.
	 *
	 * @return empty for anything else, a number past a long included
	 */
	public static Optional<GlobalUserKey> parse(String value) {
		Matcher matcher = FORM.matcher(value);
		if (!matcher.matches()) {
			return Optional.empty();
		}
		try {
			return Optional.of(new GlobalUserKey(Long.parseLong(matcher.group(1)), Long.parseLong(matcher.group(2))));
		}
		catch (NumberFormatException _) {
			return Optional.empty();
		}
	}
}
