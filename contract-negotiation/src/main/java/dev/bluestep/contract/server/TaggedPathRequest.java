package dev.bluestep.contract.server;

import org.jspecify.annotations.Nullable;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletRequestWrapper;

/**
 * An accepted stable-path request as the tagged path it stands for: {@code <stable prefix>x/y} reads as
 * {@code <tagged prefix><tag>/x/y} in its request URI, request URL and servlet path, so the dispatcher matches the
 * path-versioned handlers. Everything else (headers, query, body, attributes, context path) is the original request's.
 *
 * <p>For a dispatcher mapped to {@code /}: the servlet path is the whole decoded path within the application and the
 * path info is null.</p>
 */
final class TaggedPathRequest extends HttpServletRequestWrapper {

	private final String requestUri;
	private final String servletPath;

	/**
	 * @param request     the accepted request
	 * @param requestUri  the tagged request URI, raw, context path included
	 * @param servletPath the tagged path within the application, decoded
	 */
	TaggedPathRequest(HttpServletRequest request, String requestUri, String servletPath) {
		super(request);
		this.requestUri = requestUri;
		this.servletPath = servletPath;
	}

	@Override
	public String getRequestURI() {
		return requestUri;
	}

	@Override
	public StringBuffer getRequestURL() {
		String original = super.getRequestURL().toString();
		String originalUri = super.getRequestURI();
		if (!original.endsWith(originalUri)) {
			// Never seen from a container: the URL is the origin followed by the URI. Leave it as it was.
			return new StringBuffer(original);
		}
		return new StringBuffer(original.substring(0, original.length() - originalUri.length())).append(requestUri);
	}

	@Override
	public String getServletPath() {
		return servletPath;
	}

	@Override
	public @Nullable String getPathInfo() {
		return null;
	}
}
