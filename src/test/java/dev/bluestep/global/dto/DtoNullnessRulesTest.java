package dev.bluestep.global.dto;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.annotation.Annotation;
import java.lang.reflect.AnnotatedArrayType;
import java.lang.reflect.AnnotatedParameterizedType;
import java.lang.reflect.AnnotatedType;
import java.lang.reflect.Method;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.RecordComponent;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.OptionalDouble;
import java.util.OptionalInt;
import java.util.OptionalLong;
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
 *   <li>a record component that may be absent is a top-level {@code @Nullable T}, named for the
 *       value, with a public {@code optX()} accessor returning the matching {@code Optional}
 *       ({@code OptionalInt}, {@code OptionalLong} or {@code OptionalDouble} for a boxed
 *       {@code Integer}, {@code Long} or {@code Double}), and never an {@code Optional} component.
 *       Under {@code @NullMarked}, components are non-null by default but an {@code Optional}
 *       component stays legal to the compiler, so this class is what holds every DTO package to
 *       the rule (6.0.0 moved the last packages off the older {@code Optional<T>} spelling).</li>
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
	 * The rule, for every DTO record: no component is
	 * {@code Optional}, a {@code @Nullable} is on the component's own type and never inside a type
	 * argument or an array, and each {@code @Nullable} component {@code x} has a public
	 * {@code optX()} taking nothing and returning {@code Optional} (or {@code OptionalInt},
	 * {@code OptionalLong}, {@code OptionalDouble} for a boxed primitive, and only those), the accessor callers
	 * outside the record read.
	 */
	@Test
	void absenceIsSpelledAsNullablePlusOptAccessor() {
		List<String> offenders = new ArrayList<>();
		int nullableComponents = 0;
		for (Class<?> record : records()) {
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
								+ capitalized(component.getName()) + "() returning an Optional of its type");
					}
				}
			}
		}
		assertTrue(offenders.isEmpty(), "nullable-component rule broken: " + offenders);
		assertTrue(nullableComponents > 0,
				"no @Nullable component found under " + DTO_ROOT + " — the scan is broken");
	}

	/** Whether the type or anything inside it is annotated {@code @Nullable}. */
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

	/**
	 * Whether the record has a public {@code optX()} returning the component wrapped: the primitive
	 * optional for a boxed {@code Integer}, {@code Long} or {@code Double} (never
	 * {@code Optional<Integer>}), and otherwise an {@code Optional} of exactly the component's type. An {@code Optional<String>} accessor for a
	 * {@code UsageCategory} component does not count.
	 */
	private static boolean hasOptAccessor(Class<?> record, RecordComponent component) {
		try {
			Method accessor = record.getMethod("opt" + capitalized(component.getName()));
			Class<?> returned = accessor.getReturnType();
			Class<?> type = component.getType();
			if (type == Integer.class) {
				return returned == OptionalInt.class;
			}
			if (type == Long.class) {
				return returned == OptionalLong.class;
			}
			if (type == Double.class) {
				return returned == OptionalDouble.class;
			}
			return returned == Optional.class
					&& accessor.getGenericReturnType() instanceof ParameterizedType parameterized
					&& parameterized.getActualTypeArguments()[0].equals(component.getGenericType());
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
