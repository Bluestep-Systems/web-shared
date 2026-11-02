package dev.bluestep.global.dto.reseller;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.time.LocalDateTime;
import java.util.function.Consumer;

import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.Test;

import dev.bluestep.global.dto.basetable.BaseTableKey;
import dev.bluestep.global.dto.domain.DomainSuffix;
import dev.bluestep.global.dto.tenantaccess.ResellerKey;

import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

/**
 * Pins the bytes of every reseller record that carries {@code defaultDomain}, so retyping it from
 * {@code String} (5.5.0) to {@link DomainSuffix} is proven to change no byte on the wire, either way.
 *
 * <p>The column holds {@code NULL}, {@code ''} and host names, and a whole-set edit sends back what it
 * read, so all three must survive a round trip as they were: absent stays {@code null}, blank stays
 * {@code ""}. The expected strings were first pinned against the {@code String}-typed components
 * (commit c8aa29d, with {@link #d} the identity); only {@link #d} changed when they were retyped.</p>
 */
class ResellerDefaultDomainWireTest {

	private static final ObjectMapper JSON = JsonMapper.builder()
			.enable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
			.build();

	private static final ResellerKey RESELLER = new ResellerKey(111_222L, 1L);

	private static final String ICONS = "{\"favicon\":null,\"favicon16\":null,\"favicon32\":null,"
			+ "\"appleTouchIcon\":null,\"appleTouchIcon72\":null,\"appleTouchIcon114\":null,"
			+ "\"appleTouchIcon144\":null,\"safariPinnedTab\":null}";

	private static @Nullable DomainSuffix d(@Nullable String suffix) {
		return suffix == null ? null : DomainSuffix.of(suffix);
	}

	private static ResellerIcons icons() {
		return new ResellerIcons(null, null, null, null, null, null, null, null);
	}

	private static <T> void pinned(T sent, String expected, Class<T> type) {
		assertEquals(expected, JSON.writeValueAsString(sent));
		assertEquals(sent, JSON.readValue(expected, type));
	}

	private static String quoted(@Nullable String suffix) {
		return suffix == null ? "null" : "\"" + suffix + "\"";
	}

	private static void eachStoredShape(Consumer<@Nullable String> check) {
		for (String suffix : new String[] {"sites.example.test", ""}) {
			check.accept(suffix);
		}
		check.accept(null);
	}

	@Test
	void recordResponse() {
		eachStoredShape(suffix -> pinned(
				new ResellerRecordResponse(RESELLER, "House", null, d(suffix), null, null, icons(), 4L,
						new BaseTableKey(222_222L, 7L), LocalDateTime.of(2020, 1, 2, 3, 4, 5)),
				"{\"reseller\":{\"classid\":111222,\"seqnum\":1},\"displayName\":\"House\",\"supportEmail\":null,"
						+ "\"defaultDomain\":" + quoted(suffix) + ",\"privacyPageUrl\":null,\"termsPageUrl\":null,"
						+ "\"icons\":" + ICONS + ",\"flags\":4,\"creator\":{\"classid\":222222,\"seqnum\":7},"
						+ "\"created\":\"2020-01-02T03:04:05\"}",
				ResellerRecordResponse.class));
	}

	@Test
	void response() {
		eachStoredShape(suffix -> pinned(
				new ResellerResponse(RESELLER, null, d(suffix), null, null, icons()),
				"{\"reseller\":{\"classid\":111222,\"seqnum\":1},\"supportEmail\":null,"
						+ "\"defaultDomain\":" + quoted(suffix) + ",\"privacyPageUrl\":null,\"termsPageUrl\":null,"
						+ "\"icons\":" + ICONS + "}",
				ResellerResponse.class));
	}

	@Test
	void updateRequest() {
		eachStoredShape(suffix -> pinned(
				new ResellerUpdateRequest("House", null, d(suffix), null, null, icons(), 4L, null),
				"{\"displayName\":\"House\",\"supportEmail\":null,\"defaultDomain\":" + quoted(suffix)
						+ ",\"privacyPageUrl\":null,\"termsPageUrl\":null,\"icons\":" + ICONS
						+ ",\"flags\":4,\"actor\":null}",
				ResellerUpdateRequest.class));
	}

	@Test
	void createRequest() {
		eachStoredShape(suffix -> pinned(
				new ResellerCreateRequest(new BaseTableKey(222_222L, 7L), "House", null, d(suffix), null, null,
						icons(), null),
				"{\"creator\":{\"classid\":222222,\"seqnum\":7},\"displayName\":\"House\",\"supportEmail\":null,"
						+ "\"defaultDomain\":" + quoted(suffix) + ",\"privacyPageUrl\":null,\"termsPageUrl\":null,"
						+ "\"icons\":" + ICONS + ",\"flags\":null}",
				ResellerCreateRequest.class));
	}
}
