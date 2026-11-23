package dev.bluestep.global.dto.lookup;

import java.util.List;

public record LookupResponse<T>(
    int seq,
    List<T> options
) {}
