package dev.bluestep.contract;

import org.junit.jupiter.api.Test;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;

import dev.bluestep.http.testing.CommonPoolRules;

/** The negotiator and the filter keep deadline-http's rules: nothing here runs on the common pool. */
class NegotiationRulesTest {

	@Test
	void mainCodeStaysOffTheCommonPool() {
		JavaClasses main = new ClassFileImporter().withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
			.importPackages("dev.bluestep.contract");
		CommonPoolRules.checkAll(main);
	}
}
