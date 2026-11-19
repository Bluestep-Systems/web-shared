package dev.bluestep.global.dto.consoleglobaluser;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Instant;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import dev.bluestep.global.dto.globaluser.CatalogScopeKind;
import dev.bluestep.global.dto.globaluserrecord.LinkedAccount;
import dev.bluestep.global.dto.tenantaccess.GlobalUserKey;
import dev.bluestep.global.dto.tenantaccess.ResellerKey;
import dev.bluestep.global.dto.tenantaccess.TenantAccessGrantSource;
import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

/** The 6.13.0 console global-user shapes. Every fixture is fictional. */
@DisplayName("6.13.0 console global-user shapes")
class ConsoleGlobalUserWireTest {

	private static final ObjectMapper JSON = JsonMapper.builder()
			.enable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
			.build();

	private static final GlobalUserKey ALICE = new GlobalUserKey(222_222L, 9_001L);
	private static final GlobalUserKey BOB = new GlobalUserKey(222_222L, 9_002L);
	private static final ResellerKey RESELLER = new ResellerKey(333_333L, 7L);
	private static final UUID TOKEN = UUID.fromString("00000000-0000-4000-8000-000000000001");

	private static ConsoleGlobalUser alice() {
		return new ConsoleGlobalUser(ALICE, "Alice", "Example", "alice.example", "alice@example.test", true, true,
				new ConsoleReseller(RESELLER, "Example Reseller"), false, LocalDateTime.parse("2026-01-02T03:04:05"),
				LocalDateTime.parse("2026-01-01T00:00:00"), null, new ConsoleActor(BOB, "Bob Example"), null, "3-17",
				List.of(new ConsoleLinkedAccount("https://login.microsoftonline.com/t/v2.0",
						LinkedAccountProvider.MICROSOFT, "wxyz")));
	}

	@Test
	@DisplayName("a detail has exactly the documented wire and round trips")
	void wire() {
		ConsoleGlobalUserDetail detail = new ConsoleGlobalUserDetail(alice(),
				List.of(new ConsoleAccessToken(TOKEN, "laptop", List.of("mcp", "kb:read"),
						Instant.parse("2026-02-01T00:00:00Z"), null, Instant.parse("2026-02-02T00:00:00Z"), true)),
				List.of(new ConsoleTenantAccess("U137803", Instant.parse("2026-03-01T00:00:00Z"), null,
						TenantAccessGrantSource.SUPER)),
				new ConsoleHistoryManagerGrant(Instant.parse("2026-04-01T00:00:00Z"), new ConsoleActor(BOB, null)),
				new ConsoleCatalogScope(CatalogScopeKind.RESELLER, new ConsoleReseller(RESELLER, null), "seeded",
						Instant.parse("2026-05-01T00:00:00Z")));

		String json = JSON.writeValueAsString(detail);

		assertEquals("{\"user\":{\"user\":{\"classid\":222222,\"seqnum\":9001},\"firstName\":\"Alice\","
				+ "\"lastName\":\"Example\",\"username\":\"alice.example\",\"email\":\"alice@example.test\","
				+ "\"emailValidated\":true,\"superUser\":true,"
				+ "\"reseller\":{\"reseller\":{\"classid\":333333,\"seqnum\":7},\"name\":\"Example Reseller\"},"
				+ "\"disabled\":false,\"passwordExpireStart\":\"2026-01-02T03:04:05\","
				+ "\"createdAt\":\"2026-01-01T00:00:00\",\"modifiedAt\":null,"
				+ "\"createdBy\":{\"user\":{\"classid\":222222,\"seqnum\":9002},\"name\":\"Bob Example\"},"
				+ "\"modifiedBy\":null,\"version\":\"3-17\","
				+ "\"linkedAccounts\":[{\"issuer\":\"https://login.microsoftonline.com/t/v2.0\","
				+ "\"provider\":\"MICROSOFT\",\"subjectHint\":\"wxyz\"}]},"
				+ "\"accessTokens\":[{\"id\":\"00000000-0000-4000-8000-000000000001\",\"alias\":\"laptop\","
				+ "\"scopes\":[\"mcp\",\"kb:read\"],\"createdAt\":\"2026-02-01T00:00:00Z\",\"expiresAt\":null,"
				+ "\"lastUsedAt\":\"2026-02-02T00:00:00Z\",\"live\":true}],"
				+ "\"tenantAccess\":[{\"tenantId\":\"U137803\",\"grantedAt\":\"2026-03-01T00:00:00Z\","
				+ "\"expiresAt\":null,\"source\":\"SUPER\"}],"
				+ "\"historyManager\":{\"grantedAt\":\"2026-04-01T00:00:00Z\","
				+ "\"grantedBy\":{\"user\":{\"classid\":222222,\"seqnum\":9002},\"name\":null}},"
				+ "\"catalogScope\":{\"kind\":\"RESELLER\",\"reseller\":{\"reseller\":{\"classid\":333333,"
				+ "\"seqnum\":7},\"name\":null},\"reason\":\"seeded\",\"recordedAt\":\"2026-05-01T00:00:00Z\"}}", json);
		assertEquals(detail, roundTrip(json, ConsoleGlobalUserDetail.class));
		assertNoSecretShapedMember(JSON.readTree(json));
	}

