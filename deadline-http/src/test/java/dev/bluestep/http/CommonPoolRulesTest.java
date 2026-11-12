package dev.bluestep.http;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;

import org.junit.jupiter.api.Test;

import com.tngtech.archunit.base.DescribedPredicate;
import com.tngtech.archunit.core.domain.JavaAccess;
import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;

import dev.bluestep.http.testing.CommonPoolRules;

/**
 * The rules' self-check, ported from web-global's {@code GatewayHttpRulesTest} (#78): each rule over fixture classes
 * ({@code fixtures}) that commit one violation each, read as bytecode only; and the rules applied to this library's
 * own main code, as a consumer applies them to its own.
 */
class CommonPoolRulesTest {

	/** This library's main classes, as a consumer would import its own. */
	private static final JavaClasses MAIN = new ClassFileImporter()
		.withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
		.importPackages("dev.bluestep.http");

	/** Imported, never loaded: nothing in them is initialised. */
	private static final JavaClasses FIXTURES = new ClassFileImporter()
		.importPackages("dev.bluestep.http.fixtures");

	private static void assertCaught(DescribedPredicate<JavaAccess<?>> access, String fixture) {
		assertThatThrownBy(() -> noClasses().that().haveSimpleName(fixture).should().accessTargetWhere(access)
			.check(FIXTURES)).as(fixture).isInstanceOf(AssertionError.class).hasMessageContaining(fixture);
	}

	@Test
	void theScansFoundTheirClasses() {
		assertThat(MAIN.contain(ExecutorDeadlineHttp.class)).isTrue();
		assertThat(FIXTURES.size()).isGreaterThanOrEqualTo(9);
	}

	@Test
	void eachRuleCatchesEveryFormOfItsViolation() {
		assertCaught(CommonPoolRules.SEND_ASYNC, "CallsSendAsync");
		assertCaught(CommonPoolRules.SEND_ASYNC, "ReferencesSendAsync");
		assertCaught(CommonPoolRules.ASYNC_WITHOUT_EXECUTOR, "CallsSupplyAsync");
		assertCaught(CommonPoolRules.ASYNC_WITHOUT_EXECUTOR, "ReferencesSupplyAsync");
		assertCaught(CommonPoolRules.ASYNC_WITHOUT_EXECUTOR, "CallsInheritedAsync");
		assertCaught(CommonPoolRules.COMMON_POOL, "CallsCommonPool");
		assertCaught(CommonPoolRules.COMMON_POOL, "ReferencesCommonPool");
		assertCaught(CommonPoolRules.COMMON_POOL, "CallsDefaultExecutor");
	}

	@Test
	void asyncWorkOnANamedExecutorIsAllowed() {
		for (DescribedPredicate<JavaAccess<?>> access : List.of(CommonPoolRules.SEND_ASYNC,
				CommonPoolRules.ASYNC_WITHOUT_EXECUTOR, CommonPoolRules.COMMON_POOL)) {
			noClasses().that().haveSimpleName("UsesItsOwnExecutor").should().accessTargetWhere(access).check(FIXTURES);
		}
	}

	@Test
	void thePublishedRulesFailOverTheFixturesAndNameTheirReason() {
		assertThatThrownBy(() -> CommonPoolRules.NO_SEND_ASYNC.check(FIXTURES)).isInstanceOf(AssertionError.class)
			.hasMessageContaining("CallsSendAsync").hasMessageContaining("use DeadlineHttp");
		assertThatThrownBy(() -> CommonPoolRules.NO_ASYNC_WITHOUT_EXECUTOR.check(FIXTURES))
			.isInstanceOf(AssertionError.class).hasMessageContaining("CallsSupplyAsync");
		assertThatThrownBy(() -> CommonPoolRules.NO_COMMON_POOL.check(FIXTURES)).isInstanceOf(AssertionError.class)
			.hasMessageContaining("CallsCommonPool");
		assertThatThrownBy(() -> CommonPoolRules.checkAll(FIXTURES)).isInstanceOf(AssertionError.class);
	}

	@Test
	void anImportThatFoundNothingFailsRatherThanPassingVacuously() {
		JavaClasses nothing = new ClassFileImporter().importPackages("dev.bluestep.http.no.such.package");
		assertThat(nothing).isEmpty();
		assertThatThrownBy(() -> CommonPoolRules.checkAll(nothing)).isInstanceOf(AssertionError.class);
	}

	@Test
	void thisLibrarysMainCodeKeepsTheRules() {
		CommonPoolRules.checkAll(MAIN);
	}
}
