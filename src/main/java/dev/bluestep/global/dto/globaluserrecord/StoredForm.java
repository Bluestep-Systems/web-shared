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
	 * The cipher generations the monolith's decoder can actually read.
	 *
	 * <p><b>A closed set, matching a closed switch.</b> The decoder branches on the generation
	 * character with cases for {@code '0'} and {@code '1'} and no default, falls through to returning
	 * {@code null}, and swallows the exception on the way. So a value marked with any other character
	 * is not "a generation this package has not heard of" — it is a value that decodes to nothing, and
	 * an account whose credential decodes to nothing can never sign in and reports no error at either
	 * end.</p>
	 *
	 * <p>This deliberately does <em>not</em> stay open for a future generation. A new cipher generation
	 * requires a change to that switch, so the monolith cannot add one this constraint would have
	 * needed to already accept; leaving the set open buys nothing and costs the silently-dead account
	 * above. Measured over a development copy of the global schema: every one of the 104 marked
	 * credentials carries generation {@code '1'}, so nothing real is refused by naming the set.</p>
	 */
	private static final String DECODABLE_GENERATIONS = "01";

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
	 * <h2>Three conditions, all of them the decoder's own</h2>
	 *
	 * <p>Long enough to hold a body, marked with a generation that decoder recognises, and a body its
	 * Base64 decoder can read. Anything weaker admits a value that stores successfully and then
	 * decodes to {@code null} — which is not a rejected password but an account that can never sign in
	 * again, with a 200 at the write and no error at the read. Measured: all 104 marked credentials in
	 * a development copy of the global schema satisfy all three, so the strictness costs nothing
	 * real.</p>
	 */
	static boolean carriesGenerationMarker(String credential) {
		return credential.length() >= SHORTEST_MARKED_VALUE
				&& credential.charAt(0) == GENERATION_MARKER
				&& DECODABLE_GENERATIONS.indexOf(credential.charAt(1)) >= 0
				&& isBase64Body(credential.substring(BODY_START));
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
		for (int i = 0; i < end; i++) {
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