	@Test
	@DisplayName("a page round trips, and an unreadable linked-account list stays absent, never empty")
	void page() {
		ConsoleGlobalUser bare = new ConsoleGlobalUser(BOB, null, null, null, null, false, false, null, true, null,
				null, null, null, null, "0", null);
		ConsoleGlobalUserPage page = new ConsoleGlobalUserPage(List.of(alice(), bare), 7, true, "opaque");

		String json = JSON.writeValueAsString(page);
		ConsoleGlobalUserPage read = JSON.readValue(json, ConsoleGlobalUserPage.class);

		assertEquals(page, read);
		assertTrue(read.users().get(1).optLinkedAccounts().isEmpty());
		assertTrue(JSON.readTree(json).path("users").get(1).get("linkedAccounts").isNull());
		assertEquals(List.of(), new ConsoleGlobalUser(BOB, null, null, null, null, false, false, null, false, null,
				null, null, null, null, "0", List.of()).optLinkedAccounts().orElseThrow());
		assertTrue(new ConsoleGlobalUserPage(List.of(), 0, false, null).optEndCursor().isEmpty());
		assertNoSecretShapedMember(JSON.readTree(json));
	}

	@Test
	@DisplayName("a linked account carries the subject's last four characters, never the subject")
	void subjectHint() {
		LinkedAccount microsoft = new LinkedAccount("https://login.microsoftonline.com/tid/v2.0",
				"AAAAAAAAAAAAAAAAAAAAAKfictionalSubjectQ9z");
		ConsoleLinkedAccount shown = ConsoleLinkedAccount.of(microsoft);

		assertEquals("tQ9z", shown.subjectHint());
		assertEquals(LinkedAccountProvider.MICROSOFT, shown.provider());
		assertFalse(JSON.writeValueAsString(shown).contains(microsoft.sub()));
		// Too short to hint at: four would be most of it.
		assertEquals("", ConsoleLinkedAccount.of(new LinkedAccount("https://accounts.google.com", "1234567"))
				.subjectHint());
		assertEquals("5678", ConsoleLinkedAccount.of(new LinkedAccount("https://accounts.google.com", "12345678"))
				.subjectHint());
		// Code points, not chars: a surrogate pair is one character and is never split.
		assertEquals("b😀cd", ConsoleLinkedAccount.hint("aaaaaaab😀cd"));
		assertThrows(IllegalArgumentException.class,
				() -> new ConsoleLinkedAccount("https://x.example", LinkedAccountProvider.OTHER, "12345"));
		assertThrows(RuntimeException.class, () -> JSON.readValue(
				"{\"issuer\":\"https://x.example\",\"provider\":\"OTHER\",\"subjectHint\":\"the-whole-subject\"}",
				ConsoleLinkedAccount.class));
	}

