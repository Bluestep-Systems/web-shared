package dev.bluestep.global.dto.consoledirectory;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Instant;
import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import dev.bluestep.global.dto.tenant.TenantKey;
import dev.bluestep.global.dto.tenantaccess.ResellerKey;
import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

/** The 6.10.0 console directory shapes. */
@DisplayName("6.10.0 console directory shapes")
class ConsoleDirectoryWireTest {

	private static final ObjectMapper JSON = JsonMapper.builder()
			.enable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
			.build();

	private static final TenantKey TENANT = new TenantKey(TenantKey.CLASSID, 137_803L);

	@Test
	@DisplayName("an answer has exactly the documented wire and round trips")
	void wire() {
		ConsoleDirectoryResponse answer = new ConsoleDirectoryResponse(
				List.of(new ConsoleDirectoryReseller(new ResellerKey(111_222L, 1L), "BlueStep")),
				List.of(new ConsoleDirectoryTenant(TENANT, List.of("a.example.com", "b.example.com"), 1_000L, 2_000L,
						Instant.parse("2026-11-18T10:15:00Z"))));

		String json = JSON.writeValueAsString(answer);

		assertEquals("{\"resellers\":[{\"reseller\":{\"classid\":111222,\"seqnum\":1},\"name\":\"BlueStep\"}],"
				+ "\"tenants\":[{\"tenant\":{\"classid\":111111,\"seqnum\":137803},"
				+ "\"hostNames\":[\"a.example.com\",\"b.example.com\"],\"dbBytes\":1000,\"fileBytes\":2000,"
				+ "\"lastActivity\":\"2026-11-18T10:15:00Z\"}]}", json);
		assertEquals(answer, JSON.readValue(json, ConsoleDirectoryResponse.class));
	}

	@Test
	@DisplayName("what is not held is absent on the wire and reads back as absent, never zero")
	void absentIsNotZero() {
		ConsoleDirectoryTenant bare = new ConsoleDirectoryTenant(TENANT, List.of(), null, null, null);

		ConsoleDirectoryTenant read = JSON.readValue(
				"{\"tenant\":{\"classid\":111111,\"seqnum\":137803},\"hostNames\":[],\"dbBytes\":null,"
						+ "\"fileBytes\":null,\"lastActivity\":null}", ConsoleDirectoryTenant.class);

		assertEquals(bare, read);
		assertTrue(read.optDbBytes().isEmpty() && read.optFileBytes().isEmpty() && read.optLastActivity().isEmpty());
		assertEquals(0L, new ConsoleDirectoryTenant(TENANT, List.of(), 0L, 0L, null).optDbBytes().getAsLong());
	}

	@Test
	@DisplayName("a negative level, a missing member, and a mutated list are refused")
	void refusals() {
		assertThrows(IllegalArgumentException.class, () -> new ConsoleDirectoryTenant(TENANT, List.of(), -1L, null, null));
		assertThrows(IllegalArgumentException.class, () -> new ConsoleDirectoryTenant(TENANT, List.of(), null, -1L, null));
		assertThrows(NullPointerException.class, () -> new ConsoleDirectoryReseller(new ResellerKey(1, 1), null));
		assertThrows(RuntimeException.class, () -> JSON.readValue("{\"resellers\":[]}", ConsoleDirectoryResponse.class));
		ConsoleDirectoryResponse answer = new ConsoleDirectoryResponse(List.of(), List.of());
		assertThrows(UnsupportedOperationException.class, () -> answer.tenants().add(
				new ConsoleDirectoryTenant(TENANT, List.of(), null, null, null)));
	}
}
