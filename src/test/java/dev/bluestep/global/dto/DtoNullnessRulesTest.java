package dev.bluestep.global.dto;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.annotation.Annotation;
import java.lang.reflect.AnnotatedArrayType;
import java.lang.reflect.AnnotatedParameterizedType;
import java.lang.reflect.AnnotatedType;
import java.lang.reflect.RecordComponent;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.TreeSet;

import org.jspecify.annotations.NullMarked;
import org.junit.jupiter.api.Test;

import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;

/**
 * Enforces the two halves of the DTO nullness rule that the compiler alone cannot pin:
 *
 * <ol>
 *   <li>every DTO package opts into JSpecify via a {@code @NullMarked} package-info —
 *       NullAway runs with {@code OnlyNullMarked=true}, so a package that forgets the
 *       annotation is silently <em>unanalysed</em> rather than loudly broken; and</li>
 *   <li>no record component is declared {@code @Nullable}. Under {@code @NullMarked},
 *       components are non-null by default but {@code @Nullable} remains legal — this is
 *       the rule that forces {@code Optional<T>} instead.</li>
 * </ol>
 *
 * <p>Both are declaration-level rules, which is the level the rule actually lives at:
 * nullness here is enforced at compile time on both sides of the wire, so there are no
 * runtime guards to assert against.</p>
 */
class DtoNullnessRulesTest {

	private static final String DTO_ROOT = "dev.bluestep.global.dto";

	private static final JavaClasses DTO_CLASSES = new ClassFileImporter()
			.withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
			.importPackages(DTO_ROOT);

	/**
	 * An ArchUnit suite that imports nothing passes every rule vacuously. Pin the scan
	 * so a packaging or import-option change can never turn this class into a no-op.
	 */
	@Test
	void scanFoundTheDtoRecords() {
		List<Class<?>> records = records();
		assertFalse(records.isEmpty(), "no records found under " + DTO_ROOT + " — the scan is broken");
		assertTrue(records.size() >= 20,
				"expected the full DTO surface (~26 records), found " + records.size() + ": " + names(records));
	}

	@Test
	void everyDtoPackageIsNullMarked() {
		TreeSet<String> unmarked = new TreeSet<>();
		for (JavaClass javaClass : DTO_CLASSES) {
			Package pkg = javaClass.reflect().getPackage();
			if (pkg == null || pkg.getAnnotation(NullMarked.class) == null) {
				unmarked.add(javaClass.getPackageName());
			}
		}
		assertTrue(unmarked.isEmpty(),
				"DTO packages without a @NullMarked package-info (NullAway will skip them entirely): " + unmarked);
	}

	/**
	 * The decree: a record component that may be absent is {@code Optional<T>}, never a
	 * {@code @Nullable} reference. Checks the annotated <em>type</em> (JSpecify's
	 * {@code @Nullable} is {@code TYPE_USE}) and recurses through type arguments and
	 * array components, so {@code List<@Nullable String>} is caught too.
	 */
	@Test
	void noRecordComponentIsDeclaredNullable() {
		List<String> offenders = new ArrayList<>();
		for (Class<?> record : records()) {
			for (RecordComponent component : record.getRecordComponents()) {
				if (mentionsNullable(component.getAnnotatedType())) {
					offenders.add(record.getSimpleName() + "." + component.getName());
				}
			}
		}
		assertTrue(offenders.isEmpty(),
				"record components may not be @Nullable — wrap them in Optional<T> instead: " + offenders);
	}

	/**
	 * A raw {@code Optional} component would defeat the point: its contents would be
	 * unanalysable and callers would get {@code Object}.
	 */
	@Test
	void everyOptionalComponentIsParameterized() {
		List<String> offenders = new ArrayList<>();
		for (Class<?> record : records()) {
			for (RecordComponent component : record.getRecordComponents()) {
				if (component.getType() == Optional.class
						&& !(component.getAnnotatedType() instanceof AnnotatedParameterizedType)) {
					offenders.add(record.getSimpleName() + "." + component.getName());
				}
			}
		}
		assertTrue(offenders.isEmpty(), "raw Optional record components: " + offenders);
	}

	private static boolean mentionsNullable(AnnotatedType type) {
		for (Annotation annotation : type.getAnnotations()) {
			if ("Nullable".equals(annotation.annotationType().getSimpleName())) {
				return true;
			}
		}
		if (type instanceof AnnotatedParameterizedType parameterized) {
			for (AnnotatedType argument : parameterized.getAnnotatedActualTypeArguments()) {
				if (mentionsNullable(argument)) {
					return true;
				}
			}
		}
		if (type instanceof AnnotatedArrayType array) {
			return mentionsNullable(array.getAnnotatedGenericComponentType());
		}
		return false;
	}

	private static List<Class<?>> records() {
		List<Class<?>> records = new ArrayList<>();
		for (JavaClass javaClass : DTO_CLASSES) {
			Class<?> reflected = javaClass.reflect();
			if (reflected.isRecord()) {
				records.add(reflected);
			}
		}
		return records;
	}

	private static List<String> names(List<Class<?>> classes) {
		return classes.stream().map(Class::getSimpleName).sorted().toList();
	}
}
