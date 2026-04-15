package dev.bluestep.global.dto.lookup;

/**
 * Lookup DTO for a DSM-5 option. Shape is fixed by a legacy client interface.
 * <p>
 * <b>Naming note — DTO vs entity:</b> the field names here do <i>not</i> line up
 * one-to-one with the {@code dsm5} table / {@code Dsm5Code} entity:
 * <ul>
 *   <li>{@code label} is a <i>derived</i> display string of the form
 *       {@code "<code> <entity.label>"} (built in {@code Dsm5LookupService.toOption}).
 *       It is <b>not</b> the {@code dsm5.label} column.</li>
 *   <li>{@code name} maps to the {@code dsm5.label} column (the disorder name).</li>
 *   <li>{@code code} maps to the {@code dsm5.code} column.</li>
 * </ul>
 * The primary key on the entity side is {@code (code, label)} where {@code label}
 * is the real column — see {@code Dsm5CodeId}.
 */
public record Dsm5Option(
    String label,
    String code,
    String name,
    String category,
    String criteria
) {}
