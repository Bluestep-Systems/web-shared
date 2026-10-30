package dev.bluestep.global.dto;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import org.junit.jupiter.api.Test;

import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

import dev.bluestep.global.dto.accesstoken.CreateTokenResponse;
import dev.bluestep.global.dto.accesstoken.UpdateTokenRequest;
import dev.bluestep.global.dto.ai.BudgetSchedule;
import dev.bluestep.global.dto.ai.RateVariant;
import dev.bluestep.global.dto.aitenantblock.AiTenantBlockResponse;
import dev.bluestep.global.dto.aitenantconfig.AiTenantConfigRequest;
import dev.bluestep.global.dto.aitenantconfig.AiTenantConfigResponse;
import dev.bluestep.global.dto.aiunitrate.AiUnitRateRequest;
import dev.bluestep.global.dto.aiunitrate.AiUnitRateResponse;
import dev.bluestep.global.dto.aiusage.AiUsageQueryRequest;
import dev.bluestep.global.dto.aiusage.AiUsageQueryResponse;
import dev.bluestep.global.dto.basetable.BaseTableKey;
import dev.bluestep.global.dto.globaluser.CatalogScopeKind;
import dev.bluestep.global.dto.globaluser.GlobalUserScopeDecision;
import dev.bluestep.global.dto.globaluser.GlobalUserScopeResponse;
import dev.bluestep.global.dto.globaluserrecord.GlobalUserRecordResponse;
import dev.bluestep.global.dto.lookup.Icd10Option;
import dev.bluestep.global.dto.reseller.ResellerCreateRequest;
import dev.bluestep.global.dto.reseller.ResellerIcons;
import dev.bluestep.global.dto.reseller.ResellerRecordResponse;
import dev.bluestep.global.dto.tenantaccess.GlobalUserKey;
import dev.bluestep.global.dto.tenantaccess.ResellerKey;
import dev.bluestep.global.dto.tenantaccess.TenantAccessGrantRequest;
import dev.bluestep.global.dto.tenantaccess.TenantAccessGrantResponse;

/**
 * Holds 6.0.0's move from {@code Optional<T>} record components to {@code @Nullable} ones to an
 * unchanged wire, one record per converted package, with every optional component absent and then
 * present.
 *
 * <p>{@code wire-5.5.0.tsv} beside this class is what web-shared 5.5.0 wrote for exactly these values,
 * built with that release's {@code Optional} constructors (an absent value was {@code Optional.empty()},
 * a present one {@code Optional.of(value)}) and serialized by the same bare Jackson 3 mapper. Each case
 * must still serialize to those bytes, and those bytes must bind back to a value that serializes to them
 * again. The {@code ai} package's one-character wire is pinned separately, by
 * {@link LegacyWireCompatTest} and {@link OptionalWireContractTest}.</p>
 */
class NullableComponentWireTest {

	private static final ObjectMapper MAPPER = JsonMapper.builder().build();

	private static final OffsetDateTime T1 = OffsetDateTime.parse("2026-01-02T03:04:05Z");
	private static final OffsetDateTime T2 = OffsetDateTime.parse("2026-02-03T11:05:06Z");
	private static final LocalDateTime L1 = LocalDateTime.parse("2026-03-04T05:06:07");
	private static final String[] SCOPES = {"read", "write"};
	private static final byte[] FLAGS = {1, 2};
	private static final GlobalUserKey USER = new GlobalUserKey(222_222L, -51_001L);
	private static final ResellerKey RESELLER = new ResellerKey(111_222L, 1L);

