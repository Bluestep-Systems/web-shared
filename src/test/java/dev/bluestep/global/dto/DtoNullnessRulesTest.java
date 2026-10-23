package dev.bluestep.global.dto;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.annotation.Annotation;
import java.lang.reflect.AnnotatedArrayType;
import java.lang.reflect.AnnotatedParameterizedType;
import java.lang.reflect.AnnotatedType;
import java.lang.reflect.Method;
import java.lang.reflect.RecordComponent;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.OptionalDouble;
import java.util.OptionalInt;
import java.util.OptionalLong;
import java.util.Set;
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
 *   <li>a record component that may be absent is spelled one way per package. The older
 *       packages declare it {@code Optional<T>} and never {@code @Nullable}. The packages in
 *       {@link #NULLABLE_COMPONENT_PACKAGES} follow the newer rule the monolith's Java
 *       conventions set: a top-level {@code @Nullable T}, named for the value, with a public
 *       {@code optX()} accessor returning the matching {@code Optional}, and never an
 *       {@code Optional} component. Under {@code @NullMarked}, components are non-null by
 *       default but both spellings stay legal to the compiler, so this class is what holds
 *       each package to its own.</li>
 * </ol>
 *
 * <p>Both are declaration-level rules, which is the level the rule actually lives at:
 * nullness here is enforced at compile time on both sides of the wire, so there are no
 * runtime guards to assert against.</p>
 */
class DtoNullnessRulesTest {

	private static final String DTO_ROOT = "dev.bluestep.global.dto";

	/**
	 * The packages, each with its subpackages, whose absent-able record components are a
	 * {@code @Nullable} value plus an {@code optX()} accessor rather than {@code Optional<T>}.
	 * A package joins once it holds no {@code Optional} component; the rest keep the older rule.
	 */
	private static final Set<String> NULLABLE_COMPONENT_PACKAGES = Set.of(DTO_ROOT + ".usage");

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
	 * The older decree, for every package not in {@link #NULLABLE_COMPONENT_PACKAGES}: a record
	 * component that may be absent is {@code Optional<T>}, never a {@code @Nullable} reference.
	 * Checks the annotated <em>type</em> (JSpecify's {@code @Nullable} is {@code TYPE_USE}) and
	 * recurses through type arguments and array components, so {@code List<@Nullable String>} is
	 * caught too.
	 */
	@Test
	void noRecordComponentIsDeclaredNullable() {
		List<String> offenders = new ArrayList<>();
		for (Class<?> record : records()) {
			if (onNullableComponentRule(record)) {
				continue;
			}
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
	 * The newer rule, for the packages in {@link #NULLABLE_COMPONENT_PACKAGES}: no component is
	 * {@code Optional}, a {@code @Nullable} is on the component's own type and never inside a type
	 * argument or an array, and each {@code @Nullable} component {@code x} has a public
	 * {@code optX()} taking nothing and returning {@code Optional} (or {@code OptionalInt},
	 * {@code OptionalLong}, {@code OptionalDouble} for a boxed primitive), the accessor callers
	 * outside the record read.
	 */
	@Test
	void nullableComponentPackagesSpellAbsenceAsNullablePlusOptAccessor() {
		List<String> offenders = new ArrayList<>();
		int nullableComponents = 0;
		for (Class<?> record : records()) {
			if (!onNullableComponentRule(record)) {
				continue;
			}
			for (RecordComponent component : record.getRecordComponents()) {
				String name = record.getSimpleName() + "." + component.getName();
				AnnotatedType type = component.getAnnotatedType();
				if (component.getType() == Optional.class) {
					offenders.add(name + " is Optional");
				}
				if (mentionsNullableInside(type)) {
					offenders.add(name + " has @Nullable inside its type");
				}
				if (isNullable(type)) {
					nullableComponents++;
					if (!hasOptAccessor(record, component)) {
						offenders.add(name + " is @Nullable without a public opt"
								+ capitalized(component.getName()) + "() returning Optional");
					}
				}
			}
		}
		assertTrue(offenders.isEmpty(), "nullable-component rule broken: " + offenders);
		assertTrue(nullableComponents > 0,
				"no @Nullable component found in " + NULLABLE_COMPONENT_PACKAGES + " — the scan is broken");
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
		return isNullable(type) || mentionsNullableInside(type);
	}

	/** Whether the type itself, not anything inside it, is annotated {@code @Nullable}. */
	private static boolean isNullable(AnnotatedType type) {
		for (Annotation annotation : type.getAnnotations()) {
			if ("Nullable".equals(annotation.annotationType().getSimpleName())) {
				return true;
			}
		}
		return false;
	}

	/** Whether a type argument or array component of the type mentions {@code @Nullable}. */
	private static boolean mentionsNullableInside(AnnotatedType type) {
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

	private static boolean onNullableComponentRule(Class<?> record) {
		String pkg = record.getPackageName();
		for (String root : NULLABLE_COMPONENT_PACKAGES) {
			if (pkg.equals(root) || pkg.startsWith(root + ".")) {
				return true;
			}
		}
		return false;
	}

	private static boolean hasOptAccessor(Class<?> record, RecordComponent component) {
		try {
			Method accessor = record.getMethod("opt" + capitalized(component.getName()));
			Class<?> returned = accessor.getReturnType();
			return returned == Optional.class || returned == OptionalInt.class
					|| returned == OptionalLong.class || returned == OptionalDouble.class;
		} catch (NoSuchMethodException absent) {
			return false;
		}
	}

	private static String capitalized(String name) {
		return Character.toUpperCase(name.charAt(0)) + name.substring(1);
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
