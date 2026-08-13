package com.fimobook.backend;

import java.util.List;

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

    private final PlayerRepository playerRepository;

    public PlayerController(PlayerRepository playerRepository) {
        this.playerRepository = playerRepository;
    }

    @GetMapping
    public List<JsonNode> getPlayers(@RequestParam(defaultValue = "") String name) {
        return playerRepository.findByPlayerNameContaining(name);
    }

    @GetMapping("/search")
    public PlayerSearchResponse search(
            @RequestParam(defaultValue = "") String name,
            @RequestParam(defaultValue = "") String position,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        int safePage = Math.max(page, 0);
        int safeSize = Math.min(Math.max(size, 1), 50);
        return playerRepository.search(name.trim(), position.trim().toUpperCase(), safePage, safeSize);
    }

    @GetMapping("/{cid}")
    public JsonNode detail(@PathVariable long cid) {
        return playerRepository.findByCid(cid)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "선수 카드를 찾을 수 없습니다."));
    }
}
