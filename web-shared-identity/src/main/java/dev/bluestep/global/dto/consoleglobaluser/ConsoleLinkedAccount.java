package dev.bluestep.global.dto.consoleglobaluser;

import java.util.Objects;

import dev.bluestep.global.dto.globaluserrecord.LinkedAccount;

/**
 * A linked identity as the Console shows it (from 6.13.0): its issuer, the provider that issuer belongs to, and a hint
 * of the subject. <b>Never the subject itself.</b> The {@code (issuer, sub)} pair is what signs a person in at no
 * tenant, so the Console gets enough to tell two links apart and nothing that names the account at its provider.
 *
 * @param issuer      the issuer, as stored
 * @param provider    the provider class of {@code issuer} ({@link LinkedAccountProvider#of})
 * @param subjectHint the subject's last {@value #HINT_LENGTH} characters, or empty for a subject shorter than
 *                    {@value #SHORTEST_HINTED} characters (where four would be most of it); at most
 *                    {@value #HINT_LENGTH} characters whatever the sender
 */
public record ConsoleLinkedAccount(String issuer, LinkedAccountProvider provider, String subjectHint) {

	/** How many of the subject's trailing characters (code points) the hint carries. */
	public static final int HINT_LENGTH = 4;
	/** The shortest subject (in code points) a hint is given for: four of it must be at most half. */
	public static final int SHORTEST_HINTED = 2 * HINT_LENGTH;

	/** Requires every member, and refuses a hint longer than {@value #HINT_LENGTH} characters. */
	public ConsoleLinkedAccount {
		Objects.requireNonNull(issuer, "issuer");
		Objects.requireNonNull(provider, "provider");
		Objects.requireNonNull(subjectHint, "subjectHint");
		if (subjectHint.codePointCount(0, subjectHint.length()) > HINT_LENGTH) {
			throw new IllegalArgumentException("A subject hint is at most " + HINT_LENGTH + " characters");
		}
	}

	/** {@code account} as the Console may see it: the subject reduced to its hint. */
	public static ConsoleLinkedAccount of(LinkedAccount account) {
		return new ConsoleLinkedAccount(account.issuer(), LinkedAccountProvider.of(account.issuer()),
				hint(account.sub()));
	}

	/** The last {@value #HINT_LENGTH} code points of {@code sub}, or empty when it is too short to hint at. */
	static String hint(String sub) {
		int points = sub.codePointCount(0, sub.length());
		if (points < SHORTEST_HINTED) {
			return "";
		}
		return sub.substring(sub.offsetByCodePoints(0, points - HINT_LENGTH));
	}
}
