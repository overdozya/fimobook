package com.fimobook.backend;

import java.util.List;

public record PlayerSearchResponse(
        List<PlayerSummary> players,
        int page,
        int size,
        long totalElements,
        int totalPages) {
}
