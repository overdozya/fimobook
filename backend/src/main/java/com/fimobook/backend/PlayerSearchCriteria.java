package com.fimobook.backend;

public record PlayerSearchCriteria(
        String name,
        String position,
        String classId,
        Long leagueId,
        Long teamId,
        Long nationId,
        Boolean tradeable,
        Integer minOvr,
        Integer maxOvr,
        int priceLevel,
        Long minPrice,
        Long maxPrice,
        Long traitId,
        String playStyleId,
        String sort,
        int page,
        int size) {
}
