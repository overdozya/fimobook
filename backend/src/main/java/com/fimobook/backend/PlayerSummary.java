package com.fimobook.backend;

import tools.jackson.databind.JsonNode;

public record PlayerSummary(
        long cid,
        long pid,
        JsonNode classes,
        String playerKor,
        String playerEng,
        int ovr,
        String position,
        String team,
        String league,
        String nation,
        String pimage,
        String bimage,
        PlayerCardAssets assets,
        CardVisualTheme cardTheme,
        boolean tradeable,
        long n8Price0,
        int priceLevel,
        long price) {
}
