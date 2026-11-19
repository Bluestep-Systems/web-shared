package dev.bluestep.global.dto.myaccount;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Instant;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import dev.bluestep.global.dto.consoleglobaluser.ConsoleAccessToken;
import dev.bluestep.global.dto.consoleglobaluser.ConsoleLinkedAccount;
import dev.bluestep.global.dto.consoleglobaluser.ConsoleReseller;
import dev.bluestep.global.dto.consoleglobaluser.LinkedAccountProvider;
import dev.bluestep.global.dto.globaluser.CatalogScopeKind;
import dev.bluestep.global.dto.tenantaccess.GlobalUserKey;
import dev.bluestep.global.dto.tenantaccess.ResellerKey;
import dev.bluestep.global.dto.tenantaccess.TenantAccessGrantSource;
import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

/** The 6.14.0 My Account shapes. Every fixture is fictional. */
@DisplayName("6.14.0 my-account shapes")
class MyAccountWireTest {

	private static final ObjectMapper JSON = JsonMapper.builder()
			.enable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
			.build();

	private static final GlobalUserKey ALICE = new GlobalUserKey(222_222L, -9_001L);
	private static final ResellerKey RESELLER = new ResellerKey(333_333L, 7L);
	private static final UUID TOKEN = UUID.fromString("00000000-0000-4000-8000-000000000001");

	private static MyAccountResponse alice() {
		return new MyAccountResponse(ALICE, "Alice", "Example", "alice.example", "alice@example.test", true,
				LocalDateTime.parse("2026-01-02T03:04:05"),
				List.of(new ConsoleLinkedAccount("https://login.microsoftonline.com/t/v2.0",
						LinkedAccountProvider.MICROSOFT, "wxyz")),
				List.of(new ConsoleAccessToken(TOKEN, "laptop", List.of("mcp", "kb:read"),
						Instant.parse("2026-02-01T00:00:00Z"), null, Instant.parse("2026-02-02T00:00:00Z"), true)),
				true,
				List.of(new MyTenantAccess("U137803", "Example Clinic", Instant.parse("2026-03-01T00:00:00Z"), null,
						TenantAccessGrantSource.SUPER), new MyTenantAccess("U137804", null,
						Instant.parse("2026-03-02T00:00:00Z"), Instant.parse("2026-04-01T00:00:00Z"),
						TenantAccessGrantSource.TENANT)),
				false, Instant.parse("2026-04-01T00:00:00Z"),
				new MyCatalogScope(CatalogScopeKind.RESELLER, new ConsoleReseller(RESELLER, "Example Reseller"),
						Instant.parse("2026-05-01T00:00:00Z")),
				List.of("mcp", "kb:read"));
	}

	@Test
	@DisplayName("the account has exactly the documented wire and round trips")
	void wire() {
		String json = JSON.writeValueAsString(alice());

		assertEquals("{\"user\":{\"classid\":222222,\"seqnum\":-9001},\"firstName\":\"Alice\",\"lastName\":\"Example\","
				+ "\"username\":\"alice.example\",\"email\":\"alice@example.test\",\"emailValidated\":true,"
				+ "\"passwordExpireStart\":\"2026-01-02T03:04:05\","
				+ "\"linkedAccounts\":[{\"issuer\":\"https://login.microsoftonline.com/t/v2.0\","
				+ "\"provider\":\"MICROSOFT\",\"subjectHint\":\"wxyz\"}],"
				+ "\"accessTokens\":[{\"id\":\"00000000-0000-4000-8000-000000000001\",\"alias\":\"laptop\","
				+ "\"scopes\":[\"mcp\",\"kb:read\"],\"createdAt\":\"2026-02-01T00:00:00Z\",\"expiresAt\":null,"
				+ "\"lastUsedAt\":\"2026-02-02T00:00:00Z\",\"live\":true}],\"accessTokensTruncated\":true,"
				+ "\"tenantAccess\":[{\"tenantId\":\"U137803\",\"tenantName\":\"Example Clinic\","
				+ "\"grantedAt\":\"2026-03-01T00:00:00Z\",\"expiresAt\":null,\"source\":\"SUPER\"},"
				+ "{\"tenantId\":\"U137804\",\"tenantName\":null,\"grantedAt\":\"2026-03-02T00:00:00Z\","
				+ "\"expiresAt\":\"2026-04-01T00:00:00Z\",\"source\":\"TENANT\"}],\"tenantAccessTruncated\":false,"
				+ "\"historyManagerSince\":\"2026-04-01T00:00:00Z\","
				+ "\"catalogScope\":{\"kind\":\"RESELLER\",\"reseller\":{\"reseller\":{\"classid\":333333,"
				+ "\"seqnum\":7},\"name\":\"Example Reseller\"},\"recordedAt\":\"2026-05-01T00:00:00Z\"},"
				+ "\"mintableScopes\":[\"mcp\",\"kb:read\"]}", json);
		assertEquals(alice(), JSON.readValue(json, MyAccountResponse.class));
		assertNoOtherPeoplesOrSecretMembers(JSON.readTree(json));
	}

