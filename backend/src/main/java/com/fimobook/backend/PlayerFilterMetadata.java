package com.fimobook.backend;

import java.util.List;

public record PlayerFilterMetadata(
        List<FilterOption> classes,
        List<FilterOption> nations,
        List<FilterOption> leagues,
        List<FilterOption> traits,
        List<FilterOption> playStyles) {
}
