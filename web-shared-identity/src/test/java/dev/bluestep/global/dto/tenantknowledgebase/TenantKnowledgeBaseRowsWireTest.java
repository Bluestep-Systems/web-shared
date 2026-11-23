package dev.bluestep.global.dto.tenantknowledgebase;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.UUID;

import org.hibernate.validator.messageinterpolation.ParameterMessageInterpolator;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import dev.bluestep.global.dto.knowledgebase.KnowledgeBaseName;
import dev.bluestep.global.dto.knowledgebase.LiveBranch;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import tools.jackson.databind.DatabindException;
import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

/** The 6.6.0 tenant knowledge-base rows shapes. */
@DisplayName("6.6.0 tenant knowledge-base rows shapes")
class TenantKnowledgeBaseRowsWireTest {

	private static final ObjectMapper JACKSON3 = JsonMapper.builder()
			.enable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
			.build();

	private static final Validator VALIDATOR = validator();

	private static final UUID ID = UUID.fromString("3f0c1e2a-6f1d-4e43-9b8c-2d3f4a5b6c7d");

	private static Validator validator() {
		try (ValidatorFactory factory = Validation.byDefaultProvider().configure()
				.messageInterpolator(new ParameterMessageInterpolator())
				.buildValidatorFactory()) {
			return factory.getValidator();
		}
	}

	private static TenantKnowledgeBaseRow row() {
		return new TenantKnowledgeBaseRow(ID, KnowledgeBaseName.of("policies"), "Policies & Procedures",
				LiveBranch.of("main"));
	}

	@Test
	@DisplayName("an answer with rows has exactly the documented wire")
	void wire() {
		TenantKnowledgeBaseRowsResponse answer = TenantKnowledgeBaseRowsResponse.answered("U137803", List.of(row()));
		String json = JACKSON3.writeValueAsString(answer);
		assertEquals("{\"tenant\":\"U137803\",\"state\":\"ANSWERED\",\"knowledgeBases\":[{\"id\":\""
				+ ID + "\",\"name\":\"policies\",\"title\":\"Policies & Procedures\",\"liveBranch\":\"main\"}]}",
				json);
		assertEquals(answer, JACKSON3.readValue(json, TenantKnowledgeBaseRowsResponse.class));
	}

	@Test
	@DisplayName("every state without rows round-trips with an empty list")
	void withoutRowsRoundTrips() {
		for (TenantKnowledgeBaseRowsState state : List.of(TenantKnowledgeBaseRowsState.ABSENT,
				TenantKnowledgeBaseRowsState.UNMAPPED, TenantKnowledgeBaseRowsState.UNANSWERED)) {
			TenantKnowledgeBaseRowsResponse answer = TenantKnowledgeBaseRowsResponse.withoutRows("U1", state);
			assertEquals(answer, JACKSON3.readValue(JACKSON3.writeValueAsString(answer),
					TenantKnowledgeBaseRowsResponse.class));
		}
	}

	@Test
	@DisplayName("a state this reader does not know binds as UNKNOWN, by exact name only")
	void unknownState() {
		assertEquals(TenantKnowledgeBaseRowsState.UNKNOWN, JACKSON3.readValue("\"SOMETHING_NEW\"",
				TenantKnowledgeBaseRowsState.class));
		assertEquals(TenantKnowledgeBaseRowsState.UNKNOWN, JACKSON3.readValue("1", TenantKnowledgeBaseRowsState.class));
		assertEquals(TenantKnowledgeBaseRowsState.UNKNOWN, JACKSON3.readValue("\"answered\"",
				TenantKnowledgeBaseRowsState.class));
		assertEquals(TenantKnowledgeBaseRowsState.ANSWERED, JACKSON3.readValue("\"ANSWERED\"",
				TenantKnowledgeBaseRowsState.class));
	}

	@Test
	@DisplayName("a row cannot carry an invalid name or branch, and its title is validated")
	void rowRules() {
		assertThrows(DatabindException.class, () -> JACKSON3.readValue(
				"{\"id\":\"" + ID + "\",\"name\":\"Bad Name\",\"title\":\"t\",\"liveBranch\":\"main\"}",
				TenantKnowledgeBaseRow.class));
		assertThrows(DatabindException.class, () -> JACKSON3.readValue(
				"{\"id\":\"" + ID + "\",\"name\":\"ok\",\"title\":\"t\",\"liveBranch\":\"-bad\"}",
				TenantKnowledgeBaseRow.class));
		assertTrue(VALIDATOR.validate(row()).isEmpty());
		assertEquals(1, VALIDATOR.validate(new TenantKnowledgeBaseRow(ID, KnowledgeBaseName.of("ok"), " ",
				LiveBranch.of("main"))).size());
		assertEquals(1, VALIDATOR.validate(new TenantKnowledgeBaseRow(ID, KnowledgeBaseName.of("ok"),
				"x".repeat(201), LiveBranch.of("main"))).size());
	}

	@Test
	@DisplayName("the tenant id is validated, and the rows are copied")
	void tenantAndCopy() {
		assertTrue(VALIDATOR.validate(TenantKnowledgeBaseRowsResponse.withoutRows("U137803",
				TenantKnowledgeBaseRowsState.ABSENT)).isEmpty());
		for (String bad : List.of("137803", "U0", "u1", "U", "U1 ", "U12345678901234567890")) {
			assertEquals(1, VALIDATOR.validate(TenantKnowledgeBaseRowsResponse.withoutRows(bad,
					TenantKnowledgeBaseRowsState.ABSENT)).size(), bad);
		}
		java.util.ArrayList<TenantKnowledgeBaseRow> rows = new java.util.ArrayList<>(List.of(row()));
		TenantKnowledgeBaseRowsResponse answer = TenantKnowledgeBaseRowsResponse.answered("U1", rows);
		rows.clear();
		assertEquals(1, answer.knowledgeBases().size());
		assertThrows(UnsupportedOperationException.class, () -> answer.knowledgeBases().clear());
	}

	@Test
	@DisplayName("rows are allowed only with ANSWERED; UNKNOWN tolerates them, in code and from JSON")
	void rowsAgreeWithState() {
		List<TenantKnowledgeBaseRow> rows = List.of(row());
		for (TenantKnowledgeBaseRowsState state : TenantKnowledgeBaseRowsState.values()) {
			boolean allowed = state == TenantKnowledgeBaseRowsState.ANSWERED
					|| state == TenantKnowledgeBaseRowsState.UNKNOWN;
			String json = "{\"tenant\":\"U1\",\"state\":\"" + state + "\",\"knowledgeBases\":[{\"id\":\"" + ID
					+ "\",\"name\":\"policies\",\"title\":\"t\",\"liveBranch\":\"main\"}]}";
			if (allowed) {
				assertEquals(1, new TenantKnowledgeBaseRowsResponse("U1", state, rows).knowledgeBases().size(),
						state.name());
				assertEquals(1, JACKSON3.readValue(json, TenantKnowledgeBaseRowsResponse.class).knowledgeBases().size(),
						state.name());
			} else {
				assertThrows(IllegalArgumentException.class,
						() -> new TenantKnowledgeBaseRowsResponse("U1", state, rows), state.name());
				assertThrows(DatabindException.class,
						() -> JACKSON3.readValue(json, TenantKnowledgeBaseRowsResponse.class), state.name());
			}
			// No rows is always fine.
			assertTrue(new TenantKnowledgeBaseRowsResponse("U1", state, List.of()).knowledgeBases().isEmpty());
		}
	}
}
