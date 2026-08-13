package com.fimobook.backend;

public record PlayerSummary(
        long cid,
        long pid,
        String playerKor,
        String playerEng,
        int ovr,
        String position,
        String team,
        String league,
        String nation,
        String pimage,
        String bimage,
        long n8Price0) {
}
