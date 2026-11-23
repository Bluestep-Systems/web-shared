package dev.bluestep.global.dto.consolefleet;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Instant;
import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import dev.bluestep.global.dto.fleet.NamespaceName;
import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

/** The 6.21.0 console fleet shapes. */
@DisplayName("6.21.0 console fleet shapes")
class ConsoleFleetWireTest {

	private static final ObjectMapper JSON = JsonMapper.builder()
			.enable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
			.build();

	private static final Instant STARTED = Instant.parse("2026-11-20T18:00:00Z");
	private static final Instant ASKED = Instant.parse("2026-11-20T18:00:00.004Z");

	@Test
	@DisplayName("an answer has exactly the documented wire and round trips")
	void wire() {
		ConsoleFleetResponse answer = new ConsoleFleetResponse(STARTED, List.of(
				new ConsoleFleetNamespace(NamespaceName.of("b6p-01"), "Utah 1", false, true, true, "26.11.09.06",
						FleetReadiness.READY, null, ASKED),
				new ConsoleFleetNamespace(NamespaceName.of("b6p-02"), null, true, false, false, null,
						FleetReadiness.NOT_READY, FleetCheck.DATABASE, ASKED)));

		String json = JSON.writeValueAsString(answer);

		assertEquals("{\"checkedAt\":\"2026-11-20T18:00:00Z\",\"namespaces\":["
				+ "{\"name\":\"b6p-01\",\"label\":\"Utah 1\",\"legacy\":false,\"isPrivate\":true,\"modified\":true,"
				+ "\"build\":\"26.11.09.06\",\"readiness\":\"READY\",\"failedCheck\":null,"
				+ "\"checkedAt\":\"2026-11-20T18:00:00.004Z\"},"
				+ "{\"name\":\"b6p-02\",\"label\":null,\"legacy\":true,\"isPrivate\":false,\"modified\":false,"
				+ "\"build\":null,\"readiness\":\"NOT_READY\",\"failedCheck\":\"DATABASE\","
				+ "\"checkedAt\":\"2026-11-20T18:00:00.004Z\"}]}", json);
		assertEquals(answer, JSON.readValue(json, ConsoleFleetResponse.class));
	}

	@Test
	@DisplayName("a readiness or check a newer web-global adds binds UNKNOWN")
	void forwardBinding() {
		ConsoleFleetNamespace read = JSON.readValue("{\"name\":\"b6p-03\",\"label\":null,\"legacy\":false,"
				+ "\"isPrivate\":false,\"modified\":false,\"build\":null,\"readiness\":\"DRAINING\","
				+ "\"failedCheck\":\"MAIN_DATABASE\",\"checkedAt\":\"2026-11-20T18:00:00Z\"}", ConsoleFleetNamespace.class);

		assertEquals(FleetReadiness.UNKNOWN, read.readiness());
		assertEquals(FleetCheck.UNKNOWN, read.failedCheck());
		assertTrue(read.optBuild().isEmpty() && read.optLabel().isEmpty());
	}

	@Test
	@DisplayName("a build is a short version string: releases and dev pass, free text does not")
	void buildShape() {
		assertTrue(ConsoleFleetNamespace.isBuild("26.11.09.06"));
		assertTrue(ConsoleFleetNamespace.isBuild("dev"));
		assertTrue(ConsoleFleetNamespace.isBuild("26.11.20.03-rc1+g3f4ed5c"));
		assertTrue(ConsoleFleetNamespace.isBuild("a".repeat(ConsoleFleetNamespace.MAX_BUILD_LENGTH)));
		assertFalse(ConsoleFleetNamespace.isBuild("a".repeat(ConsoleFleetNamespace.MAX_BUILD_LENGTH + 1)));
		assertFalse(ConsoleFleetNamespace.isBuild(""));
		assertFalse(ConsoleFleetNamespace.isBuild(" 26.11.09.06"));
		assertFalse(ConsoleFleetNamespace.isBuild("<html>"));
		assertFalse(ConsoleFleetNamespace.isBuild("Internal Server Error"));
		assertFalse(ConsoleFleetNamespace.isBuild(".hidden"));
		assertFalse(ConsoleFleetNamespace.isBuild("26.11\n09"));
		assertFalse(ConsoleFleetNamespace.isBuild("２６"));
	}

	@Test
	@DisplayName("a free-text build, a missing member, and a mutated list are refused")
	void refusals() {
		assertThrows(IllegalArgumentException.class, () -> new ConsoleFleetNamespace(NamespaceName.of("b6p-01"), null,
				false, false, false, "not a version", FleetReadiness.READY, null, ASKED));
		assertThrows(RuntimeException.class, () -> JSON.readValue("{\"name\":\"b6p-01\",\"label\":null,"
				+ "\"legacy\":false,\"isPrivate\":false,\"modified\":false,\"build\":\"<b>hi</b>\","
				+ "\"readiness\":\"READY\",\"failedCheck\":null,\"checkedAt\":\"2026-11-20T18:00:00Z\"}",
				ConsoleFleetNamespace.class));
		assertThrows(RuntimeException.class, () -> JSON.readValue("{\"namespaces\":[]}", ConsoleFleetResponse.class));
		assertThrows(NullPointerException.class, () -> new ConsoleFleetNamespace(NamespaceName.of("b6p-01"), null,
				false, false, false, null, null, null, ASKED));
		ConsoleFleetResponse answer = new ConsoleFleetResponse(STARTED, List.of());
		assertThrows(UnsupportedOperationException.class, () -> answer.namespaces().add(new ConsoleFleetNamespace(
				NamespaceName.of("b6p-01"), null, false, false, false, null, FleetReadiness.UNREACHABLE, null, ASKED)));
	}
}
