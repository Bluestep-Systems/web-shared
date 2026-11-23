package dev.bluestep.contract.server;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import dev.bluestep.contract.ContractHandshake;
import dev.bluestep.contract.ContractHeaders;
import dev.bluestep.contract.ContractTag;
import jakarta.servlet.DispatcherType;
import jakarta.servlet.Filter;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import tools.jackson.databind.json.JsonMapper;

/**
 * {@link NegotiatedContractFilter} over a mock chain, in both modes: the handshake and its verbs, every refusal, the
 * rewrite and the mark, and everything it must leave alone.
 */
class NegotiatedContractFilterTest {

	/** Two live tags; 6.6.0 adds a segment 6.5.0 lacks. 6.4.0 is retired. */
	static final ContractRegistry REGISTRY = new ContractRegistry() {
		private final Map<ContractTag, Set<String>> served = Map.of(
			ContractTag.of("6.5.0"), Set.of("registry", "self-tokens"),
			ContractTag.of("6.6.0"), Set.of("registry", "self-tokens", "tenant-knowledge-base-rows"));

		@Override
		public String service() {
			return "web-global";
		}

		@Override
		public List<ContractTag> liveTags() {
			return List.copyOf(served.keySet());
		}

		@Override
		public boolean serves(ContractTag tag, String segment) {
			return served.getOrDefault(tag, Set.of()).contains(segment);
		}
	};

	static final NegotiatedContractPaths WEB_GLOBAL = NegotiatedContractPaths.rewriting("/api/contract",
		"/api/contract/", "/api/v");
	static final NegotiatedContractPaths HARNESS = NegotiatedContractPaths.attributing("/harness/contract",
		"/harness/");

	/** The request the chain's servlet saw, when the chain was reached at all. */
	final AtomicReference<HttpServletRequest> reached = new AtomicReference<>();

	/** As Tomcat presents a request to a dispatcher mapped to "/": servlet path = the decoded path, no path info. */
	static MockHttpServletRequest request(String method, String uri) {
		MockHttpServletRequest request = new MockHttpServletRequest(method, uri);
		request.setServletPath(uri);
		return request;
	}

	MockHttpServletResponse run(NegotiatedContractPaths paths, MockHttpServletRequest request)
			throws IOException, ServletException {
		MockHttpServletResponse response = new MockHttpServletResponse();
		new NegotiatedContractFilter(REGISTRY, paths).doFilter(request, response, new MockFilterChain(new HttpServlet() {
			@Override
			protected void service(HttpServletRequest seen, HttpServletResponse answer) {
				reached.set(seen);
				answer.setStatus(200);
			}
		}));
		return response;
	}

	static MockHttpServletRequest negotiated(String uri, String... tags) {
		MockHttpServletRequest request = request("GET", uri);
		for (String tag : tags) {
			request.addHeader(ContractHeaders.CONTRACT, tag);
		}
		return request;
	}

	static void assertRefused(MockHttpServletResponse response, int status, String token) {
		assertThat(response.getStatus()).isEqualTo(status);
		assertThat(response.getHeader(ContractHeaders.REFUSAL)).isEqualTo(token);
		assertThat(response.getHeader(ContractHeaders.CONTRACT)).isNull();
	}

	@Nested
	class Handshake {

		@Test
		void getAnswersEveryLiveTagNewestFirstAsJsonThatBindsToTheRecord() throws Exception {
			MockHttpServletResponse response = run(WEB_GLOBAL, request("GET", "/api/contract"));
			assertThat(response.getStatus()).isEqualTo(200);
			assertThat(response.getHeader("Cache-Control")).isEqualTo("no-store");
			assertThat(response.getContentType()).startsWith("application/json");
			assertThat(response.getContentAsString())
				.isEqualTo("{\"protocol\":1,\"service\":\"web-global\",\"tags\":[\"6.6.0\",\"6.5.0\"]}");
			ContractHandshake bound = JsonMapper.builder().build().readValue(response.getContentAsString(),
				ContractHandshake.class);
			assertThat(bound).isEqualTo(new ContractHandshake(1, "web-global", List.of("6.6.0", "6.5.0")));
			assertThat(reached.get()).as("the handshake is answered by the filter").isNull();
		}

