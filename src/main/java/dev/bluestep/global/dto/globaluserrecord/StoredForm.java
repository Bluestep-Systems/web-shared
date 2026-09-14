package dev.bluestep.global.dto.globaluserrecord;

/**
 * What the monolith's encoded credential looks like on the wire, so that a request carrying a plainly
 * un-encoded value is refused at the edge rather than stored.
 *
 * <p>Package-private, and deliberately so. This is a mistake-catcher for one column, not a
 * general-purpose format library; publishing it would invite a caller to treat "passes this check" as
 * "is a valid credential", which is not something a shape test can establish. Both records that need
 * it live here.</p>
 *
 * <h2>It is not a security boundary</h2>
 *
 * <p>A caller determined to send a plaintext password with a generation marker and a Base64 body will
 * succeed. What this catches is the mistake that actually happens — a caller reading "credential" and
 * passing the password it has in hand rather than the encoded one — which would otherwise store
 * something unusable and put a live password in a request body on the way. Cheap, and it fails at the
 * edge with a field name rather than at a login six months later.</p>
 *
 * <h2>Why {@code attribs} is not held to anything here</h2>
 *
 * <p>The row's other opaque column is the same kind of ciphertext and invites the same mistake, and a
 * guard for it was written and then withdrawn. The reason is worth recording so it is not
 * reintroduced without an answer: {@code attribs} is {@code varchar(4000)} with no CHECK, so the
 * database accepts any text in it, and the shapes carrying it are served by an <b>already-released</b>
 * contract tag. A constraint there would refuse values that tag accepted — and, because the update is
 * a whole-record replace, would make any row already holding such a value un-editable through the
 * API, since a read hands the stored value straight back and the write then refuses it. A credential
 * constraint has neither problem: it guards a column nothing reads back, and on the create shape it
 * only narrows a guard that tag already shipped.</p>
 */
final class StoredForm {

	/**
	 * The character the monolith's password encoder writes before a generation character.
	 *
	 * <p>Its decoder's own recognition test is {@code length() > 2 && charAt(0) == '\n'}: a marked
	 * value is {@code '\n'}, one character naming the cipher generation, then the Base64 body.</p>
	 */
	private static final char GENERATION_MARKER = '\n';

	/**
	 * The generations whose value the monolith can read back, each a Base64 body over a reversible
	 * cipher.
	 *
	 * <p><b>A closed set, matching a closed switch.</b> The decoder branches on the generation
	 * character with cases for {@code '0'} and {@code '1'}, falls through to returning {@code null},
	 * and swallows the exception on the way. So a value marked with a character nothing here names is
	 * not "a generation this package has not heard of" — it is a value that decodes to nothing, and an
	 * account whose credential decodes to nothing can never sign in and reports no error at either
	 * end.</p>
	 *
	 * <p>Measured over a development copy of the global schema: every one of the 104 marked credentials
	 * carries generation {@code '1'}, so nothing real is refused by naming the set.</p>
	 */
	private static final String REVERSIBLE_GENERATIONS = "01";

	/**
	 * The generation whose value the monolith <em>cannot</em> read back: Argon2id, one-way by design.
	 *
	 * <h2>Why this is here at all, when the set above was deliberately closed</h2>
	 *
	 * <p>The reasoning that closed it was sound and is now half wrong. It ran: an unrecognised
	 * generation decodes to nothing, and a credential that decodes to nothing is a dead account, so
	 * refusing one protects the account. That inference holds for every generation this constraint was
	 * written against, because all of them were ciphers and reading one back was how a password was
	 * checked.</p>
	 *
	 * <p>It does not hold for this one. A {@code \n2} value decodes to nothing <b>on purpose</b>, and
	 * the account it belongs to signs in perfectly well — the monolith stopped verifying by decoding
	 * and now verifies against the stored form, so "cannot be read back" became a property to want
	 * rather than a symptom to catch. See {@code docs/password-hashing-plan.md} in the monolith.</p>
	 *
	 * <p>So the set stays closed and gains a member, rather than being opened. What this constraint
	 * tests is still "did the monolith's encoder produce this", and the answer for a generation with no
	 * body shape of its own would still be no. It is named here because its shape is known, not because
	 * the character is new.</p>
	 */
	private static final char ONE_WAY_GENERATION = '2';

	/** The algorithm a one-way body names, in the encoded form Argon2's reference implementation defines. */
	private static final String ARGON2ID = "argon2id";

