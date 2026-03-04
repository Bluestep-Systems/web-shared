package dev.bluestep.global.dto.icd10updater;

public record Icd10DiffResponse(String table, int rowsAdded, int rowsUpdated, int rowsDeleted) {
}
