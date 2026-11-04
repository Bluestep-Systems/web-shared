package dev.bluestep.global.dto.knowledgebase;

import org.jspecify.annotations.Nullable;

import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;

/**
 * Checks {@link KnowledgeBaseTitle} with {@link GlobalKnowledgeBase#title(String)} itself, so the
 * request's edge and the writer that stores the title cannot disagree.
 */
public class KnowledgeBaseTitleValidator implements ConstraintValidator<KnowledgeBaseTitle, String> {

	@Override
	public boolean isValid(@Nullable String value, ConstraintValidatorContext context) {
		return value == null || GlobalKnowledgeBase.title(value).isPresent();
	}
}
