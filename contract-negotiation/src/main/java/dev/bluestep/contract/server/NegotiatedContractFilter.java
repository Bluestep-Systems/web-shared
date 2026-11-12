package dev.bluestep.contract.server;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.util.ArrayDeque;
import java.util.Collections;
import java.util.Deque;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.regex.Pattern;

import dev.bluestep.contract.ContractHandshake;
import dev.bluestep.contract.ContractHeaders;
import dev.bluestep.contract.ContractRefusalToken;
import dev.bluestep.contract.ContractTag;
import jakarta.servlet.DispatcherType;
import jakarta.servlet.Filter;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/**
 * Answers a service's contract handshake and enforces {@value ContractHeaders#CONTRACT} on its stable paths,
 * <b>before</b> the dispatcher matches a handler.
 *
 * <h2>Why a filter, ahead of the dispatcher</h2>
 *
 * <p>For the reason web-global's {@code ContractVersionFilter} gives: method and media-type matching happen inside the
 * dispatcher's handler lookup, before any interceptor runs, so a guard placed there lets an unserved tag leak a
 * {@code 405} listing verbs, a {@code 415}, or a {@code 200} to an unmatched {@code OPTIONS}. A request refused here
 * has matched nothing, so none of that can happen.</p>
 *
 * <h2>What it does, per request (the {@code REQUEST} dispatch only)</h2>
 * <ul>
 *   <li><b>A non-canonical path</b>: when the raw URI, its percent-decoding, that with dot-segments removed, or the
 *       container's mapped path is the handshake or under the stable prefix, all four must be equal and the raw URI
 *       canonical (no {@code ;}, {@code //}, {@code .}/{@code ..} segment or needless percent-encoding), else
 *       {@code 400}.</li>
 *   <li><b>The handshake path</b>, exactly: {@code GET} answers {@code 200}, {@code Cache-Control: no-store}, the
 *       {@link ContractHandshake} as JSON. Any other verb answers {@code 405 Allow: GET}.</li>
 *   <li><b>Under the stable prefix</b>: exactly one {@value ContractHeaders#CONTRACT} header holding a tag, else
 *       {@code 400 contract-header-invalid}; a live tag, else {@code 404 unknown-version}; a tag that serves the first
 *       segment, else {@code 404 surface-not-served}. Each refusal goes through {@code sendError} with
 *       {@value ContractHeaders#REFUSAL} set first, and its message names only the tag asked for. An accepted request
 *       gets the attribute {@value ContractHeaders#NEGOTIATED_ATTRIBUTE} and the response header
 *       {@value ContractHeaders#CONTRACT} naming the tag, then is either rewritten onto the tagged path
 *       ({@link NegotiationMode.Rewrite}) or passed on as it is ({@link NegotiationMode.Attribute}).</li>
 *   <li>Every answer under the protocol, refusals included, carries {@code Vary: B6P-Contract}.</li>
 *   <li><b>Anything else</b> passes through untouched, including a tagged path: that style is the service's own
 *       business.</li>
 * </ul>
 *
 * <h2>Ordering: authentication first</h2>
 *
 * <p>Register it <b>after</b> the service's authentication (web-global: Spring Security at {@code -100}, this filter at
 * {@code -1}, ahead of its {@code ContractVersionFilter} at {@code 0}; web: after the harness's auth filter). An
 * unauthenticated request must be answered {@code 401} before any tag is evaluated, so that an anonymous caller can
 * neither read the handshake nor tell a live tag from an unknown one by the refusal it gets.</p>
 */
public final class NegotiatedContractFilter implements Filter {

	/** The handshake's {@code service}: safe to write into JSON as it is. */
	private static final Pattern SERVICE = Pattern.compile("^[a-z][a-z0-9-]{0,62}$");

	/**
	 * Spring's cached parsed paths (ServletRequestPathUtils.PATH_ATTRIBUTE, UrlPathHelper.PATH_ATTRIBUTE). Spring's
	 * dispatcher re-parses on entry anyway; removing them before a rewrite makes sure nothing downstream can read a
	 * path parsed from the stable form.
	 */
	private static final List<String> SPRING_PATH_CACHES = List.of(
		"org.springframework.web.util.ServletRequestPathUtils.PATH", "org.springframework.web.util.UrlPathHelper.PATH");

	private final ContractRegistry registry;
	private final NegotiatedContractPaths paths;

	/**
	 * A filter over {@code registry} at {@code paths}.
	 *
	 * @param registry what the service serves
	 * @param paths    where its handshake and stable paths are, and what to do with an accepted request
	 * @throws IllegalArgumentException when the registry's service name is not lower-case letters, digits and
	 *                                  {@code -}
	 */
	public NegotiatedContractFilter(ContractRegistry registry, NegotiatedContractPaths paths) {
		this.registry = Objects.requireNonNull(registry, "registry");
		this.paths = Objects.requireNonNull(paths, "paths");
		if (!SERVICE.matcher(registry.service()).matches()) {
			throw new IllegalArgumentException("A service name is lower-case letters, digits and '-': "
				+ registry.service());
		}
	}

