package dev.bluestep.global.dto.knowledgebase;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;

/**
 * A request's title follows {@link GlobalKnowledgeBase#title(String)}'s rule, judged on the value that
 * rule stores. That value is trimmed, so surrounding whitespace never counts against the length or as a
 * control character: {@code " Handbook "} and {@code "\tHandbook\n"} are valid and store as
 * {@code Handbook}.
 *
 * <p>Absent ({@code null}) is valid: an absent title takes the name on a create and is kept on a patch.
 * A present title that is blank once trimmed is not.</p>
 */
@Documented
@Constraint(validatedBy = KnowledgeBaseTitleValidator.class)
@Target({ElementType.FIELD, ElementType.METHOD, ElementType.PARAMETER, ElementType.TYPE_USE})
@Retention(RetentionPolicy.RUNTIME)
public @interface KnowledgeBaseTitle {

	String message() default "must hold something besides spaces, at most " + GlobalKnowledgeBase.MAX_TITLE_LENGTH
			+ " characters once trimmed, and no control character";

	Class<?>[] groups() default {};

	Class<? extends Payload>[] payload() default {};
}
