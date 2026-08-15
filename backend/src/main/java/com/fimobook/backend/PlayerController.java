package com.fimobook.backend;

import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import tools.jackson.databind.JsonNode;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/players")

public class PlayerController {

    private static final Logger LOGGER = LoggerFactory.getLogger(PlayerController.class);

    private final PlayerRepository playerRepository;
    private final java.util.Optional<PriceRefreshService> priceRefreshService;

    public PlayerController(
            PlayerRepository playerRepository,
            java.util.Optional<PriceRefreshService> priceRefreshService) {
        this.playerRepository = playerRepository;
        this.priceRefreshService = priceRefreshService;
    }

    @GetMapping
    public List<JsonNode> getPlayers(@RequestParam(defaultValue = "") String name) {
        String query = name.trim();
        return query.isEmpty() ? List.of() : playerRepository.findByPlayerNameContaining(query);
    }

    @GetMapping("/search")
    public PlayerSearchResponse search(
            @RequestParam(defaultValue = "") String name,
            @RequestParam(defaultValue = "") String position,
            @RequestParam(defaultValue = "") String classId,
            @RequestParam(required = false) Long leagueId,
            @RequestParam(required = false) Long teamId,
            @RequestParam(required = false) Long nationId,
            @RequestParam(defaultValue = "true") Boolean tradeable,
            @RequestParam(required = false) Integer minOvr,
            @RequestParam(required = false) Integer maxOvr,
            @RequestParam(defaultValue = "0") int priceLevel,
            @RequestParam(required = false) Long minPrice,
            @RequestParam(required = false) Long maxPrice,
            @RequestParam(required = false) Long traitId,
            @RequestParam(defaultValue = "") String playStyleId,
            @RequestParam(defaultValue = "ovrDesc") String sort,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        int safePage = Math.max(page, 0);
        int safeSize = Math.min(Math.max(size, 1), 50);
        int safePriceLevel = Math.min(Math.max(priceLevel, 0), 15);
        var criteria = new PlayerSearchCriteria(
                name.trim(), position.trim().toUpperCase(), classId.trim(),
                leagueId, teamId, nationId, tradeable, minOvr, maxOvr,
                safePriceLevel, minPrice, maxPrice, traitId, playStyleId.trim(), sort,
                safePage, safeSize);
        return playerRepository.search(criteria);
    }

    @GetMapping("/{cid}")
    public JsonNode detail(@PathVariable long cid) {
        JsonNode player = playerRepository.findByCid(cid)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "선수 카드를 찾을 수 없습니다."));
        priceRefreshService.ifPresent(service -> {
            try {
                service.requestIfStale(cid);
            } catch (RuntimeException error) {
                LOGGER.warn("Failed to enqueue a price refresh for cid={}", cid, error);
            }
        });
        return player;
    }
}