	@Override
	public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
			throws IOException, ServletException {
		if (!(request instanceof HttpServletRequest http) || !(response instanceof HttpServletResponse answer)
				|| request.getDispatcherType() != DispatcherType.REQUEST) {
			chain.doFilter(request, response);
			return;
		}
		// Decided on every spelling of the path a later stage might match on: the raw request URI, that URI
		// percent-decoded, the decoded form with its dot-segments removed, and the path the container mapped (servlet
		// path + path info). If ANY of them is the handshake or under the stable prefix, the raw URI must be canonical
		// and all of them equal; otherwise the request is refused 400. That closes "/h%61rness/admin/../../outside"
		// (raw outside the prefix, container path "/outside", but a matcher that decodes before normalising sees
		// "/harness/admin/**"), "/harness;x=1/…", "/outside/../harness/x" and every such disagreement: fail closed.
		String raw = pathWithinApplication(http);
		Optional<String> rawDecoded = percentDecoded(raw);
		String normalized = removeDotSegments(rawDecoded.orElse(raw));
		String mapped = decodedPath(http);
		if (!isProtected(raw) && !rawDecoded.map(this::isProtected).orElse(false) && !isProtected(normalized)
				&& !isProtected(mapped)) {
			chain.doFilter(request, response);
			return;
		}
		vary(answer);
		if (!isCanonical(raw) || rawDecoded.isEmpty() || !rawDecoded.get().equals(mapped)
				|| !normalized.equals(mapped)) {
			answer.sendError(HttpServletResponse.SC_BAD_REQUEST, "Not a canonical contract path");
			return;
		}
		if (mapped.equals(paths.handshakePath())) {
			handshake(http, answer);
			return;
		}
		negotiated(http, answer, chain, raw.substring(paths.stablePrefix().length()),
			mapped.substring(paths.stablePrefix().length()));
	}

	/** Whether {@code path} is the handshake or under the stable prefix. */
	private boolean isProtected(String path) {
		return path.equals(paths.handshakePath()) || path.startsWith(paths.stablePrefix());
	}

	/**
	 * Every response under the protocol varies by the negotiated tag, refusals included, so no cache hands a
	 * {@code 6.5.0} answer to a {@code 6.6.0} request for the same stable URL.
	 */
	private static void vary(HttpServletResponse response) {
		response.addHeader("Vary", ContractHeaders.CONTRACT);
	}

	/**
	 * Whether a raw path is in its one canonical spelling: no {@code ;} parameters, no backslash, no empty segment
	 * ({@code //}), no {@code .} or {@code ..} segment, and no percent-encoding of an unreserved character
	 * (letters, digits, {@code - . _ ~}) or of {@code /}, {@code \}, {@code ;}. Malformed percent-encoding is not
	 * canonical either.
	 */
	static boolean isCanonical(String raw) {
		if (raw.indexOf(';') >= 0 || raw.indexOf('\\') >= 0 || raw.contains("//")) {
			return false;
		}
		for (String segment : raw.split("/", -1)) {
			if (segment.equals(".") || segment.equals("..")) {
				return false;
			}
		}
		for (int index = raw.indexOf('%'); index >= 0; index = raw.indexOf('%', index + 1)) {
			if (index + 2 >= raw.length()) {
				return false;
			}
			int high = Character.digit(raw.charAt(index + 1), 16);
			int low = Character.digit(raw.charAt(index + 2), 16);
			if (high < 0 || low < 0) {
				return false;
			}
			char decoded = (char) (high * 16 + low);
			if (Character.isLetterOrDigit(decoded) && decoded < 0x80 || "-._~/\\;".indexOf(decoded) >= 0) {
				return false;
			}
		}
		return true;
	}

