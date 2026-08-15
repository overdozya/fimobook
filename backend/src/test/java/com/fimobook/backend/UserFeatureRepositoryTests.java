package com.fimobook.backend;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;

@SpringBootTest(properties = "fimo.price-refresh.enabled=false")
@EnabledIfEnvironmentVariable(named = "FIMO_DB_TEST", matches = "true")
class UserFeatureRepositoryTests {

    private static final long TEST_CID = 22901979L;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private ReviewRepository reviewRepository;

    @Autowired
    private SquadRepository squadRepository;

    @Test
    void enforcesOneReviewAndOneReactionPerUserAndPersistsSquad() {
        String email = "repository-test-" + System.nanoTime() + "@fimobook.local";
        jdbcTemplate.update("""
                INSERT INTO users (email, password_hash, display_name)
                VALUES (?, 'unused-test-hash', '저장소검증')
                """, email);
        long userId = jdbcTemplate.queryForObject("SELECT id FROM users WHERE email=?", Long.class, email);

        try {
            var review = reviewRepository.create(userId, TEST_CID, 5, "자동 테스트");
            assertThatThrownBy(() -> reviewRepository.create(userId, TEST_CID, 4, "중복"))
                    .isInstanceOf(DuplicateKeyException.class);
            assertThat(reviewRepository.react(review.id(), userId, "like").likes()).isEqualTo(1);
            assertThat(reviewRepository.react(review.id(), userId, "like").likes()).isZero();

            var squad = squadRepository.saveDefault(userId,
                    List.of(new SquadController.SquadSlotRequest("st", TEST_CID)));
            assertThat(squad).singleElement().satisfies(player -> {
                assertThat(player.cid()).isEqualTo(TEST_CID);
                assertThat(player.slotId()).isEqualTo("st");
                assertThat(player.bimage()).startsWith("/api/assets/");
                assertThat(player.assets().flag()).startsWith("/api/assets/");
                assertThat(player.cardTheme().name()).matches("#[0-9A-Fa-f]{6}");
            });
        } finally {
            jdbcTemplate.update("DELETE FROM users WHERE id=?", userId);
        }
    }
}
