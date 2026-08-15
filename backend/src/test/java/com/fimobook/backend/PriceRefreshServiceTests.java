package com.fimobook.backend;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;

class PriceRefreshServiceTests {

    private final PlayerRepository repository = mock(PlayerRepository.class);
    private final JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
    private final PriceRefreshService service = new PriceRefreshService(repository, jdbcTemplate, 3);

    @Test
    void queuesOnePidJobForStaleTradeableCard() {
        when(repository.findRefreshCandidate(10L)).thenReturn(Optional.of(
                new PriceRefreshCandidate(
                        10L, 20L, true, LocalDateTime.now(ZoneOffset.UTC).minusHours(4))));

        service.requestIfStale(10L);

        verify(jdbcTemplate).update(anyString(), eq(20L), eq(10L));
    }

    @Test
    void doesNotQueueFreshCard() {
        when(repository.findRefreshCandidate(10L)).thenReturn(Optional.of(
                new PriceRefreshCandidate(
                        10L, 20L, true, LocalDateTime.now(ZoneOffset.UTC).minusMinutes(30))));

        service.requestIfStale(10L);

        verifyNoInteractions(jdbcTemplate);
    }

    @Test
    void doesNotQueueUntradeableCard() {
        when(repository.findRefreshCandidate(10L)).thenReturn(Optional.of(
                new PriceRefreshCandidate(10L, 20L, false, null)));

        service.requestIfStale(10L);

        verify(jdbcTemplate, never()).update(anyString(), eq(20L), eq(10L));
    }
}
