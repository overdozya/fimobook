package com.fimobook.backend;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.Base64;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class RefreshTokenService {

    public record Rotation(long userId, String refreshToken) {
    }

    private final JdbcClient jdbcClient;
    private final SecureRandom secureRandom = new SecureRandom();
    private final long expirationDays;

    public RefreshTokenService(JdbcClient jdbcClient,
            @Value("${fimo.jwt.refresh-expiration-days:30}") long expirationDays) {
        this.jdbcClient = jdbcClient;
        this.expirationDays = expirationDays;
    }

    public String create(long userId) {
        byte[] bytes = new byte[32];
        secureRandom.nextBytes(bytes);
        String token = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
        jdbcClient.sql("""
                INSERT INTO auth_refresh_tokens (user_id, token_hash, expires_at)
                VALUES (:userId, :tokenHash, :expiresAt)
                """).param("userId", userId).param("tokenHash", hash(token))
                .param("expiresAt", LocalDateTime.now().plusDays(expirationDays)).update();
        return token;
    }

    @Transactional
    public Rotation rotate(String token) {
        if (token == null || token.isBlank()) throw unauthorized();
        var stored = jdbcClient.sql("""
                SELECT id, user_id, expires_at, revoked_at
                  FROM auth_refresh_tokens
                 WHERE token_hash=:tokenHash
                 FOR UPDATE
                """).param("tokenHash", hash(token))
                .query((rs, rowNum) -> new StoredToken(rs.getLong("id"), rs.getLong("user_id"),
                        rs.getTimestamp("expires_at").toLocalDateTime(),
                        rs.getTimestamp("revoked_at") == null ? null : rs.getTimestamp("revoked_at").toLocalDateTime()))
                .optional().orElseThrow(this::unauthorized);
        if (stored.revokedAt() != null || !stored.expiresAt().isAfter(LocalDateTime.now())) {
            throw unauthorized();
        }
        revokeById(stored.id());
        return new Rotation(stored.userId(), create(stored.userId()));
    }

    public void revoke(String token) {
        if (token == null || token.isBlank()) return;
        jdbcClient.sql("UPDATE auth_refresh_tokens SET revoked_at=COALESCE(revoked_at, NOW()) WHERE token_hash=:tokenHash")
                .param("tokenHash", hash(token)).update();
    }

    private void revokeById(long id) {
        jdbcClient.sql("UPDATE auth_refresh_tokens SET revoked_at=NOW() WHERE id=:id")
                .param("id", id).update();
    }

    private String hash(String token) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(token.getBytes(StandardCharsets.UTF_8));
            return java.util.HexFormat.of().formatHex(digest);
        } catch (Exception exception) {
            throw new IllegalStateException("Refresh token hashing failed", exception);
        }
    }

    private ResponseStatusException unauthorized() {
        return new ResponseStatusException(HttpStatus.UNAUTHORIZED, "로그인이 만료되었습니다.");
    }

    private record StoredToken(long id, long userId, LocalDateTime expiresAt, LocalDateTime revokedAt) {
    }
}
