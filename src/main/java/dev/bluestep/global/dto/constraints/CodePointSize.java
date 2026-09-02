package dev.bluestep.global.dto.constraints;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;

/**
 * A string length bound counted the way the database counts it: in <b>code points</b>, not in Java's
 * UTF-16 code units.
 *
 * <h2>Why {@code @Size} is the wrong tool for a column width</h2>
 *
 * <p>{@code @Size} measures {@link String#length()}, which counts UTF-16 code units. PostgreSQL's
 * {@code varchar(n)} counts characters. For every character outside the Basic Multilingual Plane —
 * many CJK extension ideographs, historic scripts, emoji — Java counts two where PostgreSQL counts
 * one, so {@code @Size(max = 30)} refuses a sixteen-character name that {@code varchar(30)} stores
 * without complaint.</p>
 *
 * <p>That is not merely over-strict. These bounds sit on <b>whole-record</b> update shapes, where a
 * read hands the stored value back verbatim and the caller sends it again. A row already holding such
 * a value would become <em>un-editable through the API</em>: {@code GET}, change a surname,
 * {@code PUT}, 400, with nothing the caller can do to comply. Counting code points removes that
 * failure mode by construction rather than making it rarer — and it is what lets the surrounding
 * documentation say truthfully that these constraints refuse only what the database refuses anyway.
 * A bound that is stricter than its column has to describe itself as something else.</p>
 *
 * <h2>Why an annotation and not a compact constructor</h2>
 *
 * <p>A compact constructor throws during Jackson binding, <em>before</em> Bean Validation runs. That
 * produces a {@code HttpMessageNotReadableException} — a 400 with no {@code fieldErrors} array and no
 * field name — rather than the constraint-violation shape the rest of this package produces and the
 * consuming services' tests assert. Two different 400 shapes for one class of mistake is worse than a
 * slightly longer annotation.</p>
 *
 * <p>Declared {@link ElementType#TYPE_USE} so it can be written as a container-element constraint —
 * {@code Optional<@CodePointSize(max = 30) String>} — which is how every optional component in this
 * package carries its bound. A constraint placed on the {@code Optional} itself is checked against the
 * container and fails at runtime with "no validator could be found".</p>
 *
 * <p>{@code null} is valid, as it is for every Bean Validation size constraint: absence is
 * {@code @NotNull}'s or {@code @NotBlank}'s question, and a constraint answering it too would report
 * one omission twice.</p>
 *
 * <p>No {@code min}. Every use here mirrors a column width, which is a maximum; a minimum would be a
 * different kind of rule with no column to derive it from.</p>
 */
@Documented
@Constraint(validatedBy = CodePointSizeValidator.class)
@Target({ElementType.FIELD, ElementType.METHOD, ElementType.PARAMETER, ElementType.TYPE_USE})
@Retention(RetentionPolicy.RUNTIME)
public @interface CodePointSize {

	/** The largest number of code points allowed — the column's declared width. */
	int max();

	String message() default "must be at most {max} characters";

	Class<?>[] groups() default {};

	Class<? extends Payload>[] payload() default {};
}