		@Test
		void anEmptyRegistryAnswersAnEmptyList() throws Exception {
			ContractRegistry empty = new ContractRegistry() {
				@Override
				public String service() {
					return "web-harness";
				}

				@Override
				public List<ContractTag> liveTags() {
					return List.of();
				}

				@Override
				public boolean serves(ContractTag tag, String segment) {
					return false;
				}
			};
			MockHttpServletResponse response = new MockHttpServletResponse();
			new NegotiatedContractFilter(empty, HARNESS).doFilter(request("GET", "/harness/contract"), response,
				new MockFilterChain());
			assertThat(response.getContentAsString()).isEqualTo("{\"protocol\":1,\"service\":\"web-harness\",\"tags\":[]}");
		}

		@Test
		void anyOtherVerbIs405AllowGet() throws Exception {
			for (String method : List.of("POST", "PUT", "DELETE", "PATCH", "HEAD", "OPTIONS")) {
				MockHttpServletResponse response = run(WEB_GLOBAL, request(method, "/api/contract"));
				assertThat(response.getStatus()).as(method).isEqualTo(405);
				assertThat(response.getHeader("Allow")).as(method).isEqualTo("GET");
				assertThat(reached.get()).as(method).isNull();
			}
		}

		@Test
		void isAnsweredUnderAContextPathAndInAttributeMode() throws Exception {
			MockHttpServletRequest request = request("GET", "/ctx/harness/contract");
			request.setContextPath("/ctx");
			request.setServletPath("/harness/contract");
			assertThat(run(HARNESS, request).getStatus()).isEqualTo(200);
		}

		@Test
		void needsNoContractHeader() throws Exception {
			MockHttpServletRequest request = request("GET", "/api/contract");
			request.addHeader(ContractHeaders.CONTRACT, "nonsense");
			assertThat(run(WEB_GLOBAL, request).getStatus()).isEqualTo(200);
		}
	}

	@Nested
	class Refusals {

		@Test
		void aMissingHeaderIs400() throws Exception {
			assertRefused(run(WEB_GLOBAL, negotiated("/api/contract/registry/x")), 400, "contract-header-invalid");
			assertThat(reached.get()).isNull();
		}

		@Test
		void aRepeatedHeaderIs400EvenWhenBothAgree() throws Exception {
			assertRefused(run(WEB_GLOBAL, negotiated("/api/contract/registry/x", "6.5.0", "6.5.0")), 400,
				"contract-header-invalid");
		}

		@Test
		void aHeaderThatIsNotATagIs400() throws Exception {
			for (String bad : List.of("", "6.5", "v6.5.0", "06.5.0", "6.5.0, 6.6.0", "latest")) {
				MockHttpServletResponse response = run(WEB_GLOBAL, negotiated("/api/contract/registry/x", bad));
				assertRefused(response, 400, "contract-header-invalid");
				assertThat(response.getErrorMessage()).as("the bad value is not echoed").doesNotContain("latest");
			}
		}

		@Test
		void surroundingSpaceIsNotPartOfTheTag() throws Exception {
			assertThat(run(WEB_GLOBAL, negotiated("/api/contract/registry/x", " 6.5.0 ")).getStatus()).isEqualTo(200);
		}

		@Test
		void aTagThatIsNotLiveIs404UnknownVersionNamingOnlyThatTag() throws Exception {
			for (String tag : List.of("6.4.0", "9.9.9")) {
				MockHttpServletResponse response = run(WEB_GLOBAL, negotiated("/api/contract/registry/x", tag));
				assertRefused(response, 404, "unknown-version");
				assertThat(response.getErrorMessage()).contains(tag).doesNotContain("6.6.0").doesNotContain("6.5.0");
			}
			assertThat(reached.get()).isNull();
		}