	/** What separates the fields of that encoded form, and what ends the segment ahead of it. */
	private static final char PHC_SEPARATOR = '$';

	/**
	 * The shortest value that can carry a marker and a body: the marker, the generation character, and
	 * at least one character of ciphertext. Mirrors the decoder's own {@code length() > 2}, so a value
	 * this constraint accepts is exactly a value that decoder will try to decode.
	 */
	private static final int SHORTEST_MARKED_VALUE = 3;

	/** Where the Base64 body starts, after the marker and the generation character. */
	private static final int BODY_START = 2;

	private StoredForm() {
	}

	/**
	 * Whether a credential carries a cipher-generation marker the monolith's decoder recognises.
	 *
	 * <h2>What was measured, and why this is narrower than "is a stored credential"</h2>
	 *
	 * <p>Over the {@code global.globaluser} rows of a development copy of the global schema, 237 of
	 * which carry a non-empty credential: <b>104 (43.9%) carry the marker and 133 (56.1%) do not</b>.
	 * The unmarked majority is not malformed — it is an <em>earlier cipher generation</em>, written
	 * before the marker existed, which the monolith's decoder still reads by falling through to
	 * generation zero when it finds no marker. Those values are shorter and are the output of a
	 * different block cipher; nothing distinguishes one from an arbitrary string by shape alone, which
	 * is why no predicate here can accept them without also accepting a plaintext password.</p>
	 *
	 * <p>So this deliberately answers the narrower question: <b>is this the current generation's stored
	 * form</b>, not "is this something the monolith could decode". That narrower question is the right
	 * one for a write, because the only producer this surface has is the monolith's own encoder, which
	 * emits a marked value every time — and because the monolith holds the decoded plaintext whenever
	 * it writes, every write it makes re-encodes to the current generation regardless of what the row
	 * held before. The 56% is a measurement of what is <em>at rest</em>, not of what arrives here.</p>
	 *
	 * <p><b>The consequence to know about:</b> a caller that ever carries a stored value through
	 * verbatim — a migration, a restore, a row copy — would be refused for those 56%. There is no such
	 * caller today and this surface has no operation that would produce one. If one is ever wanted, it
	 * wants an explicit verb that says it is carrying an already-stored value, not a quiet loosening
	 * of this constraint into one that can no longer tell a credential from a password.</p>
	 *
	 * <h2>Three conditions, and the third depends on the second</h2>
	 *
	 * <p>Long enough to hold a body, marked with a generation this package names, and a body of the
	 * shape <em>that generation</em> produces — Base64 over a reversible cipher, or the Argon2id
	 * encoded form. Anything weaker admits a value that stores successfully and is then unusable: for
	 * a reversible generation that is an account which can never sign in again, with a 200 at the
	 * write and no error at the read; for the one-way generation it is an account whose every password
	 * is refused, which fails just as quietly. Measured: all 104 marked credentials in a development
	 * copy of the global schema satisfy all three, so the strictness costs nothing real.</p>
	 */
	static boolean carriesGenerationMarker(String credential) {
		if (credential.length() < SHORTEST_MARKED_VALUE || credential.charAt(0) != GENERATION_MARKER) {
			return false;
		}
		final String body = credential.substring(BODY_START);
		if (credential.charAt(1) == ONE_WAY_GENERATION) {
			return isOneWayBody(body);
		}
		return REVERSIBLE_GENERATIONS.indexOf(credential.charAt(1)) >= 0 && isBase64Body(body);
	}

	/**
	 * Whether a body has the shape the one-way generation produces: an optional {@code key=value}
	 * segment, then Argon2id's encoded form.
	 *
	 * <p>The encoded form is what makes this a usable test at all. It is self-describing —
	 * {@code $argon2id$v=19$m=...,t=...,p=...$salt$hash} — so a value carrying it did not come from a
	 * caller passing the password it had in hand, which is the mistake this class exists to catch. The
	 * segment ahead of it is whatever the monolith knows about the password and cannot recompute
	 * later; it is held to a shape rather than parsed, because its keys are the monolith's to add and
	 * a constraint that enumerated them would refuse the next one.</p>
	 *
	 * <p>An <em>empty</em> segment is accepted for the same reason. A body that is nothing but the
	 * encoded form is a coherent value in this generation, and refusing it would make a key the
	 * monolith happens to write today into part of the contract.</p>
	 */
	private static boolean isOneWayBody(String body) {
		final int encodedStart = body.indexOf(PHC_SEPARATOR);
		if (encodedStart < 0 || !isMetadata(body.substring(0, encodedStart))) {
			return false;
		}
		// A leading empty segment, then algorithm, version, cost, salt and hash.
		final String[] parts = body.substring(encodedStart).split("\\$", -1);
		return parts.length == 6
				&& parts[0].isEmpty()
				&& ARGON2ID.equals(parts[1])
				&& !parts[2].isEmpty()
				&& !parts[3].isEmpty()
				&& !parts[4].isEmpty() && isBase64Alphabet(parts[4])
				&& !parts[5].isEmpty() && isBase64Alphabet(parts[5]);
	}

