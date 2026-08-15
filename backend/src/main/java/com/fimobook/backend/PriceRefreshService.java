package com.fimobook.backend;

import java.time.Duration;
import java.time.LocalDateTime;
import java.time.ZoneOffset;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

@Service
@ConditionalOnProperty(name = "fimo.price-refresh.enabled", havingValue = "true")
public class PriceRefreshService {

    private final PlayerRepository playerRepository;
    private final JdbcTemplate jdbcTemplate;
    private final Duration cacheDuration;

    public PriceRefreshService(
            PlayerRepository playerRepository,
            JdbcTemplate jdbcTemplate,
            @Value("${fimo.price-refresh.cache-hours:3}") long cacheHours) {
        this.playerRepository = playerRepository;
        this.jdbcTemplate = jdbcTemplate;
        this.cacheDuration = Duration.ofHours(cacheHours);
    }

    public void requestIfStale(long cid) {
        playerRepository.findRefreshCandidate(cid).ifPresent(candidate -> {
            if (!candidate.tradeable() || isFresh(candidate.priceCheckedAt())) {
                return;
            }
            jdbcTemplate.update("""
                    INSERT INTO price_refresh_jobs (
                        pid, requested_cid, status, attempts, available_at, locked_at, last_error
                    ) VALUES (?, ?, 'PENDING', 0, NOW(), NULL, NULL)
                    ON DUPLICATE KEY UPDATE
                        requested_cid = VALUES(requested_cid),
                        available_at = IF(status = 'RUNNING', available_at, LEAST(available_at, NOW())),
                        status = IF(status = 'RUNNING', status, 'PENDING')
                    """, candidate.pid(), candidate.cid());
        });
    }

    private boolean isFresh(LocalDateTime checkedAt) {
        return checkedAt != null
                && checkedAt.isAfter(LocalDateTime.now(ZoneOffset.UTC).minus(cacheDuration));
    }
}
