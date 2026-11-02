package dev.bluestep.global.dto.domain;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.time.LocalDateTime;
import java.util.List;

import org.junit.jupiter.api.Test;

import dev.bluestep.global.dto.basetable.BaseTableKey;
import dev.bluestep.global.dto.registry.RegistryHost;
import dev.bluestep.global.dto.registry.RegistryTenantResponse;
import dev.bluestep.global.dto.tenant.TenantKey;

import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

/**
 * Pins the bytes of every record that carries a host or a site prefix, so retyping those components
 * from {@code String} to {@link DomainHost} and {@link DomainPrefix} is proven to change no byte on the
 * wire, either way.
 *
 * <p>The expected strings were first pinned against the {@code String}-typed components (commit
 * 24249cb's shapes, with {@link #h} the identity; then c8aa29d's, with {@link #p} the identity);
 * only those helpers changed when the components were retyped. Each record is written to exactly the old
 * bytes and those bytes read back to an equal record, including a stored host the request rule would
 * refuse.</p>
 */
class DomainHostWireTest {

	private static final ObjectMapper JSON = JsonMapper.builder()
			.enable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
			.build();

	private static final TenantKey TENANT = new TenantKey(TenantKey.CLASSID, 137_803L);
	private static final DomainKey DOMAIN = new DomainKey(DomainKey.CLASSID, 5_531L);

	/** A stored host the request rule refuses (padding, upper case): it still reads and writes as stored. */
	private static final String STORED = " Harbor.Example.org";

	private static DomainHost h(String host) {
		return DomainHost.of(host);
	}

	private static DomainPrefix p(String prefix) {
		return DomainPrefix.of(prefix);
	}

	private static <T> void pinned(T sent, String expected, Class<T> type) {
		assertEquals(expected, JSON.writeValueAsString(sent));
		assertEquals(sent, JSON.readValue(expected, type));
	}

	@Test
	void recordResponse() {
		pinned(new DomainRecordResponse(DOMAIN, h(STORED), TENANT, new BaseTableKey(111_000L, 12L), p("harbor"), 4L,
				120, null, LocalDateTime.of(2020, 1, 2, 3, 4, 5), null, null, null),
				"{\"domain\":{\"classid\":120100,\"seqnum\":5531},\"host\":\" Harbor.Example.org\","
						+ "\"tenant\":{\"classid\":111111,\"seqnum\":137803},"
						+ "\"organization\":{\"classid\":111000,\"seqnum\":12},\"domainPrefix\":\"harbor\",\"flags\":4,"
						+ "\"ingressReadTimeoutSeconds\":120,\"signIn\":null,\"created\":\"2020-01-02T03:04:05\","
						+ "\"modified\":null,\"creator\":null,\"modifier\":null}",
				DomainRecordResponse.class);
	}

	@Test
	void createRequest() {
		pinned(new DomainCreateRequest(TENANT, h("harbor.example.org"), null, null, 0L, null, null, null),
				"{\"tenant\":{\"classid\":111111,\"seqnum\":137803},\"host\":\"harbor.example.org\","
						+ "\"organization\":null,\"domainPrefix\":null,\"flags\":0,\"ingressReadTimeoutSeconds\":null,"
						+ "\"signIn\":null,\"creator\":null}",
				DomainCreateRequest.class);
	}

	@Test
	void updateRequest() {
		pinned(new DomainUpdateRequest(h("harbor.example.org"), null, p("harbor"), 2L, 300, null, null),
				"{\"host\":\"harbor.example.org\",\"organization\":null,\"domainPrefix\":\"harbor\",\"flags\":2,"
						+ "\"ingressReadTimeoutSeconds\":300,\"signIn\":null,\"actor\":null}",
				DomainUpdateRequest.class);
	}

	@Test
	void ingressRow() {
		pinned(new DomainIngressRow(h("a.harbor.example.org"), null, p("a"), "harbor.example.org"),
				"{\"host\":\"a.harbor.example.org\",\"ingressReadTimeoutSeconds\":null,\"domainPrefix\":\"a\","
						+ "\"wildcard\":\"harbor.example.org\"}",
				DomainIngressRow.class);
	}

	@Test
	void registryTenantAndHosts() {
		pinned(new RegistryTenantResponse(TENANT, "U137803", null, h(STORED), null,
				List.of(new RegistryHost(h(STORED), 300), new RegistryHost(h("harbor.bluestep.net"), null))),
				"{\"tenant\":{\"classid\":111111,\"seqnum\":137803},\"tenantId\":\"U137803\",\"name\":null,"
						+ "\"defaultHost\":\" Harbor.Example.org\",\"ingressReadTimeoutSeconds\":null,"
						+ "\"hosts\":[{\"host\":\" Harbor.Example.org\",\"ingressReadTimeoutSeconds\":300},"
						+ "{\"host\":\"harbor.bluestep.net\",\"ingressReadTimeoutSeconds\":null}]}",
				RegistryTenantResponse.class);
	}

	@Test
	void absentDefaultHost() {
		pinned(new RegistryTenantResponse(TENANT, "U137803", null, null, null, List.of()),
				"{\"tenant\":{\"classid\":111111,\"seqnum\":137803},\"tenantId\":\"U137803\",\"name\":null,"
						+ "\"defaultHost\":null,\"ingressReadTimeoutSeconds\":null,\"hosts\":[]}",
				RegistryTenantResponse.class);
	}
}
