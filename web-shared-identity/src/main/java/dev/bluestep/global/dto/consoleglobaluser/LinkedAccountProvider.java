package dev.bluestep.global.dto.consoleglobaluser;

import java.net.URI;
import java.util.Locale;
import java.util.Set;

/**
 * Which identity provider a linked account's issuer belongs to, as the Console shows it (from 6.13.0). Decided from
 * the issuer's host alone; anything not recognised, or not a well-formed issuer, is {@link #OTHER}.
 */
public enum LinkedAccountProvider {

	/** Microsoft Entra ID (work or school) and Microsoft personal accounts. */
	MICROSOFT,

	/** Google accounts. */
	GOOGLE,

	/** Any other issuer. */
	OTHER;

	private static final Set<String> MICROSOFT_HOSTS = Set.of("login.microsoftonline.com", "sts.windows.net",
			"login.live.com", "login.windows.net");
	private static final Set<String> GOOGLE_HOSTS = Set.of("accounts.google.com");

	/**
	 * The provider of {@code issuer}: by its host, case-insensitively. Google's issuer is sometimes written without a
	 * scheme ({@code accounts.google.com}), so a bare host is read as one.
	 */
	public static LinkedAccountProvider of(String issuer) {
		String spelled = issuer.strip();
		try {
			String host = URI.create(spelled.contains("://") ? spelled : "https://" + spelled).getHost();
			if (host == null) {
				return OTHER;
			}
			String lower = host.toLowerCase(Locale.ROOT);
			if (MICROSOFT_HOSTS.contains(lower)) {
				return MICROSOFT;
			}
			if (GOOGLE_HOSTS.contains(lower)) {
				return GOOGLE;
			}
			return OTHER;
		}
		catch (IllegalArgumentException _) {
			return OTHER;
		}
	}
}
