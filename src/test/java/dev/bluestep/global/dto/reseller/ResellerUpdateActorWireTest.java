package dev.bluestep.global.dto.reseller;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Optional;

import org.junit.jupiter.api.Test;

import dev.bluestep.global.dto.tenant.TenantActor;
import dev.bluestep.global.dto.tenant.TenantKey;
import dev.bluestep.global.dto.tenantaccess.GlobalUserKey;
import dev.bluestep.global.dto.tenantaccess.TenantUserKey;

import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

/**
 * Pins {@link ResellerUpdateRequest#actor()}, added in 5.5.0: both actor kinds bind, and a body written
 * before the component existed (no {@code actor} key) binds as no actor, as does an explicit null.
 */
class ResellerUpdateActorWireTest {

	private static final ObjectMapper JSON = JsonMapper.builder().build();

	private static final String BEFORE_5_5_0 = """
			{"displayName":"House","icons":{},"flags":4}""";

	@Test
	void aGlobalUserActor_roundTrips() throws Exception {
		ResellerUpdateRequest sent = withActor(new TenantActor.GlobalUser(new GlobalUserKey(222_222L, -180L)));

		String json = JSON.writeValueAsString(sent);

		assertTrue(json.contains("\"actor\":{\"kind\":\"global-user\",\"user\":{\"classid\":222222,\"seqnum\":-180}}"),
				json);
		assertEquals(sent, JSON.readValue(json, ResellerUpdateRequest.class));
	}

	@Test
	void aTenantUserActor_roundTrips() throws Exception {
		ResellerUpdateRequest sent = withActor(new TenantActor.TenantUser(new TenantKey(111_111L, 137_803L),
				new TenantUserKey(222_222L, 42L)));

		assertEquals(sent, JSON.readValue(JSON.writeValueAsString(sent), ResellerUpdateRequest.class));
	}

	@Test
	void aBodyWithoutTheKey_orWithNull_isNoActor() throws Exception {
		ResellerUpdateRequest old = JSON.readValue(BEFORE_5_5_0, ResellerUpdateRequest.class);
		ResellerUpdateRequest nulled = JSON.readValue(
				"{\"displayName\":\"House\",\"icons\":{},\"actor\":null}", ResellerUpdateRequest.class);

		assertTrue(old.actor().isEmpty());
		assertEquals(Optional.of(4L), old.flags());
		assertTrue(nulled.actor().isEmpty());
	}

	@Test
	void theSevenArgumentConstructor_isNoActor() {
		ResellerUpdateRequest request = new ResellerUpdateRequest("House", Optional.empty(), Optional.empty(),
				Optional.empty(), Optional.empty(), JSON.readValue("{}", ResellerIcons.class), Optional.empty());

		assertTrue(request.actor().isEmpty());
	}

	private static ResellerUpdateRequest withActor(TenantActor actor) throws Exception {
		return new ResellerUpdateRequest("House", Optional.of("help@example.test"), Optional.empty(),
				Optional.empty(), Optional.empty(), JSON.readValue("{}", ResellerIcons.class), Optional.of(4L),
				Optional.of(actor));
	}
}
