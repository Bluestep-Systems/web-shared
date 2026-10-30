package dev.bluestep.global.dto.registry;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;

import dev.bluestep.global.dto.tenant.TenantKey;

import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

/**
 * Pins 6.0.0's registry shapes: their wire keys, absent against explicit null, and the host list's
 * default and copy. Strict on unknown properties, so a derived getter leaking onto the wire fails the
 * round trip.
 */
class RegistryWireTest {

	private static final ObjectMapper JSON = JsonMapper.builder()
			.enable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
			.build();

	private static final TenantKey TENANT = new TenantKey(TenantKey.CLASSID, 137_803L);

	@Test
	void tenant_roundTripsAndPinsItsKeys() {
		RegistryTenantResponse sent = new RegistryTenantResponse(TENANT, "U137803", "Harbor Clinic",
				"harbor.example.org", 300, List.of(new RegistryHost("harbor.example.org", 300),
						new RegistryHost("harbor.bluestep.net", null)));

		String json = JSON.writeValueAsString(sent);

		assertEquals("{\"tenant\":{\"classid\":111111,\"seqnum\":137803},\"tenantId\":\"U137803\","
				+ "\"name\":\"Harbor Clinic\",\"defaultHost\":\"harbor.example.org\",\"ingressReadTimeoutSeconds\":300,"
				+ "\"hosts\":[{\"host\":\"harbor.example.org\",\"ingressReadTimeoutSeconds\":300},"
				+ "{\"host\":\"harbor.bluestep.net\",\"ingressReadTimeoutSeconds\":null}]}", json);
		assertEquals(sent, JSON.readValue(json, RegistryTenantResponse.class));
	}

	@Test
	void tenant_anOmittedKeyAndAnExplicitNullBothBindAsAbsent() {
		String key = "\"tenant\":{\"classid\":111111,\"seqnum\":137803},\"tenantId\":\"U137803\"";

		RegistryTenantResponse omitted = JSON.readValue("{" + key + "}", RegistryTenantResponse.class);
		RegistryTenantResponse nulled = JSON.readValue("{" + key + ",\"name\":null,\"defaultHost\":null,"
				+ "\"ingressReadTimeoutSeconds\":null,\"hosts\":null}", RegistryTenantResponse.class);

		assertEquals(omitted, nulled);
		assertTrue(omitted.optName().isEmpty());
		assertTrue(omitted.optDefaultHost().isEmpty());
		assertTrue(omitted.optIngressReadTimeoutSeconds().isEmpty());
		assertEquals(List.of(), omitted.hosts());
	}

	@Test
	void tenant_copiesItsHosts() {
		List<RegistryHost> hosts = new ArrayList<>(List.of(new RegistryHost("harbor.example.org", null)));
		RegistryTenantResponse response = new RegistryTenantResponse(TENANT, "U137803", null, null, null, hosts);
		hosts.add(new RegistryHost("other.example.org", null));

		assertEquals(1, response.hosts().size());
	}
}
