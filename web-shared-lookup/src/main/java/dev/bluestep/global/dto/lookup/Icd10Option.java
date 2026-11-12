package dev.bluestep.global.dto.lookup;

import java.util.Optional;

import org.jspecify.annotations.Nullable;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Lookup DTO for an ICD-10 option. Only {@code code} is NOT NULL in the underlying
 * {@code icd10}/{@code icd10next} views — every descriptive and classification column
 * is nullable there, so each is carried as an {@code Optional}. {@code label} and
 * {@code isFinal} are derived server-side and are always present.
 */
public record Icd10Option(
    String label,
    String code,
    @JsonProperty("short") @Nullable String dxShort,
    @JsonProperty("long") @Nullable String dxLong,
    @JsonProperty("final") String isFinal,
    @Nullable String category,
    @Nullable String nonsurgical,
    @Nullable String nonortho,
    @Nullable String ortho,
    @Nullable String majorjoint
) {

    /** {@link #dxShort}, as an {@code Optional}. */
    public Optional<String> optDxShort() {
        return Optional.ofNullable(dxShort);
    }

    /** {@link #dxLong}, as an {@code Optional}. */
    public Optional<String> optDxLong() {
        return Optional.ofNullable(dxLong);
    }

    /** {@link #category}, as an {@code Optional}. */
    public Optional<String> optCategory() {
        return Optional.ofNullable(category);
    }

    /** {@link #nonsurgical}, as an {@code Optional}. */
    public Optional<String> optNonsurgical() {
        return Optional.ofNullable(nonsurgical);
    }

    /** {@link #nonortho}, as an {@code Optional}. */
    public Optional<String> optNonortho() {
        return Optional.ofNullable(nonortho);
    }

    /** {@link #ortho}, as an {@code Optional}. */
    public Optional<String> optOrtho() {
        return Optional.ofNullable(ortho);
    }

    /** {@link #majorjoint}, as an {@code Optional}. */
    public Optional<String> optMajorjoint() {
        return Optional.ofNullable(majorjoint);
    }
}
