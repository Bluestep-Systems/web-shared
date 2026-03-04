package dev.bluestep.global.dto.icd10updater;

import java.util.List;
import java.util.Map;

public record Icd10StatusResponse(Map<String, String> views, List<Icd10TableInfo> tables) {
}
