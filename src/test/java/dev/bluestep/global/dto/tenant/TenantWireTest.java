package dev.bluestep.global.dto.tenant;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.LocalDateTime;
import java.util.List;

import org.junit.jupiter.api.Test;

import dev.bluestep.global.dto.basetable.BaseTableKey;
import dev.bluestep.global.dto.tenantaccess.GlobalUserKey;
import dev.bluestep.global.dto.tenantaccess.ResellerKey;
import dev.bluestep.global.dto.tenantaccess.TenantUserKey;

import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

/**
 * Pins the tenant shapes' wire keys and the actor's polymorphism.
 *
 * <p>web-global and every monolith build bind these records from different releases, so a renamed key
 * or kind would compile clean on both sides and mis-bind across a staggered deploy. The mapper is bare,
 * as in the other wire tests: what binds here binds under any consumer's defaults.</p>
 */
class TenantWireTest {

	private static final ObjectMapper JSON = JsonMapper.builder().build();

	private static final TenantKey TENANT = new TenantKey(TenantKey.CLASSID, 137_803L);

	private static TenantRecordResponse record(TenantActor creator, TenantActor modifier) {
		return new TenantRecordResponse(TENANT, "Harbor Clinic", null, "harbor", "care", "U137803",
				new ResellerKey(111_222L, 1L), "web-a", 8L,
				new TenantFeatures(true, false, false, false, false, true, true, false, true, false, false, false,
						true),
				new TenantLock(5, true, LocalDateTime.of(2026, 10, 1, 8, 30), 60, "soon", "now", "done"),
				"cipher", "a,b", "fs1", 2, -1, 1_000.0, 500.0, "help",
				new TenantSignIn("TENANT_OAUTH2", "{\"issuer\":\"https://id.example\"}", "https://in.example",
						"https://out.example", 30, 5.0, "NONE"),
				new TenantStorage(10L, 5L, null, null),
				new TenantOrganization("desc", 25_000, null, null, new BaseTableKey(1_000_005L, 77L),
						new BaseTableKey(120_100L, 9L), null, "help@example.org", null, false, "Pat Doe",
						null, null, "pat@example.org", null, null, null, "10.0.0.0/8", false, true, null),
				LocalDateTime.of(2020, 1, 2, 3, 4, 5), LocalDateTime.of(2026, 10, 28, 9, 0), creator,
				modifier);
	}

	@Test
	void record_roundTripsWithBothActorKinds() throws Exception {
		TenantRecordResponse sent = record(new TenantActor.GlobalUser(new GlobalUserKey(222_222L, -180L)),
				new TenantActor.TenantUser(TENANT, new TenantUserKey(222_222L, 42L)));

		String json = JSON.writeValueAsString(sent);

		assertEquals(sent, JSON.readValue(json, TenantRecordResponse.class));
		assertTrue(json.contains("\"creator\":{\"kind\":\"global-user\",\"user\":{\"classid\":222222,\"seqnum\":-180}}"),
				json);
		assertTrue(json.contains("\"modifier\":{\"kind\":\"tenant-user\",\"tenant\":{\"classid\":111111,"
				+ "\"seqnum\":137803},\"user\":{\"classid\":222222,\"seqnum\":42}}"), json);
	}

	@Test
	void absentActors_areAbsentNotAThirdKind() throws Exception {
		TenantRecordResponse sent = record(null, null);

		TenantRecordResponse read = JSON.readValue(JSON.writeValueAsString(sent), TenantRecordResponse.class);

		assertTrue(read.optCreator().isEmpty());
		assertTrue(read.optModifier().isEmpty());
	}

	@Test
	void anUnknownActorKind_failsBinding() {
		assertThrows(JacksonException.class, () -> JSON.readValue(
				"{\"kind\":\"system\",\"user\":{\"classid\":222222,\"seqnum\":-1}}", TenantActor.class));
	}

	@Test
	void snapshot_pinsItsKeysAndDefaultsAnOmittedList() throws Exception {
		TenantSnapshotEntry entry = new TenantSnapshotEntry(TENANT, new ResellerKey(111_222L, 1L), "web-a", 4,
				8L, true, false, true, false);

		assertEquals("""
				{"tenants":[{"tenant":{"classid":111111,"seqnum":137803},\
				"reseller":{"classid":111222,"seqnum":1},"namespace":"web-a","lockStatus":4,"flags":8,\
				"enableChangeHistory":true,"enableObjectStats":false,"enableAlerts":true,"global":false}]}""",
				JSON.writeValueAsString(new TenantSnapshotResponse(List.of(entry))));
		assertEquals(List.of(), JSON.readValue("{}", TenantSnapshotResponse.class).tenants());
	}

	@Test
	void tenantKey_rendersAsClassidColonSeqnum() {
		assertEquals("111111:137803", TENANT.toString());
	}
}
