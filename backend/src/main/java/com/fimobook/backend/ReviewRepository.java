package com.fimobook.backend;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

@Repository
public class ReviewRepository {

    public record Review(long id, long userId, String authorName, long cid, int rating,
            String content, int likes, int dislikes, LocalDateTime createdAt, LocalDateTime updatedAt) {
    }

    private static final String SELECT = """
            SELECT r.id, r.user_id, u.display_name, r.cid, r.rating, r.content,
                   r.likes, r.dislikes, r.created_at, r.updated_at
              FROM reviews r
              JOIN users u ON u.id = r.user_id
            """;

    private final JdbcClient jdbcClient;

    public ReviewRepository(JdbcClient jdbcClient) {
        this.jdbcClient = jdbcClient;
    }

    public List<Review> findByCid(long cid) {
        return jdbcClient.sql(SELECT + " WHERE r.cid = :cid ORDER BY r.created_at DESC")
                .param("cid", cid).query(this::map).list();
    }

    public Optional<Review> findById(long id) {
        return jdbcClient.sql(SELECT + " WHERE r.id = :id")
                .param("id", id).query(this::map).optional();
    }

    @Transactional
    public Review create(long userId, long cid, int rating, String content) {
        jdbcClient.sql("""
                INSERT INTO reviews (user_id, cid, rating, content)
                VALUES (:userId, :cid, :rating, :content)
                """).param("userId", userId).param("cid", cid)
                .param("rating", rating).param("content", content).update();
        long id = jdbcClient.sql("SELECT LAST_INSERT_ID()").query(Long.class).single();
        return findById(id).orElseThrow();
    }

    public Review update(long id, int rating, String content) {
        jdbcClient.sql("UPDATE reviews SET rating=:rating, content=:content WHERE id=:id")
                .param("rating", rating).param("content", content).param("id", id).update();
        return findById(id).orElseThrow();
    }

    public void delete(long id) {
        jdbcClient.sql("DELETE FROM reviews WHERE id=:id").param("id", id).update();
    }

    @Transactional
    public Review react(long id, long userId, String reaction) {
        jdbcClient.sql("SELECT id FROM reviews WHERE id=:id FOR UPDATE")
                .param("id", id).query(Long.class).single();
        String previous = jdbcClient.sql("""
                SELECT reaction FROM review_reactions
                 WHERE review_id=:reviewId AND user_id=:userId
                """).param("reviewId", id).param("userId", userId)
                .query(String.class).optional().orElse(null);

        if (reaction.equals(previous)) {
            jdbcClient.sql("DELETE FROM review_reactions WHERE review_id=:reviewId AND user_id=:userId")
                    .param("reviewId", id).param("userId", userId).update();
            adjustCount(id, reaction, -1);
        } else if (previous == null) {
            jdbcClient.sql("""
                    INSERT INTO review_reactions (review_id, user_id, reaction)
                    VALUES (:reviewId, :userId, :reaction)
                    """).param("reviewId", id).param("userId", userId).param("reaction", reaction).update();
            adjustCount(id, reaction, 1);
        } else {
            jdbcClient.sql("""
                    UPDATE review_reactions SET reaction=:reaction
                     WHERE review_id=:reviewId AND user_id=:userId
                    """).param("reaction", reaction).param("reviewId", id).param("userId", userId).update();
            adjustCount(id, previous, -1);
            adjustCount(id, reaction, 1);
        }
        return findById(id).orElseThrow();
    }

    private void adjustCount(long id, String reaction, int delta) {
        String column = "like".equals(reaction) ? "likes" : "dislikes";
        jdbcClient.sql("UPDATE reviews SET " + column + " = GREATEST(" + column + " + :delta, 0) WHERE id=:id")
                .param("delta", delta).param("id", id).update();
    }

    private Review map(java.sql.ResultSet rs, int rowNum) throws java.sql.SQLException {
        return new Review(rs.getLong("id"), rs.getLong("user_id"), rs.getString("display_name"),
                rs.getLong("cid"), rs.getInt("rating"), rs.getString("content"),
                rs.getInt("likes"), rs.getInt("dislikes"),
                rs.getTimestamp("created_at").toLocalDateTime(),
                rs.getTimestamp("updated_at").toLocalDateTime());
    }
}