		@Test
		void aSegmentTheTagDoesNotServeIs404SurfaceNotServed() throws Exception {
			assertRefused(run(WEB_GLOBAL, negotiated("/api/contract/tenant-knowledge-base-rows/U1", "6.5.0")), 404,
				"surface-not-served");
			assertRefused(run(WEB_GLOBAL, negotiated("/api/contract/", "6.6.0")), 404, "surface-not-served");
			assertThat(run(WEB_GLOBAL, negotiated("/api/contract//registry", "6.6.0")).getStatus())
				.as("an empty segment is not canonical").isEqualTo(400);
			assertThat(reached.get()).isNull();
		}

		@Test
		void theHeaderIsCheckedBeforeTheTagAndTheTagBeforeTheSegment() throws Exception {
			assertRefused(run(WEB_GLOBAL, negotiated("/api/contract/nothing-here", "bad")), 400,
				"contract-header-invalid");
			assertRefused(run(WEB_GLOBAL, negotiated("/api/contract/nothing-here", "9.9.9")), 404, "unknown-version");
		}

		@Test
		void everyVerbIsRefusedTheSameWayIncludingOptions() throws Exception {
			for (String method : List.of("GET", "POST", "PUT", "DELETE", "OPTIONS", "HEAD")) {
				MockHttpServletRequest request = request(method, "/api/contract/registry/x");
				request.addHeader(ContractHeaders.CONTRACT, "9.9.9");
				assertRefused(run(WEB_GLOBAL, request), 404, "unknown-version");
			}
		}

		@Test
		void aPathUnderThePrefixInOnlyOneSpellingIs400() throws Exception {
			// Raw "/harness;v=1/x" is not under "/harness/", but the container mapped it as "/harness/x".
			MockHttpServletRequest semicolon = negotiated("/harness;v=1/tenant-knowledge-base-rows/U1", "1.0.0");
			semicolon.setServletPath("/harness/tenant-knowledge-base-rows/U1");
			MockHttpServletResponse response = run(HARNESS, semicolon);
			assertThat(response.getStatus()).isEqualTo(400);
			assertThat(reached.get()).isNull();
			MockHttpServletRequest encoded = negotiated("/api/%63ontract/registry/x", "6.6.0");
			encoded.setServletPath("/api/contract/registry/x");
			assertThat(run(WEB_GLOBAL, encoded).getStatus()).isEqualTo(400);
			assertThat(reached.get()).isNull();
		}
	}

	/** As Tomcat presents a raw URI: its servlet path is what the container decoded and normalised. */
	static MockHttpServletRequest mapped(String rawUri, String containerPath, String... tags) {
		MockHttpServletRequest request = negotiated(rawUri, tags);
		request.setServletPath(containerPath);
		return request;
	}

	@Nested
	class CanonicalPaths {

		/** Raw URI, the path the container mapped it to: each disagrees or is non-canonical, so each is a 400. */
		static final List<String[]> HARNESS_BYPASSES = List.of(
			new String[] {"/h%61rness/admin/../../outside", "/outside"},
			new String[] {"/h%61rness/tenant-knowledge-base-rows/U1", "/harness/tenant-knowledge-base-rows/U1"},
			new String[] {"/outside/../harness/tenant-knowledge-base-rows/U1", "/harness/tenant-knowledge-base-rows/U1"},
			new String[] {"/harness/./tenant-knowledge-base-rows/U1", "/harness/tenant-knowledge-base-rows/U1"},
			new String[] {"/harness/tenant-knowledge-base-rows/../admin", "/harness/admin"},
			new String[] {"/harness//tenant-knowledge-base-rows/U1", "/harness/tenant-knowledge-base-rows/U1"},
			new String[] {"/harness/tenant-knowledge-base-rows;a=1/U1", "/harness/tenant-knowledge-base-rows/U1"},
			new String[] {"/harness/%2e%2e/outside", "/outside"},
			new String[] {"/harness/tenant-knowledge-base-rows%2FU1", "/harness/tenant-knowledge-base-rows/U1"},
			new String[] {"/harness/tenant-knowledge-base-rows/%55" + "1", "/harness/tenant-knowledge-base-rows/U1"},
			new String[] {"/harness/tenant-knowledge-base-rows/%zz", "/harness/tenant-knowledge-base-rows/%zz"},
			new String[] {"/harness/tenant-knowledge-base-rows/U1", "/outside"},
			new String[] {"/outside", "/harness/tenant-knowledge-base-rows/U1"},
			new String[] {"/harness\\tenant-knowledge-base-rows/U1", "/harness/tenant-knowledge-base-rows/U1"},
			new String[] {"/h%61rness/contract", "/harness/contract"});

