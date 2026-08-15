package com.fimobook.backend;

import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ObjectNode;

@Component
@ConditionalOnProperty(name = "fimo.price-refresh.enabled", havingValue = "true")
public class PriceRefreshWorker {

    private static final Logger LOGGER = LoggerFactory.getLogger(PriceRefreshWorker.class);

    private static final String PRICE_UPSERT = """
            INSERT INTO card_prices_current (
                cid, enhancement_level, price, observed_at, changed_at
            ) VALUES (?, ?, ?, ?, ?)
            ON DUPLICATE KEY UPDATE
                changed_at = IF(
                    observed_at <= VALUES(observed_at) AND price <> VALUES(price),
                    VALUES(changed_at), changed_at),
                price = IF(observed_at <= VALUES(observed_at), VALUES(price), price),
                observed_at = IF(observed_at <= VALUES(observed_at), VALUES(observed_at), observed_at)
            """;

    private final JdbcTemplate jdbcTemplate;
    private final SquadMakerPriceClient priceClient;
    private final JsonMapper jsonMapper;
    private final TransactionTemplate transactionTemplate;
    private final long lockTimeoutSeconds;

    public PriceRefreshWorker(
            JdbcTemplate jdbcTemplate,
            SquadMakerPriceClient priceClient,
            JsonMapper jsonMapper,
            PlatformTransactionManager transactionManager,
            @Value("${fimo.price-refresh.lock-timeout-seconds:60}") long lockTimeoutSeconds) {
        this.jdbcTemplate = jdbcTemplate;
        this.priceClient = priceClient;
        this.jsonMapper = jsonMapper;
        this.transactionTemplate = new TransactionTemplate(transactionManager);
        this.lockTimeoutSeconds = lockTimeoutSeconds;
    }

    @Scheduled(fixedDelayString = "${fimo.price-refresh.poll-delay-ms:500}")
    public void processNext() {
        RefreshJob job = claimNext();
        if (job == null) {
            return;
        }
        try {
            List<JsonNode> cards = priceClient.fetchPlayerClasses(job.pid());
            if (cards.isEmpty()) {
                throw new IllegalStateException("PlayerClass returned no cards for pid=" + job.pid());
            }
            transactionTemplate.executeWithoutResult(status -> savePrices(cards));
            jdbcTemplate.update("DELETE FROM price_refresh_jobs WHERE pid = ?", job.pid());
        } catch (RuntimeException error) {
            fail(job, error);
        }
    }

    protected RefreshJob claimNext() {
        return transactionTemplate.execute(status -> {
            jdbcTemplate.update("""
                    UPDATE price_refresh_jobs
                       SET status = 'PENDING', locked_at = NULL, available_at = NOW(),
                           last_error = 'Recovered an abandoned RUNNING job'
                     WHERE status = 'RUNNING'
                       AND locked_at < TIMESTAMPADD(SECOND, ?, NOW())
                    """, -lockTimeoutSeconds);
            List<RefreshJob> jobs = jdbcTemplate.query("""
                    SELECT pid, requested_cid, attempts
                      FROM price_refresh_jobs
                     WHERE status = 'PENDING' AND available_at <= NOW()
                     ORDER BY created_at
                     LIMIT 1
                     FOR UPDATE SKIP LOCKED
                    """, (resultSet, rowNum) -> new RefreshJob(
                    resultSet.getLong("pid"),
                    resultSet.getLong("requested_cid"),
                    resultSet.getInt("attempts")));
            if (jobs.isEmpty()) {
                return null;
            }
            RefreshJob job = jobs.get(0);
            int updated = jdbcTemplate.update("""
                    UPDATE price_refresh_jobs
                       SET status = 'RUNNING', locked_at = NOW()
                     WHERE pid = ? AND status = 'PENDING'
                    """, job.pid());
            return updated == 1 ? job : null;
        });
    }

    protected void savePrices(List<JsonNode> cards) {
        Timestamp observedAt = Timestamp.valueOf(
                LocalDateTime.ofInstant(Instant.now(), ZoneOffset.UTC));
        List<Object[]> currentRows = new ArrayList<>(cards.size() * 16);

        for (JsonNode card : cards) {
            long cid = card.path("cid").longValue();
            if (jdbcTemplate.queryForObject(
                    "SELECT COUNT(*) FROM players WHERE cid = ? AND is_active = TRUE",
                    Integer.class, cid) == 0) {
                continue;
            }
            ObjectNode prices = jsonMapper.createObjectNode();
            for (int level = 0; level <= 15; level++) {
                long price = card.path("n8Price" + level).longValue();
                prices.put("n8Price" + level, price);
                jdbcTemplate.update("""
                        INSERT INTO card_price_history (cid, enhancement_level, price, observed_at)
                        SELECT cid, enhancement_level, ?, ?
                          FROM card_prices_current
                         WHERE cid = ? AND enhancement_level = ? AND price <> ?
                           AND observed_at <= ?
                        """, price, observedAt, cid, level, price, observedAt);
                currentRows.add(new Object[] { cid, level, price, observedAt, observedAt });
            }
            jdbcTemplate.update("""
                    UPDATE players
                       SET prices_data = ?, price_checked_at = ?
                     WHERE cid = ?
                       AND (price_checked_at IS NULL OR price_checked_at <= ?)
                    """, prices.toString(), observedAt, cid, observedAt);
        }

        if (!currentRows.isEmpty()) {
            jdbcTemplate.batchUpdate(PRICE_UPSERT, currentRows);
        }
    }

    private void fail(RefreshJob job, RuntimeException error) {
        LOGGER.warn("Price refresh failed for pid={} cid={}", job.pid(), job.requestedCid(), error);
        int attempts = job.attempts() + 1;
        int delaySeconds = switch (attempts) {
            case 1 -> 5;
            case 2 -> 30;
            default -> 120;
        };
        jdbcTemplate.update("""
                UPDATE price_refresh_jobs
                   SET status = 'PENDING', attempts = ?,
                       available_at = TIMESTAMPADD(SECOND, ?, NOW()),
                       locked_at = NULL, last_error = ?
                 WHERE pid = ?
                """, attempts, delaySeconds, errorSummary(error), job.pid());
    }

    private String errorSummary(Throwable error) {
        Throwable root = error;
        while (root.getCause() != null) {
            root = root.getCause();
        }
        String message = error.getMessage();
        String rootMessage = root.getMessage();
        String summary = root == error || rootMessage == null
                ? message
                : message + ": " + rootMessage;
        if (summary == null) {
            summary = error.getClass().getSimpleName();
        }
        return abbreviate(summary);
    }

    private String abbreviate(String message) {
        return message.length() <= 1000 ? message : message.substring(0, 1000);
    }

    private record RefreshJob(long pid, long requestedCid, int attempts) {
    }
}