	static Map<String, Object> cases() {
		Map<String, Object> c = new LinkedHashMap<>();
		c.put("accesstoken.CreateTokenResponse.absent", new CreateTokenResponse("raw", 1L, 2L, "enc", "alias", SCOPES,
				null, null, FLAGS));
		c.put("accesstoken.CreateTokenResponse.present", new CreateTokenResponse("raw", 1L, 2L, "enc", "alias", SCOPES,
				T1, T2, FLAGS));
		c.put("accesstoken.UpdateTokenRequest.absent", new UpdateTokenRequest("alias", SCOPES, null, FLAGS));
		c.put("accesstoken.UpdateTokenRequest.present", new UpdateTokenRequest("alias", SCOPES, T1, FLAGS));
		c.put("aitenantblock.AiTenantBlockResponse.absent", new AiTenantBlockResponse(7L, "acme", null, null, T1));
		c.put("aitenantblock.AiTenantBlockResponse.present", new AiTenantBlockResponse(7L, "acme", "org-1",
				"abuse", T1));
		c.put("aitenantconfig.AiTenantConfigRequest.absent", new AiTenantConfigRequest("acme", null, null,
				null, null, 5, null, null, null));
		c.put("aitenantconfig.AiTenantConfigRequest.present", new AiTenantConfigRequest("acme", "org-1", "u-9",
				"chat", 250_000L, 5, BudgetSchedule.MONTHLY, -420, Boolean.TRUE));
		c.put("aitenantconfig.AiTenantConfigResponse.absent", new AiTenantConfigResponse(3L, "acme", null, null,
				null, null, 5, BudgetSchedule.LIFETIME, 0, true, T1, T2));
		c.put("aitenantconfig.AiTenantConfigResponse.present", new AiTenantConfigResponse(3L, "acme", "org-1",
				"u-9", "chat", 250_000L, 5, BudgetSchedule.DAILY, 60, false, T1, T2));
		c.put("aiunitrate.AiUnitRateRequest.absent", new AiUnitRateRequest("anthropic", "m", "text_input",
				RateVariant.DEFAULT, "text", "tokens", 3L, null, null, null));
		c.put("aiunitrate.AiUnitRateRequest.present", new AiUnitRateRequest("anthropic", "m", "text_input",
				RateVariant.BATCH, "text", "tokens", 3L, 1L, 9L, "{\"type\":\"tiers\"}"));
		c.put("aiunitrate.AiUnitRateResponse.absent", new AiUnitRateResponse(4L, "anthropic", "m", "text_input",
				RateVariant.DEFAULT, "text", "tokens", 3L, 1_000_000L, 0L, null, T1, T2));
		c.put("aiunitrate.AiUnitRateResponse.present", new AiUnitRateResponse(4L, "anthropic", "m", "text_input",
				RateVariant.FAST, "text", "tokens", 3L, 1L, 9L, "{\"type\":\"tiers\"}", T1, T2));
		c.put("aiusage.AiUsageQueryRequest.absent", new AiUsageQueryRequest("acme", null, null, null,
				null, null));
		c.put("aiusage.AiUsageQueryRequest.present", new AiUsageQueryRequest("acme", "org-1", "u-9",
				"chat", T1, T2));
		c.put("aiusage.AiUsageQueryResponse.absent", new AiUsageQueryResponse(12L, null, null, null, T1,
				null, false, true, false));
		c.put("aiusage.AiUsageQueryResponse.present", new AiUsageQueryResponse(12L, 500L, 8,
				BudgetSchedule.WEEKLY, T1, T2, true, false, true));
		c.put("lookup.Icd10Option.absent", new Icd10Option("Label", "A00", null, null, "Y", null, null,
				null, null, null));
		c.put("lookup.Icd10Option.present", new Icd10Option("Label", "A00", "short", "long", "Y", "cat",
				"ns", "no", "or", "mj"));
		c.put("tenantaccess.TenantAccessGrantRequest.absent", new TenantAccessGrantRequest(null));
		c.put("tenantaccess.TenantAccessGrantRequest.present", new TenantAccessGrantRequest(T1));
		c.put("tenantaccess.TenantAccessGrantResponse.absent", new TenantAccessGrantResponse(USER, T1, null, true));
		c.put("tenantaccess.TenantAccessGrantResponse.present", new TenantAccessGrantResponse(USER, T1, T2, false));
		c.put("reseller.ResellerRecordResponse.absent", new ResellerRecordResponse(RESELLER, null, null,
				null, null, null, icons(null), 0L, null, null));
		c.put("reseller.ResellerRecordResponse.present", new ResellerRecordResponse(RESELLER, "House",
				"help@example.test", "example.test", "https://p.example.test", "https://t.example.test",
				icons("i"), 5L, new BaseTableKey(9L, 10L), L1));
		c.put("reseller.ResellerCreateRequest.absent", new ResellerCreateRequest(new BaseTableKey(9L, 10L), "House",
				null, null, null, null, icons(null), null));
		c.put("reseller.ResellerCreateRequest.present", new ResellerCreateRequest(new BaseTableKey(9L, 10L), "House",
				"help@example.test", "example.test", "https://p.example.test", "https://t.example.test",
				icons("i"), 5L));
		c.put("globaluser.GlobalUserScopeResponse.absent", new GlobalUserScopeResponse(USER, CatalogScopeKind.FLEET,
				null, null));
		c.put("globaluser.GlobalUserScopeResponse.present", new GlobalUserScopeResponse(USER,
				CatalogScopeKind.RESELLER, RESELLER, new GlobalUserScopeDecision("contractor", T1, "admin")));
		c.put("globaluserrecord.GlobalUserRecordResponse.absent", new GlobalUserRecordResponse(USER, null,
				null, null, null, null, false, false, null, null, false, null, List.of(),
				null));
		c.put("globaluserrecord.GlobalUserRecordResponse.present", new GlobalUserRecordResponse(USER, "Del",
				"Novak", "del.novak", "del@example.test", RESELLER, true, true, "attribs", L1, true,
				L1, List.of(), 3L));
		return c;
	}