		static final List<String[]> WEB_GLOBAL_BYPASSES = List.of(
			new String[] {"/api/c%6Fntract/registry/x", "/api/contract/registry/x"},
			new String[] {"/api/%63ontract/admin/../../outside", "/outside"},
			new String[] {"/api/contract/registry/../../v6.5.0/registry/x", "/api/v6.5.0/registry/x"},
			new String[] {"/api/v6.5.0/../contract/registry/x", "/api/contract/registry/x"},
			new String[] {"/api/contract/registry;jsessionid=1/x", "/api/contract/registry/x"},
			new String[] {"/api/contract/./registry/x", "/api/contract/registry/x"},
			new String[] {"/api/c%6Fntract", "/api/contract"});

		@Test
		void theCodexExampleAndItsVariantsAreRefusedInAttributeMode() throws Exception {
			for (String[] bypass : HARNESS_BYPASSES) {
				reached.set(null);
				MockHttpServletResponse response = run(HARNESS, mapped(bypass[0], bypass[1], "999.0.0"));
				assertThat(response.getStatus()).as(bypass[0]).isEqualTo(400);
				assertThat(reached.get()).as(bypass[0]).isNull();
				assertThat(response.getHeaders("Vary")).as(bypass[0]).contains(ContractHeaders.CONTRACT);
			}
		}

		@Test
		void theVariantsAreRefusedInRewriteMode() throws Exception {
			for (String[] bypass : WEB_GLOBAL_BYPASSES) {
				reached.set(null);
				MockHttpServletResponse response = run(WEB_GLOBAL, mapped(bypass[0], bypass[1], "6.6.0"));
				assertThat(response.getStatus()).as(bypass[0]).isEqualTo(400);
				assertThat(reached.get()).as(bypass[0]).isNull();
			}
		}

		@Test
		void aNeedlesslyEncodedHandshakeIsRefusedToo() throws Exception {
			MockHttpServletResponse response = run(WEB_GLOBAL, mapped("/api/contr%61ct", "/api/contract"));
			assertThat(response.getStatus()).isEqualTo(400);
			assertThat(response.getContentAsString()).doesNotContain("6.6.0");
		}

		@Test
		void encodingOfAReservedOrNonAsciiCharacterIsCanonical() throws Exception {
			MockHttpServletResponse response = run(WEB_GLOBAL, mapped("/api/contract/registry/caf%C3%A9%20x",
				"/api/contract/registry/caf\u00e9 x", "6.6.0"));
			assertThat(response.getStatus()).isEqualTo(200);
			assertThat(reached.get().getRequestURI()).isEqualTo("/api/v6.6.0/registry/caf%C3%A9%20x");
		}

		@Test
		void canonicalityRules() {
			assertThat(NegotiatedContractFilter.isCanonical("/harness/tenant-knowledge-base-rows/U1")).isTrue();
			assertThat(NegotiatedContractFilter.isCanonical("/a/%20b/%C3%A9/%25")).isTrue();
			for (String bad : List.of("/a;b", "/a//b", "/a/./b", "/a/../b", "/a/.", "/a/..", "/%61", "/%2E", "/%2f",
					"/%5C", "/%3B", "/%7E", "/%2D", "/%5F", "/%", "/%4", "/%g0", "/a\\b")) {
				assertThat(NegotiatedContractFilter.isCanonical(bad)).as(bad).isFalse();
			}
		}

