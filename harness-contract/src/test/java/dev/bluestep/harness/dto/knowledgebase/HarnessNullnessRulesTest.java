package dev.bluestep.harness.dto.knowledgebase;

import static org.assertj.core.api.Assertions.assertThat;

import java.lang.annotation.Annotation;
import java.lang.reflect.RecordComponent;
import java.util.List;
import java.util.Optional;

import org.jspecify.annotations.NullMarked;
import org.junit.jupiter.api.Test;

import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;

/**
 * The DTO nullness rule, held for this artifact as the root library holds it for its own: every package is
 * {@code @NullMarked} (NullAway skips one that is not), and no record component is {@code Optional} or
 * {@code @Nullable}: in 1.0.0 nothing on the harness wire may be absent.
 */
class HarnessNullnessRulesTest {

	static final JavaClasses MAIN = new ClassFileImporter().withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
		.importPackages("dev.bluestep.harness");

	static List<Class<?>> records() {
		return MAIN.stream().map(JavaClass::reflect).filter(Class::isRecord).<Class<?>>map(type -> type).toList();
	}

	@Test
	void theScanFoundTheRecords() {
		assertThat(records()).hasSizeGreaterThanOrEqualTo(2);
	}

	@Test
	void everyPackageIsNullMarked() {
		assertThat(MAIN).allSatisfy(type -> assertThat(type.reflect().getPackage().getAnnotation(NullMarked.class))
			.as(type.getPackageName()).isNotNull());
	}

	@Test
	void noComponentIsOptionalOrNullable() {
		for (Class<?> record : records()) {
			for (RecordComponent component : record.getRecordComponents()) {
				String name = record.getSimpleName() + "." + component.getName();
				assertThat(component.getType()).as(name).isNotEqualTo(Optional.class);
				for (Annotation annotation : component.getAnnotatedType().getAnnotations()) {
					assertThat(annotation.annotationType().getSimpleName()).as(name).isNotEqualTo("Nullable");
				}
			}
		}
	}
}