	/** {@code raw} percent-decoded as UTF-8 ({@code +} stays {@code +}), or empty when it is malformed. */
	static Optional<String> percentDecoded(String raw) {
		if (raw.indexOf('%') < 0) {
			return Optional.of(raw);
		}
		ByteArrayOutputStream bytes = new ByteArrayOutputStream(raw.length());
		for (int index = 0; index < raw.length(); index++) {
			char c = raw.charAt(index);
			if (c == '%') {
				if (index + 2 >= raw.length()) {
					return Optional.empty();
				}
				int high = Character.digit(raw.charAt(index + 1), 16);
				int low = Character.digit(raw.charAt(index + 2), 16);
				if (high < 0 || low < 0) {
					return Optional.empty();
				}
				bytes.write(high * 16 + low);
				index += 2;
			}
			else {
				byte[] encoded = String.valueOf(c).getBytes(StandardCharsets.UTF_8);
				bytes.write(encoded, 0, encoded.length);
			}
		}
		try {
			return Optional.of(StandardCharsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT)
				.onUnmappableCharacter(CodingErrorAction.REPORT).decode(ByteBuffer.wrap(bytes.toByteArray()))
				.toString());
		}
		catch (CharacterCodingException e) {
			return Optional.empty();
		}
	}

	/** RFC 3986 §5.2.4: {@code path} without its {@code .} and {@code ..} segments. */
	static String removeDotSegments(String path) {
		Deque<String> kept = new ArrayDeque<>();
		String[] segments = path.split("/", -1);
		for (int index = 0; index < segments.length; index++) {
			String segment = segments[index];
			boolean last = index == segments.length - 1;
			if (segment.equals(".") || segment.equals("..")) {
				if (segment.equals("..") && kept.size() > 1) {
					kept.removeLast();
				}
				if (last) {
					kept.addLast("");
				}
				continue;
			}
			kept.addLast(segment);
		}
		String joined = String.join("/", kept);
		return path.startsWith("/") && !joined.startsWith("/") ? "/" + joined : joined;
	}

	private void handshake(HttpServletRequest request, HttpServletResponse response) throws IOException {
		response.setHeader("Cache-Control", "no-store");
		if (!"GET".equals(request.getMethod())) {
			response.setHeader("Allow", "GET");
			response.sendError(HttpServletResponse.SC_METHOD_NOT_ALLOWED, "The contract handshake answers GET only");
			return;
		}
		byte[] body = json(ContractHandshake.of(registry.service(), registry.liveTags()))
			.getBytes(StandardCharsets.UTF_8);
		response.setStatus(HttpServletResponse.SC_OK);
		response.setContentType("application/json");
		response.setCharacterEncoding("UTF-8");
		response.setContentLength(body.length);
		response.getOutputStream().write(body);
	}

	private void negotiated(HttpServletRequest request, HttpServletResponse response, FilterChain chain,
			String rawRest, String decodedRest) throws IOException, ServletException {
		Optional<ContractTag> asked = requestedTag(request);
		if (asked.isEmpty()) {
			refuse(response, HttpServletResponse.SC_BAD_REQUEST, ContractRefusalToken.CONTRACT_HEADER_INVALID,
				"Send exactly one " + ContractHeaders.CONTRACT + " header naming a contract tag (x.y.z)");
			return;
		}
		ContractTag tag = asked.get();
		if (!registry.isLive(tag)) {
			refuse(response, HttpServletResponse.SC_NOT_FOUND, ContractRefusalToken.UNKNOWN_VERSION,
				"No contract version " + tag + " is served here");
			return;
		}
		int slash = decodedRest.indexOf('/');
		String segment = slash < 0 ? decodedRest : decodedRest.substring(0, slash);
		if (!registry.serves(tag, segment)) {
			refuse(response, HttpServletResponse.SC_NOT_FOUND, ContractRefusalToken.SURFACE_NOT_SERVED,
				"Contract version " + tag + " does not serve " + segment);
			return;
		}
		request.setAttribute(ContractHeaders.NEGOTIATED_ATTRIBUTE, tag.toString());
		response.setHeader(ContractHeaders.CONTRACT, tag.toString());
		if (paths.mode() instanceof NegotiationMode.Rewrite rewrite) {
			SPRING_PATH_CACHES.forEach(request::removeAttribute);
			String tagged = rewrite.taggedPrefix() + tag + "/";
			chain.doFilter(new TaggedPathRequest(request, request.getContextPath() + tagged + rawRest,
				tagged + decodedRest), response);
			return;
		}
		chain.doFilter(request, response);
	}

	/** The tag, when the request carries exactly one header and it spells one. */
	private static Optional<ContractTag> requestedTag(HttpServletRequest request) {
		List<String> values = Collections.list(request.getHeaders(ContractHeaders.CONTRACT));
		if (values.size() != 1) {
			return Optional.empty();
		}
		return ContractTag.parse(values.getFirst().strip());
	}

	/**
	 * Answers a refusal, labelled. The header is set before {@code sendError} and survives it: headers are not part of
	 * the buffer {@code sendError} clears, and the container's error dispatch does not reset the response.
	 */
	private static void refuse(HttpServletResponse response, int status, ContractRefusalToken refusal, String message)
			throws IOException {
		response.setHeader(ContractHeaders.REFUSAL, refusal.token());
		response.sendError(status, message);
	}

	/** The request's path within the application, without the context path; not decoded. */
	private static String pathWithinApplication(HttpServletRequest request) {
		String uri = request.getRequestURI();
		String context = request.getContextPath();
		return context.isEmpty() || !uri.startsWith(context) ? uri : uri.substring(context.length());
	}

	/** The decoded, normalised path the container mapped: servlet path and path info. */
	private static String decodedPath(HttpServletRequest request) {
		String info = request.getPathInfo();
		return request.getServletPath() + (info == null ? "" : info);
	}

	/**
	 * The handshake as JSON. Written here rather than by a mapper so that the library carries no JSON dependency; every
	 * value is safe as it is (the service name is held to {@link #SERVICE}, the tags are digits and dots).
	 */
	static String json(ContractHandshake handshake) {
		StringBuilder json = new StringBuilder(64 + handshake.tags().size() * 10)
			.append("{\"protocol\":").append(handshake.protocol())
			.append(",\"service\":\"").append(handshake.service()).append("\",\"tags\":[");
		for (int index = 0; index < handshake.tags().size(); index++) {
			json.append(index == 0 ? "\"" : ",\"").append(handshake.tags().get(index)).append('"');
		}
		return json.append("]}").toString();
	}
}
