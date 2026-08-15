package com.fimobook.backend;

import java.util.List;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/player-metadata")
public class PlayerMetadataController {

    private final PlayerMetadataRepository repository;

    public PlayerMetadataController(PlayerMetadataRepository repository) {
        this.repository = repository;
    }

    @GetMapping
    public PlayerFilterMetadata filters() {
        return repository.filters();
    }

    @GetMapping("/teams")
    public List<FilterOption> teams(
            @RequestParam(required = false) Long leagueId,
            @RequestParam(defaultValue = "") String name,
            @RequestParam(defaultValue = "100") int limit) {
        return repository.teams(leagueId, name.trim(), Math.min(Math.max(limit, 1), 200));
    }
}