		@Test
		void decodingAndDotSegmentRemovalFollowTheRfc() {
			assertThat(NegotiatedContractFilter.percentDecoded("/h%61rness/%C3%A9+")).contains("/harness/\u00e9+");
			assertThat(NegotiatedContractFilter.percentDecoded("/%C3")).isEmpty();
			assertThat(NegotiatedContractFilter.percentDecoded("/%zz")).isEmpty();
			assertThat(NegotiatedContractFilter.removeDotSegments("/harness/admin/../../outside")).isEqualTo("/outside");
			assertThat(NegotiatedContractFilter.removeDotSegments("/../../x")).isEqualTo("/x");
			assertThat(NegotiatedContractFilter.removeDotSegments("/a/./b/.")).isEqualTo("/a/b/");
			assertThat(NegotiatedContractFilter.removeDotSegments("/a/b/..")).isEqualTo("/a/");
			assertThat(NegotiatedContractFilter.removeDotSegments("/a/b")).isEqualTo("/a/b");
		}
	}

	@Nested
	class VaryHeader {

		@Test
		void everyAnswerUnderTheProtocolVariesByTheContractHeader() throws Exception {
			List<MockHttpServletRequest> requests = List.of(
				negotiated("/api/contract/registry/x", "6.6.0"),
				negotiated("/api/contract/registry/x"),
				negotiated("/api/contract/registry/x", "9.9.9"),
				negotiated("/api/contract/tenant-knowledge-base-rows/U1", "6.5.0"),
				mapped("/api/c%6Fntract/registry/x", "/api/contract/registry/x", "6.6.0"),
				request("GET", "/api/contract"),
				request("POST", "/api/contract"));
			for (MockHttpServletRequest request : requests) {
				MockHttpServletResponse response = run(WEB_GLOBAL, request);
				assertThat(response.getHeaders("Vary")).as(request.getRequestURI() + " " + response.getStatus())
					.contains(ContractHeaders.CONTRACT);
			}
		}

		@Test
		void aVaryTheHandlerAddsIsKept() throws Exception {
			MockHttpServletResponse response = new MockHttpServletResponse();
			new NegotiatedContractFilter(REGISTRY, WEB_GLOBAL).doFilter(negotiated("/api/contract/registry/x", "6.6.0"),
				response, new MockFilterChain(new HttpServlet() {
					@Override
					protected void service(HttpServletRequest seen, HttpServletResponse answer) {
						answer.addHeader("Vary", "Origin");
					}
				}));
			assertThat(response.getHeaders("Vary")).containsExactly(ContractHeaders.CONTRACT, "Origin");
		}

		@Test
		void pathsOutsideTheProtocolGetNoVary() throws Exception {
			assertThat(run(WEB_GLOBAL, request("GET", "/api/v6.5.0/registry/x")).getHeader("Vary")).isNull();
		}
	}

	@Nested
	class RewriteMode {

		@Test
		void anAcceptedRequestReachesTheChainAsTheTaggedPath() throws Exception {
			MockHttpServletRequest request = negotiated("/api/contract/registry/tenants/by-host/a.example", "6.6.0");
			request.setQueryString("x=1");
			MockHttpServletResponse response = run(WEB_GLOBAL, request);
			assertThat(response.getStatus()).isEqualTo(200);
			assertThat(response.getHeader(ContractHeaders.CONTRACT)).isEqualTo("6.6.0");
			HttpServletRequest seen = reached.get();
			assertThat(seen.getRequestURI()).isEqualTo("/api/v6.6.0/registry/tenants/by-host/a.example");
			assertThat(seen.getServletPath()).isEqualTo("/api/v6.6.0/registry/tenants/by-host/a.example");
			assertThat(seen.getPathInfo()).isNull();
			assertThat(seen.getRequestURL()).hasToString("http://localhost/api/v6.6.0/registry/tenants/by-host/a.example");
			assertThat(seen.getQueryString()).isEqualTo("x=1");
			assertThat(seen.getHeader(ContractHeaders.CONTRACT)).isEqualTo("6.6.0");
			assertThat(seen.getAttribute(ContractHeaders.NEGOTIATED_ATTRIBUTE)).isEqualTo("6.6.0");
		}

