package dev.bluestep.global.dto.lookup;

public record Icd10Option(
    String code,
    String dxShort,
    String dxLong,
    boolean isFinal,
    String category,
    String nonsurgical,
    String nonortho,
    String ortho,
    String majorjoint
) {}
