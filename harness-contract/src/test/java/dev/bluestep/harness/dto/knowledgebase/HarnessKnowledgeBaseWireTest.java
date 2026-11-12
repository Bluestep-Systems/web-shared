package dev.bluestep.harness.dto.knowledgebase;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import dev.bluestep.harness.testing.HarnessWireSamples;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * The harness wire under Jackson 3, the mapper web and web-global both run: the shared samples bind, the records write
 * exactly the samples, and a newer web's answer still binds.
 */
class HarnessKnowledgeBaseWireTest {

	static final JsonMapper MAPPER = JsonMapper.builder().build();

	static final HarnessTenantKnowledgeBaseRows FOUND = HarnessTenantKnowledgeBaseRows.found("U137803", List.of(
		new HarnessKnowledgeBaseRow(UUID.fromString("3f0c6a52-8e1d-4c3b-9a57-2b8f4e9d1c07"), "handbook",
			"Employee Handbook", "main"),
		new HarnessKnowledgeBaseRow(UUID.fromString("9b2e4d71-0f6a-4e85-b3c9-7d1a5f8e2c40"), "policies",
			"Policies & Procedures", "release/2026")));

	static HarnessTenantKnowledgeBaseRows read(String json) {
		return MAPPER.readValue(json, HarnessTenantKnowledgeBaseRows.class);
	}

	static JsonNode tree(String json) {
		return MAPPER.readTree(json);
	}

	@Test
	void theFoundSampleBindsAndIsExactlyWhatTheRecordWrites() {
		assertThat(read(HarnessWireSamples.TENANT_KNOWLEDGE_BASE_ROWS_FOUND.json())).isEqualTo(FOUND);
		assertThat(tree(MAPPER.writeValueAsString(FOUND)))
			.isEqualTo(tree(HarnessWireSamples.TENANT_KNOWLEDGE_BASE_ROWS_FOUND.json()));
	}

	@Test
	void theEmptyAndAbsentSamplesBindAndRoundTrip() {
		HarnessTenantKnowledgeBaseRows empty = HarnessTenantKnowledgeBaseRows.found("U137803", List.of());
		HarnessTenantKnowledgeBaseRows absent = HarnessTenantKnowledgeBaseRows.absent("U142133");
		assertThat(read(HarnessWireSamples.TENANT_KNOWLEDGE_BASE_ROWS_FOUND_EMPTY.json())).isEqualTo(empty);
		assertThat(read(HarnessWireSamples.TENANT_KNOWLEDGE_BASE_ROWS_ABSENT.json())).isEqualTo(absent);
		assertThat(tree(MAPPER.writeValueAsString(absent)))
			.isEqualTo(tree(HarnessWireSamples.TENANT_KNOWLEDGE_BASE_ROWS_ABSENT.json()));
	}

	@Test
	void theWireCarriesExactlyTheseKeys() {
		JsonNode written = tree(MAPPER.writeValueAsString(FOUND));
		assertThat(written.propertyNames()).containsExactlyInAnyOrder("tenant", "state", "knowledgeBases");
		assertThat(written.get("knowledgeBases").get(0).propertyNames())
			.containsExactlyInAnyOrder("id", "name", "title", "liveBranch");
		assertThat(written.get("state").asString()).isEqualTo("FOUND");
	}

	@Test
	void aNewerWebsAnswerBindsAsUnknown() {
		HarnessTenantKnowledgeBaseRows newer = read(HarnessWireSamples.TENANT_KNOWLEDGE_BASE_ROWS_NEWER_STATE.json());
		assertThat(newer.state()).isEqualTo(HarnessRowsState.UNKNOWN);
		assertThat(newer.knowledgeBases()).isEmpty();
	}

	@Test
	void aStateBindsByExactNameOnlyNeverByOrdinal() {
		for (String state : List.of("1", "found", " FOUND", "")) {
			assertThat(read("{\"tenant\":\"U1\",\"state\":\"" + state + "\",\"knowledgeBases\":[]}").state())
				.as(state).isEqualTo(HarnessRowsState.UNKNOWN);
		}
		assertThat(read("{\"tenant\":\"U1\",\"state\":1,\"knowledgeBases\":[]}").state())
			.isEqualTo(HarnessRowsState.UNKNOWN);
		assertThat(HarnessRowsState.fromWire("ABSENT")).isEqualTo(HarnessRowsState.ABSENT);
	}

	@Test
	void aMissingComponentFailsToBind() {
		assertThatThrownBy(() -> read("{\"tenant\":\"U1\",\"state\":\"FOUND\"}")).isInstanceOf(JacksonException.class);
		assertThatThrownBy(() -> read("{\"state\":\"FOUND\",\"knowledgeBases\":[]}"))
			.isInstanceOf(JacksonException.class);
		assertThatThrownBy(() -> read("{\"tenant\":\"U1\",\"state\":\"FOUND\",\"knowledgeBases\":[{\"id\":\""
			+ UUID.randomUUID() + "\",\"name\":\"a\",\"title\":\"A\"}]}")).isInstanceOf(JacksonException.class);
	}

	@Test
	void anAnswerCannotChangeOnceBuilt() {
		List<HarnessKnowledgeBaseRow> rows = new ArrayList<>(FOUND.knowledgeBases());
		HarnessTenantKnowledgeBaseRows answer = HarnessTenantKnowledgeBaseRows.found("U137803", rows);
		rows.clear();
		assertThat(answer.knowledgeBases()).hasSize(2);
		assertThatThrownBy(() -> answer.knowledgeBases().clear()).isInstanceOf(UnsupportedOperationException.class);
	}
}
