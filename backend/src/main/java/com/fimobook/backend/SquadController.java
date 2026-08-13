package com.fimobook.backend;

import java.util.List;
import java.util.Set;

import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/squads/me")
public class SquadController {

    public record SquadSlotRequest(String slotId, long cid) {
    }

    private static final Set<String> SLOT_IDS = Set.of(
            "gk", "lb", "cb1", "cb2", "rb", "cm1", "cdm", "cm2", "lw", "st", "rw");

    private final SquadRepository repository;

    public SquadController(SquadRepository repository) {
        this.repository = repository;
    }

    @GetMapping
    public List<SquadRepository.SquadPlayer> get(Authentication authentication) {
        return repository.findDefault(userId(authentication));
    }

    @PutMapping
    public List<SquadRepository.SquadPlayer> save(@RequestBody List<SquadSlotRequest> slots,
            Authentication authentication) {
        if (slots.stream().anyMatch(slot -> slot.slotId() == null || !SLOT_IDS.contains(slot.slotId()))) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "알 수 없는 스쿼드 슬롯입니다.");
        }
        return repository.saveDefault(userId(authentication), slots);
    }

    private long userId(Authentication authentication) {
        return (Long) authentication.getPrincipal();
    }
}
