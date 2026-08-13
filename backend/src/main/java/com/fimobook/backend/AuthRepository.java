package com.fimobook.backend;

import java.util.Optional;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

@Repository
public class AuthRepository {

    public record User(long id, String email, String passwordHash, String displayName) {
    }

    private final JdbcClient jdbcClient;

    public AuthRepository(JdbcClient jdbcClient) {
        this.jdbcClient = jdbcClient;
    }

    public Optional<User> findByEmail(String email) {
        return jdbcClient.sql("SELECT id, email, password_hash, display_name FROM users WHERE email = :email")
                .param("email", email)
                .query((rs, rowNum) -> new User(rs.getLong("id"), rs.getString("email"),
                        rs.getString("password_hash"), rs.getString("display_name")))
                .optional();
    }

    public User create(String email, String passwordHash, String displayName) {
        jdbcClient.sql("INSERT INTO users (email, password_hash, display_name) VALUES (:email, :password, :name)")
                .param("email", email)
                .param("password", passwordHash)
                .param("name", displayName)
                .update();
        return findByEmail(email).orElseThrow();
    }

    public Optional<User> findById(long id) {
        return jdbcClient.sql("SELECT id, email, password_hash, display_name FROM users WHERE id = :id")
                .param("id", id)
                .query((rs, rowNum) -> new User(rs.getLong("id"), rs.getString("email"),
                        rs.getString("password_hash"), rs.getString("display_name")))
                .optional();
    }
}