		@Test
		void keepsTheContextPathAndTheRawEncodingOfTheRest() throws Exception {
			MockHttpServletRequest request = negotiated("/ctx/api/contract/registry/a%20b", "6.5.0");
			request.setContextPath("/ctx");
			request.setServletPath("/api/contract/registry/a b");
			run(WEB_GLOBAL, request);
			HttpServletRequest seen = reached.get();
			assertThat(seen.getRequestURI()).isEqualTo("/ctx/api/v6.5.0/registry/a%20b");
			assertThat(seen.getContextPath()).isEqualTo("/ctx");
			assertThat(seen.getServletPath()).isEqualTo("/api/v6.5.0/registry/a b");
		}

		@Test
		void aBareSegmentIsRewrittenToo() throws Exception {
			run(WEB_GLOBAL, negotiated("/api/contract/self-tokens", "6.6.0"));
			assertThat(reached.get().getRequestURI()).isEqualTo("/api/v6.6.0/self-tokens");
		}

		@Test
		void dropsSpringsCachedPathsBeforeTheRewrite() throws Exception {
			MockHttpServletRequest request = negotiated("/api/contract/registry/x", "6.6.0");
			request.setAttribute("org.springframework.web.util.ServletRequestPathUtils.PATH", "parsed stable path");
			request.setAttribute("org.springframework.web.util.UrlPathHelper.PATH", "/api/contract/registry/x");
			run(WEB_GLOBAL, request);
			assertThat(reached.get().getAttribute("org.springframework.web.util.ServletRequestPathUtils.PATH")).isNull();
			assertThat(reached.get().getAttribute("org.springframework.web.util.UrlPathHelper.PATH")).isNull();
		}
	}

	@Nested
	class AttributeMode {

		@Test
		void anAcceptedRequestIsMarkedAndPassedOnUnchanged() throws Exception {
			ContractRegistry harness = new ContractRegistry() {
				@Override
				public String service() {
					return "web-harness";
				}

				@Override
				public List<ContractTag> liveTags() {
					return List.of(ContractTag.of("1.0.0"));
				}

				@Override
				public boolean serves(ContractTag tag, String segment) {
					return segment.equals("tenant-knowledge-base-rows");
				}
			};
			MockHttpServletRequest request = negotiated("/harness/tenant-knowledge-base-rows/U137803", "1.0.0");
			MockHttpServletResponse response = new MockHttpServletResponse();
			MockFilterChain chain = new MockFilterChain();
			new NegotiatedContractFilter(harness, HARNESS).doFilter(request, response, chain);
			assertThat(chain.getRequest()).isSameAs(request);
			assertThat(request.getRequestURI()).isEqualTo("/harness/tenant-knowledge-base-rows/U137803");
			assertThat(request.getAttribute(ContractHeaders.NEGOTIATED_ATTRIBUTE)).isEqualTo("1.0.0");
			assertThat(response.getHeader(ContractHeaders.CONTRACT)).isEqualTo("1.0.0");
		}

		@Test
		void theHandshakePathUnderTheStablePrefixIsTheHandshakeNotASegment() throws Exception {
			assertThat(run(HARNESS, request("GET", "/harness/contract")).getStatus()).isEqualTo(200);
			assertRefused(run(HARNESS, negotiated("/harness/contract/x", "6.6.0")), 404, "surface-not-served");
		}
	}

	@Nested
	class LeftAlone {