	@Test
	@DisplayName("the provider is decided by the issuer's host alone")
	void provider() {
		assertEquals(LinkedAccountProvider.MICROSOFT, LinkedAccountProvider.of("https://login.microsoftonline.com/x/v2.0"));
		assertEquals(LinkedAccountProvider.MICROSOFT, LinkedAccountProvider.of("https://sts.windows.net/x/"));
		assertEquals(LinkedAccountProvider.MICROSOFT, LinkedAccountProvider.of("HTTPS://LOGIN.MICROSOFTONLINE.COM/x"));
		assertEquals(LinkedAccountProvider.GOOGLE, LinkedAccountProvider.of("https://accounts.google.com"));
		assertEquals(LinkedAccountProvider.GOOGLE, LinkedAccountProvider.of("accounts.google.com"));
		assertEquals(LinkedAccountProvider.OTHER, LinkedAccountProvider.of("https://accounts.google.com.evil.example"));
		assertEquals(LinkedAccountProvider.OTHER, LinkedAccountProvider.of("https://idp.example.test"));
		assertEquals(LinkedAccountProvider.OTHER, LinkedAccountProvider.of("not a uri at all"));
		assertEquals(LinkedAccountProvider.OTHER, LinkedAccountProvider.of(""));
	}

	@Test
	@DisplayName("required members, a scope's reseller rule, a negative count and mutated lists are refused")
	void refusals() {
		assertThrows(NullPointerException.class, () -> new ConsoleActor(null, "x"));
		assertThrows(NullPointerException.class, () -> new ConsoleReseller(null, "x"));
		assertThrows(IllegalArgumentException.class, () -> new ConsoleCatalogScope(CatalogScopeKind.FLEET,
				new ConsoleReseller(RESELLER, "r"), "x", Instant.EPOCH));
		assertThrows(IllegalArgumentException.class, () -> new ConsoleCatalogScope(CatalogScopeKind.RESELLER, null,
				"x", Instant.EPOCH));
		assertThrows(IllegalArgumentException.class, () -> new ConsoleGlobalUserPage(List.of(), -1, false, null));
		assertThrows(RuntimeException.class, () -> JSON.readValue("{\"users\":[],\"totalCount\":0,"
				+ "\"hasNextPage\":false,\"endCursor\":null,\"encodedtoken\":\"x\"}", ConsoleGlobalUserPage.class));
		List<String> scopes = new ArrayList<>(List.of("mcp"));
		ConsoleAccessToken token = new ConsoleAccessToken(TOKEN, "a", scopes, null, null, null, false);
		scopes.add("kb:read");
		assertEquals(List.of("mcp"), token.scopes());
		assertThrows(UnsupportedOperationException.class, () -> token.scopes().add("x"));
		ConsoleGlobalUserPage page = new ConsoleGlobalUserPage(List.of(), 0, false, null);
		assertThrows(UnsupportedOperationException.class, () -> page.users().add(alice()));
	}

	private static <T> T roundTrip(String json, Class<T> type) {
		return JSON.readValue(json, type);
	}

	/** No member anywhere is named as a secret, a token hash, a credential, a subject or the attribs cipher is. */
	static void assertNoSecretShapedMember(JsonNode node) {
		if (node.isObject()) {
			for (String name : node.propertyNames()) {
				String lower = name.toLowerCase(Locale.ROOT);
				for (String banned : List.of("hash", "encoded", "token\"", "secret", "password\"", "credential",
						"stored", "attribs", "sub\"", "subject\"", "cipher")) {
					assertFalse((lower + "\"").contains(banned), () -> "member " + name + " looks like " + banned);
				}
				assertNoSecretShapedMember(node.get(name));
			}
		}
		else if (node.isArray()) {
			node.forEach(ConsoleGlobalUserWireTest::assertNoSecretShapedMember);
		}
	}
}
