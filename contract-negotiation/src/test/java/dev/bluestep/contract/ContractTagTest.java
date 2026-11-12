package dev.bluestep.contract;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import java.util.stream.Stream;

import org.junit.jupiter.api.Test;

class ContractTagTest {

	@Test
	void parsesAStrictSemverTriple() {
		assertThat(ContractTag.parse("6.5.0")).contains(new ContractTag(6, 5, 0));
		assertThat(ContractTag.parse("0.0.0")).contains(new ContractTag(0, 0, 0));
		assertThat(ContractTag.parse("123456789.1.10")).contains(new ContractTag(123456789, 1, 10));
	}

	@Test
	void refusesEverythingElse() {
		for (String text : List.of("", "6.5", "6.5.0.1", "v6.5.0", "06.5.0", "6.05.0", "6.5.00", "6.5.0-rc1", " 6.5.0",
				"6.5.0 ", "6..0", "-1.0.0", "1234567890.0.0", "6.5.x", "６.5.0")) {
			assertThat(ContractTag.parse(text)).as(text).isEmpty();
		}
		assertThat(ContractTag.parse(null)).isEmpty();
		assertThatThrownBy(() -> ContractTag.of("6.5")).isInstanceOf(IllegalArgumentException.class)
			.hasMessageContaining("6.5");
		assertThatThrownBy(() -> new ContractTag(-1, 0, 0)).isInstanceOf(IllegalArgumentException.class);
	}

	@Test
	void ordersNumericallyNotLexically() {
		List<ContractTag> sorted = Stream.of("6.10.0", "6.9.0", "1.2.2", "6.9.10", "6.9.2", "10.0.0")
			.map(ContractTag::of).sorted().toList();
		assertThat(sorted).extracting(ContractTag::toString)
			.containsExactly("1.2.2", "6.9.0", "6.9.2", "6.9.10", "6.10.0", "10.0.0");
		assertThat(ContractTag.compare("6.10.0", "6.9.0")).isPositive();
		assertThat(ContractTag.compare("6.4.0", "6.4.0")).isZero();
		assertThat(ContractTag.compare("1.2.2", "4.0.0")).isNegative();
		assertThat(ContractTag.of("6.4.0").isAtLeast(ContractTag.of("6.4.0"))).isTrue();
		assertThat(ContractTag.of("6.3.9").isAtLeast(ContractTag.of("6.4.0"))).isFalse();
		assertThatThrownBy(() -> ContractTag.compare("6.4", "6.4.0")).isInstanceOf(IllegalArgumentException.class);
	}

	@Test
	void writesAsItIsSpelled() {
		assertThat(ContractTag.of("6.10.0")).hasToString("6.10.0");
	}
}
