package dev.bluestep.global.dto.ai;

import java.util.Arrays;
import java.util.Optional;

/**
 * The shape a descriptive pricing axis must take, and how to read one back.
 *
 * <p>{@link UnitCategory} and {@link UnitMeasure} name the axis values this codebase <em>knows</em>.
 * They are deliberately not the values it <em>permits</em>: a provider decides what it charges for and
 * in what, and a vendor is entitled to bill in a unit nobody has heard of. Since changeset 012 the
 * database accepts any {@code UPPER_SNAKE_CASE} value on either axis, and this class is where that
 * rule is written once for the DTOs, the entity and the tests to share.</p>
 *
 * <p>The enums stay because aggregation is what the axes are for — {@code SUM(cost) WHERE
 * unit_category = 'AUDIO_INPUT'} works across providers regardless of what each calls the thing — and
 * a typed constant is how a query is written without spelling a string. A value outside them
 * aggregates under its own name and prices exactly the same, because pricing never reads these
 * columns at all.</p>
 */
public final class UnitAxis {

	/**
	 * {@code UPPER_SNAKE_CASE}. Matches how the enums spell themselves, so a known value is always a
	 * valid one, and constrains the axes to a shape rather than to a vocabulary — the same guard
	 * {@link UnitKeys#KEY_PATTERN} puts on unit keys.
	 */
	public static final String PATTERN = "^[A-Z0-9]+(_[A-Z0-9]+)*$";

	/** Mirrors {@code varchar(30)} on both axis columns. */
	public static final int MAX_LENGTH = 30;

	private UnitAxis() {
	}

	/** The {@link UnitCategory} this names, or empty when it names none — see the class javadoc. */
	public static Optional<UnitCategory> category(String value) {
		return constant(UnitCategory.class, value);
	}

	/** The {@link UnitMeasure} this names, or empty when it names none. */
	public static Optional<UnitMeasure> measure(String value) {
		return constant(UnitMeasure.class, value);
	}

	private static <E extends Enum<E>> Optional<E> constant(Class<E> type, String value) {
		return Arrays.stream(type.getEnumConstants())
				.filter(constant -> constant.name().equals(value))
				.findFirst();
	}
}
