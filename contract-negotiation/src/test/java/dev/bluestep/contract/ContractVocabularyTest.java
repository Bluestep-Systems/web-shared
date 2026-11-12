package dev.bluestep.contract;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;

/**
 * The spellings other repositories match on: web-global's {@code ContractRefusal}, the monolith's
 * {@code WebGlobalResponseException.REFUSAL_HEADER} and the gateway's registry client spell the same values today.
 */
class ContractVocabularyTest {

	@Test
	void headerNamesAreThoseTheFleetAlreadySpells() {
		assertThat(ContractHeaders.CONTRACT).isEqualTo("B6P-Contract");
		assertThat(ContractHeaders.REFUSAL).isEqualTo("X-Contract-Refusal");
		assertThat(ContractHeaders.NEGOTIATED_ATTRIBUTE).isEqualTo("B6P-Contract.negotiated");
	}

	@Test
	void refusalTokensAreWebGlobalsPlusTheHeaderOne() {
		assertThat(ContractRefusalToken.values()).extracting(ContractRefusalToken::token).containsExactly(
			"unknown-version", "surface-not-served", "operation-arrived-later", "contract-header-invalid");
		for (ContractRefusalToken token : ContractRefusalToken.values()) {
			assertThat(ContractRefusalToken.fromToken(token.token())).contains(token);
			assertThat(ContractRefusalToken.fromToken(" " + token.token() + " ")).contains(token);
		}
		assertThat(ContractRefusalToken.fromToken("something-newer")).isEmpty();
		assertThat(ContractRefusalToken.fromToken(null)).isEmpty();
	}

	@Test
	void onlyTheTwo404sAreAMismatch() {
		assertThat(ContractRefusalToken.UNKNOWN_VERSION.isMismatch()).isTrue();
		assertThat(ContractRefusalToken.SURFACE_NOT_SERVED.isMismatch()).isTrue();
		assertThat(ContractRefusalToken.OPERATION_ARRIVED_LATER.isMismatch()).isFalse();
		assertThat(ContractRefusalToken.CONTRACT_HEADER_INVALID.isMismatch()).isFalse();
	}

	@Test
	void aHandshakeListsItsTagsNewestFirstAndCannotChange() {
		ContractHandshake handshake = ContractHandshake.of("web-global",
			List.of(ContractTag.of("1.2.2"), ContractTag.of("6.10.0"), ContractTag.of("6.9.0"), ContractTag.of("6.9.0")));
		assertThat(handshake.protocol()).isEqualTo(1);
		assertThat(handshake.tags()).containsExactly("6.10.0", "6.9.0", "1.2.2");
		List<String> source = new ArrayList<>(List.of("1.0.0"));
		ContractHandshake copied = new ContractHandshake(1, "web-harness", source);
		source.add("2.0.0");
		assertThat(copied.tags()).containsExactly("1.0.0");
		assertThatThrownBy(() -> copied.tags().add("3.0.0")).isInstanceOf(UnsupportedOperationException.class);
	}
}