	/** An icon set, every icon absent when {@code prefix} is null and numbered after it otherwise. */
	private static ResellerIcons icons(String prefix) {
		return new ResellerIcons(icon(prefix, 1), icon(prefix, 2), icon(prefix, 3), icon(prefix, 4), icon(prefix, 5),
				icon(prefix, 6), icon(prefix, 7), icon(prefix, 8));
	}

	private static String icon(String prefix, int n) {
		return prefix == null ? null : prefix + n;
	}

	@Test
	void everyCaseWritesTheBytes550Wrote() throws Exception {
		Map<String, String> golden = golden();
		Map<String, Object> cases = cases();
		assertEquals(golden.keySet(), cases.keySet(), "the cases and the 5.5.0 capture must name the same values");
		for (Map.Entry<String, Object> c : cases.entrySet()) {
			assertEquals(golden.get(c.getKey()), MAPPER.writeValueAsString(c.getValue()), c.getKey());
		}
	}

	@Test
	void every550PayloadBindsAndWritesBackUnchanged() throws Exception {
		Map<String, String> golden = golden();
		for (Map.Entry<String, Object> c : cases().entrySet()) {
			String json = golden.get(c.getKey());
			Object bound = MAPPER.readValue(json, c.getValue().getClass());
			assertEquals(json, MAPPER.writeValueAsString(bound), c.getKey());
		}
	}

	private static Map<String, String> golden() throws IOException {
		Map<String, String> golden = new LinkedHashMap<>();
		try (InputStream in = Objects.requireNonNull(
				NullableComponentWireTest.class.getResourceAsStream("wire-5.5.0.tsv"))) {
			for (String line : new String(in.readAllBytes(), StandardCharsets.UTF_8).split("\n")) {
				if (line.isBlank()) {
					continue;
				}
				String[] parts = line.split("\t", 2);
				golden.put(parts[0], parts[1]);
			}
		}
		assertTrue(golden.size() >= 30, "the 5.5.0 capture is missing cases: " + golden.size());
		return golden;
	}
}