	@Test
	@DisplayName("a bare account round trips; an unreadable linked-account list stays absent, never empty")
	void bare() {
		MyAccountResponse bare = new MyAccountResponse(ALICE, null, null, null, null, false, null, null, List.of(),
				false, List.of(), false, null, null, List.of("mcp"));
		String json = JSON.writeValueAsString(bare);
		MyAccountResponse read = JSON.readValue(json, MyAccountResponse.class);

		assertEquals(bare, read);
		assertTrue(read.optLinkedAccounts().isEmpty());
		assertTrue(JSON.readTree(json).get("linkedAccounts").isNull());
		assertTrue(read.optHistoryManagerSince().isEmpty());
		assertTrue(read.optCatalogScope().isEmpty());
		assertNoOtherPeoplesOrSecretMembers(JSON.readTree(json));
	}

	@Test
	@DisplayName("the acting header spells a key one way and reads back only that spelling")
	void actingHeader() {
		assertEquals("B6P-Acting-Global-User", MyAccountHeaders.ACTING_GLOBAL_USER_HEADER);
		assertEquals("222222:-9001", MyAccountHeaders.value(ALICE));
		assertEquals(Optional.of(ALICE), MyAccountHeaders.parse(MyAccountHeaders.value(ALICE)));
		assertEquals(Optional.of(new GlobalUserKey(7000, 1)), MyAccountHeaders.parse("7000:1"));
		for (String bad : List.of("", "222222", "222222:", ":1", " 222222:-1", "222222:-1 ", "222222 : -1",
				"+222222:-1", "222222:+1", "0222222:-1", "222222:-01", "222222:0", "0:1", "-222222:-1",
				"222222:-1:3", "222222:-99999999999999999999", "99999999999999999999:-1", "a:b", "222222:-1,7000:1")) {
			assertEquals(Optional.empty(), MyAccountHeaders.parse(bad), bad);
		}
	}

	@Test
	@DisplayName("a refusal's reason binds forward: an unknown or missing one is UNKNOWN")
	void refusal() {
		MyAccountRefusal disabled = new MyAccountRefusal(MyAccountRefusal.Reason.ACCOUNT_DISABLED, "off");
		String json = JSON.writeValueAsString(disabled);
		assertEquals("{\"reason\":\"ACCOUNT_DISABLED\",\"message\":\"off\"}", json);
		assertEquals(disabled, JSON.readValue(json, MyAccountRefusal.class));
		assertEquals(MyAccountRefusal.Reason.UNKNOWN,
				JSON.readValue("{\"reason\":\"STEP_UP_REQUIRED\",\"message\":\"x\"}", MyAccountRefusal.class).reason());
		assertEquals(MyAccountRefusal.Reason.UNKNOWN,
				JSON.readValue("{\"reason\":\"0\",\"message\":\"x\"}", MyAccountRefusal.class).reason());
		MyAccountRefusal empty = JSON.readValue("{}", MyAccountRefusal.class);
		assertEquals(MyAccountRefusal.Reason.UNKNOWN, empty.reason());
		assertEquals("", empty.message());
	}

	@Test
	@DisplayName("required members, a scope's reseller rule, unknown members and mutated lists are refused")
	void refusals() {
		assertThrows(NullPointerException.class, () -> new MyTenantAccess(null, "x", Instant.EPOCH, null,
				TenantAccessGrantSource.SUPER));
		assertThrows(NullPointerException.class, () -> new MyTenantAccess("U1", "x", Instant.EPOCH, null, null));
		assertThrows(IllegalArgumentException.class, () -> new MyCatalogScope(CatalogScopeKind.FLEET,
				new ConsoleReseller(RESELLER, "r"), Instant.EPOCH));
		assertThrows(IllegalArgumentException.class, () -> new MyCatalogScope(CatalogScopeKind.RESELLER, null,
				Instant.EPOCH));
		assertThrows(RuntimeException.class, () -> JSON.readValue(JSON.writeValueAsString(alice())
				.replace("\"mintableScopes\"", "\"superUser\":true,\"mintableScopes\""), MyAccountResponse.class));
		List<String> scopes = new ArrayList<>(List.of("mcp"));
		MyAccountResponse account = new MyAccountResponse(ALICE, null, null, null, null, false, null, null, List.of(),
				false, List.of(), false, null, null, scopes);
		scopes.add("kb:read");
		assertEquals(List.of("mcp"), account.mintableScopes());
		assertThrows(UnsupportedOperationException.class, () -> account.accessTokens().add(null));
		assertThrows(UnsupportedOperationException.class, () -> account.mintableScopes().add("x"));
	}

	/**
	 * No member is named as a secret, a token hash, a credential, a subject or the attribs cipher is, nor as the
	 * details of other people and operators this view leaves out.
	 */
	static void assertNoOtherPeoplesOrSecretMembers(JsonNode node) {
		if (node.isObject()) {
			for (String name : node.propertyNames()) {
				String lower = name.toLowerCase(Locale.ROOT) + "\"";
				for (String banned : List.of("hash", "encoded", "token\"", "secret", "password\"", "credential",
						"stored", "attribs", "sub\"", "subject\"", "cipher", "createdby", "modifiedby", "grantedby",
						"version", "reason", "superuser", "disabled")) {
					assertFalse(lower.contains(banned), () -> "member " + name + " looks like " + banned);
				}
				assertNoOtherPeoplesOrSecretMembers(node.get(name));
			}
		}
		else if (node.isArray()) {
			node.forEach(MyAccountWireTest::assertNoOtherPeoplesOrSecretMembers);
		}
	}
}
