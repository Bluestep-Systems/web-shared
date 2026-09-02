/**
 * Bean Validation constraints this module defines for itself, where the built-in ones do not say what
 * these DTOs mean.
 *
 * <p>There is one, and the bar for adding another is that a standard constraint is not merely verbose
 * but <em>wrong</em>: {@link dev.bluestep.global.dto.constraints.CodePointSize} exists because
 * {@code @Size} counts UTF-16 code units while the columns these DTOs mirror count characters, so the
 * standard annotation cannot express a column width truthfully. A custom constraint that only saved
 * typing would be a private dialect for every consumer to learn.</p>
 *
 * <p>Null-marked: every type here is non-null unless explicitly annotated
 * {@link org.jspecify.annotations.Nullable}.</p>
 */
@NullMarked
package dev.bluestep.global.dto.constraints;

import org.jspecify.annotations.NullMarked;