		@Test
		void pathsOutsideTheProtocolPassThroughUntouched() throws Exception {
			for (String uri : List.of("/api/v6.5.0/registry/x", "/api/contracts", "/api/contractual/x", "/actuator/health",
					"/api/v1/ai", "/")) {
				reached.set(null);
				MockHttpServletRequest request = request("GET", uri);
				request.addHeader(ContractHeaders.CONTRACT, "nonsense");
				MockHttpServletResponse response = run(WEB_GLOBAL, request);
				assertThat(response.getStatus()).as(uri).isEqualTo(200);
				assertThat(response.getHeader(ContractHeaders.REFUSAL)).as(uri).isNull();
				assertThat(reached.get()).as(uri).isSameAs(request);
				assertThat(request.getAttribute(ContractHeaders.NEGOTIATED_ATTRIBUTE)).as(uri).isNull();
			}
		}

		@Test
		void anErrorOrForwardDispatchPassesThrough() throws Exception {
			for (DispatcherType type : List.of(DispatcherType.ERROR, DispatcherType.FORWARD, DispatcherType.ASYNC)) {
				reached.set(null);
				MockHttpServletRequest request = negotiated("/api/contract/registry/x");
				request.setDispatcherType(type);
				assertThat(run(WEB_GLOBAL, request).getStatus()).as(type.name()).isEqualTo(200);
				assertThat(reached.get()).as(type.name()).isSameAs(request);
			}
		}

		@Test
		void aRequestRefusedByAuthenticationAheadOfItIsNeverEvaluated() throws Exception {
			Filter authentication = new Filter() {
				@Override
				public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
						throws IOException {
					((HttpServletResponse) response).sendError(401);
				}
			};
			MockHttpServletResponse response = new MockHttpServletResponse();
			MockHttpServletRequest request = negotiated("/api/contract/registry/x", "9.9.9");
			new MockFilterChain(new HttpServlet() {
			}, authentication, new NegotiatedContractFilter(REGISTRY, WEB_GLOBAL)).doFilter(request, response);
			assertThat(response.getStatus()).isEqualTo(401);
			assertThat(response.getHeader(ContractHeaders.REFUSAL)).isNull();
			MockHttpServletResponse handshake = new MockHttpServletResponse();
			new MockFilterChain(new HttpServlet() {
			}, authentication, new NegotiatedContractFilter(REGISTRY, WEB_GLOBAL))
				.doFilter(request("GET", "/api/contract"), handshake);
			assertThat(handshake.getStatus()).isEqualTo(401);
			assertThat(handshake.getContentAsString()).doesNotContain("6.6.0");
		}
	}

	@Nested
	class Construction {

		@Test
		void pathsAreHeldToTheirShapes() {
			assertThatThrownBy(() -> NegotiatedContractPaths.rewriting("api/contract", "/api/contract/", "/api/v"))
				.isInstanceOf(IllegalArgumentException.class);
			assertThatThrownBy(() -> NegotiatedContractPaths.rewriting("/api/contract/", "/api/contract/", "/api/v"))
				.isInstanceOf(IllegalArgumentException.class);
			assertThatThrownBy(() -> NegotiatedContractPaths.rewriting("/api/contract", "/api/contract", "/api/v"))
				.isInstanceOf(IllegalArgumentException.class);
			assertThatThrownBy(() -> NegotiatedContractPaths.rewriting("/api/contract", "/", "/api/v"))
				.isInstanceOf(IllegalArgumentException.class);
			assertThatThrownBy(() -> NegotiatedContractPaths.rewriting("/api/contract", "/api/contract/", "/api/v/"))
				.isInstanceOf(IllegalArgumentException.class);
			assertThatThrownBy(() -> NegotiatedContractPaths.attributing("/harness/contract?x", "/harness/"))
				.isInstanceOf(IllegalArgumentException.class);
		}

		@Test
		void theServiceNameMustBeSafeToWriteAsItIs() {
			ContractRegistry quoted = new ContractRegistry() {
				@Override
				public String service() {
					return "web\"global";
				}

				@Override
				public List<ContractTag> liveTags() {
					return List.of();
				}

				@Override
				public boolean serves(ContractTag tag, String segment) {
					return false;
				}
			};
			assertThatThrownBy(() -> new NegotiatedContractFilter(quoted, WEB_GLOBAL))
				.isInstanceOf(IllegalArgumentException.class);
		}
	}
}
