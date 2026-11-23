package dev.bluestep.global.dto.constraints;

import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;
import org.jspecify.annotations.Nullable;

/**
 * Counts {@link CodePointSize}'s bound in code points, matching PostgreSQL's {@code varchar(n)}.
 *
 * <p>{@link String#codePointCount} is the whole implementation: it walks the UTF-16 sequence pairing
 * surrogates, so a supplementary-plane character counts once here exactly as it counts once in the
 * column. Java's own {@code length()} would count it twice, which is the bug this constraint exists
 * to avoid.</p>
 *
 * <p>An unpaired surrogate — which a caller can send, since JSON strings are not required to be
 * well-formed UTF-16 — counts as one, and so does PostgreSQL's replacement of it. Neither side
 * silently agrees with the other on what such a value <em>means</em>, but they agree on its length,
 * which is all this constraint claims.</p>
 */
public class CodePointSizeValidator implements ConstraintValidator<CodePointSize, String> {

	private int max;

	@Override
	public void initialize(CodePointSize constraint) {
		this.max = constraint.max();
	}

	@Override
	public boolean isValid(@Nullable String value, ConstraintValidatorContext context) {
		// Absence belongs to @NotNull / @NotBlank. Answering it here too would report one omission
		// twice, which is the noise that makes a fieldErrors array hard to act on.
		return value == null || value.codePointCount(0, value.length()) <= max;
	}
}