	/**
	 * Whether a segment is empty or a comma-separated list of non-empty {@code key=value} pairs.
	 *
	 * <p>Both halves are held to a token alphabet rather than to anything meaning-bearing. The
	 * monolith writes numbers here today and the temptation was to say so, but a constraint in this
	 * repository that refuses the next key's value is a cross-repository coupling nobody would find
	 * until a password change started answering 400 in production — and it would buy nothing, because
	 * what establishes that a caller did not pass a password is the Argon2id encoded form after this
	 * segment, not the segment's contents.</p>
	 */
	private static boolean isMetadata(String segment) {
		if (segment.isEmpty()) {
			return true;
		}
		for (final String pair : segment.split(",", -1)) {
			final int equals = pair.indexOf('=');
			if (equals <= 0 || equals == pair.length() - 1
					|| !isToken(pair.substring(0, equals))
					|| !isToken(pair.substring(equals + 1))) {
				return false;
			}
		}
		return true;
	}

	/** Alphanumerics and the punctuation a version, a score or a date needs; no whitespace, no {@code =}. */
	private static boolean isToken(String value) {
		for (int i = 0; i < value.length(); i++) {
			final char c = value.charAt(i);
			final boolean allowed = (c >= 'A' && c <= 'Z') || (c >= 'a' && c <= 'z')
					|| (c >= '0' && c <= '9') || c == '.' || c == '-' || c == '_' || c == '+';
			if (!allowed) {
				return false;
			}
		}
		return true;
	}

	/**
	 * Whether a value has the shape of the monolith's Base64-over-ciphertext columns: Base64 characters
	 * only, optional padding, and a length that is a positive multiple of four.
	 *
	 * <p>Standard Base64 only — not the URL-safe alphabet, and not a line-wrapped body. The monolith's
	 * encoder draws from a fixed dictionary and never wraps at any length: its output array is exactly
	 * {@code ((n + 2) / 3) * 4} bytes, all from that alphabet. So a value carrying {@code -}, {@code _}
	 * or a newline did not come from it. Measured over a development copy of the global schema: all 104
	 * marked credentials have a body satisfying this, so the check costs no real value.</p>
	 *
	 * <p>The empty string is refused: a marker with nothing after it is a value the monolith's decoder
	 * returns {@code null} for, which is an account that can never sign in rather than a rejected
	 * request.</p>
	 */
	private static boolean isBase64Body(String value) {
		if (value.isEmpty() || value.length() % 4 != 0) {
			return false;
		}
		// At most two padding characters, and only at the very end. Anything left after stripping them
		// is held to the alphabet, so a value that is nothing but padding still fails.
		int end = value.length();
		int padding = 0;
		while (end > 0 && padding < 2 && value.charAt(end - 1) == '=') {
			end--;
			padding++;
		}
		return isBase64Alphabet(value.substring(0, end));
	}

	/**
	 * Whether every character is in the standard Base64 alphabet, with nothing said about length or
	 * padding.
	 *
	 * <p>Split out because the two generations disagree about those two things and agree about this
	 * one. A reversible body is padded and a whole number of quanta; Argon2id's encoded form carries
	 * unpadded salt and hash segments whose lengths are whatever the parameters made them, so holding
	 * them to a multiple of four would refuse every value the encoder produces.</p>
	 *
	 * <p>The empty string answers true here. Callers that need a non-empty value say so themselves,
	 * which is what {@link #isBase64Body}'s own emptiness check is for.</p>
	 */
	private static boolean isBase64Alphabet(String value) {
		for (int i = 0; i < value.length(); i++) {
			final char c = value.charAt(i);
			final boolean inAlphabet = (c >= 'A' && c <= 'Z') || (c >= 'a' && c <= 'z')
					|| (c >= '0' && c <= '9') || c == '+' || c == '/';
			if (!inAlphabet) {
				return false;
			}
		}
		return true;
	}
}
