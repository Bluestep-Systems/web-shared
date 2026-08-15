package dev.bluestep.global.dto.lookup;

import java.util.Optional;

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
    @JsonProperty("short") Optional<String> dxShort,
    @JsonProperty("long") Optional<String> dxLong,
    @JsonProperty("final") String isFinal,
    Optional<String> category,
    Optional<String> nonsurgical,
    Optional<String> nonortho,
    Optional<String> ortho,
    Optional<String> majorjoint
) {}
