package dev.bluestep.global.dto.lookup;

import com.fasterxml.jackson.annotation.JsonProperty;

public record Icd10Option(
    String label,
    String code,
    @JsonProperty("short") String dxShort,
    @JsonProperty("long") String dxLong,
    @JsonProperty("final") String isFinal,
    String category,
    String nonsurgical,
    String nonortho,
    String ortho,
    String majorjoint
) {}
