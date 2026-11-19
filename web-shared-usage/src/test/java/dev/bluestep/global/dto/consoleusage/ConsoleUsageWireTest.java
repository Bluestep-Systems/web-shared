package dev.bluestep.global.dto.consoleusage;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.time.Instant;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import dev.bluestep.global.dto.usage.StorageLevel;
import dev.bluestep.global.dto.usage.StorageUsageDay;
import dev.bluestep.global.dto.usage.StorageUsageResponse;
import dev.bluestep.global.dto.usage.UsageCategory;
import dev.bluestep.global.dto.usage.UsageCategorySeries;
import dev.bluestep.global.dto.usage.UsageGrain;
import dev.bluestep.global.dto.usage.UsageSeriesPoint;
import dev.bluestep.global.dto.usage.UsageSeriesResponse;
import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

/** The 6.12.0 console usage shape. */
@DisplayName("6.12.0 console usage shape")
class ConsoleUsageWireTest {

	private static final ObjectMapper JSON = JsonMapper.builder()
			.enable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
			.build();

	private static final Instant DAY_1 = Instant.parse("2026-11-16T00:00:00Z");
	private static final Instant DAY_2 = Instant.parse("2026-11-17T00:00:00Z");

	private static ConsoleUsageResponse answer() {
		UsageSeriesResponse usage = new UsageSeriesResponse("U137803", DAY_1, Instant.parse("2026-11-18T00:00:00Z"),
				Instant.parse("2026-11-18T09:30:00Z"), UsageGrain.DAY, List.of(
						new UsageCategorySeries(UsageCategory.HTTP, List.of(
								new UsageSeriesPoint(DAY_1, 10, 500, 90, 100, 1_000, 2_000), UsageSeriesPoint.empty(DAY_2))),
						new UsageCategorySeries(UsageCategory.FILE, List.of(UsageSeriesPoint.empty(DAY_1),
								UsageSeriesPoint.empty(DAY_2)))));
		StorageUsageResponse storage = new StorageUsageResponse("U137803", LocalDate.parse("2026-11-16"),
				LocalDate.parse("2026-11-17"),
				List.of(new StorageLevel("db", OffsetDateTime.parse("2026-11-18T08:00:00Z"), 1_000L)),
				List.of(new StorageUsageDay(LocalDate.parse("2026-11-16"), "db", 24, 900L, 21_000L)));
		return new ConsoleUsageResponse(usage, storage);
	}

	@Test
	@DisplayName("an answer round trips, and carries exactly its two halves")
	void wire() {
		ConsoleUsageResponse answer = answer();

		String json = JSON.writeValueAsString(answer);

		assertEquals(answer, JSON.readValue(json, ConsoleUsageResponse.class));
		assertEquals(List.of("usage", "storage"), JSON.readTree(json).propertyNames().stream().toList());
	}

	@Test
	@DisplayName("a missing half is refused, on the wire and in code")
	void refusals() {
		assertThrows(NullPointerException.class, () -> new ConsoleUsageResponse(answer().usage(), null));
		assertThrows(NullPointerException.class, () -> new ConsoleUsageResponse(null, answer().storage()));
		assertThrows(RuntimeException.class, () -> JSON.readValue("{\"usage\":null,\"storage\":null}", ConsoleUsageResponse.class));
	}
}
