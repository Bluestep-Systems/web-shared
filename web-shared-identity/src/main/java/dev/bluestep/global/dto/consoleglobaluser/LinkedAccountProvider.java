package dev.bluestep.global.dto.consoleglobaluser;

import java.util.List;
import java.util.Optional;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * Which identity provider a linked account's issuer belongs to, as the Console shows it (from 6.13.0). Decided from
 * the issuer's host alone; anything not recognised, or not a well-formed issuer, is {@link #OTHER}.
 *
 * <p><b>The rule is data (6.17.0).</b> Each recognised provider is a list of hosts, and an issuer belongs to the
 * provider when it matches {@link #issuerPattern()} <em>as a whole</em>: an optional scheme, optional user-info, one
 * of the hosts, an optional port, an optional path, query or fragment, around nothing but white space. Java
 * ({@link #of}) and the database (web-global's directory filter, PostgreSQL's {@code ~*} over the same text) both
 * evaluate that one pattern, so the filter and the {@code provider} a page shows cannot disagree. The pattern uses
 * only syntax both regular-expression engines read alike, and is matched case-insensitively with {@code .} matching
 * every character.</p>
 */
public enum LinkedAccountProvider {

	/** Microsoft Entra ID (work or school) and Microsoft personal accounts. */
	MICROSOFT("login.microsoftonline.com", "sts.windows.net", "login.live.com", "login.windows.net"),

	/** Google accounts. */
	GOOGLE("accounts.google.com"),

	/** Any other issuer. */
	OTHER();

	private final List<String> hosts;
	private final Optional<Pattern> compiled;
	private final Optional<String> pattern;

	LinkedAccountProvider(String... hosts) {
		this.hosts = List.of(hosts);
		if (hosts.length == 0) {
			this.pattern = Optional.empty();
			this.compiled = Optional.empty();
		}
		else {
			String text = "\\s*(?:[a-z][a-z0-9+.-]*://)?(?:[^/?#@]*@)?(?:"
					+ this.hosts.stream().map(host -> host.replace(".", "\\.")).collect(Collectors.joining("|"))
					+ ")(?::[0-9]*)?(?:[/?#].*)?\\s*";
			this.pattern = Optional.of(text);
			this.compiled = Optional.of(Pattern.compile(text, Pattern.CASE_INSENSITIVE | Pattern.DOTALL));
		}
	}

	/** The hosts this provider's issuers carry; none for {@link #OTHER}. */
	public List<String> hosts() {
		return hosts;
	}

	/**
	 * The pattern a whole issuer matches to belong to this provider, to be matched case-insensitively with {@code .}
	 * matching every character (Java {@code CASE_INSENSITIVE | DOTALL}, PostgreSQL's {@code ~*} as it stands), or empty
	 * for {@link #OTHER}, which is what no other provider claims.
	 */
	public Optional<String> issuerPattern() {
		return pattern;
	}

	/**
	 * The provider of {@code issuer}: by its host, case-insensitively. Google's issuer is sometimes written without a
	 * scheme ({@code accounts.google.com}), so a bare host is read as one.
	 */
	public static LinkedAccountProvider of(String issuer) {
		for (LinkedAccountProvider provider : values()) {
			if (provider.compiled.map(compiled -> compiled.matcher(issuer).matches()).orElse(false)) {
				return provider;
			}
		}
		return OTHER;
	}
}
