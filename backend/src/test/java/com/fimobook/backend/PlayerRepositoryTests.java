package com.fimobook.backend;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest
@EnabledIfEnvironmentVariable(named = "FIMO_DB_TEST", matches = "true")
class PlayerRepositoryTests {

    @Autowired
    private PlayerRepository playerRepository;

    @Test
    void findsCruijffCardsWithVueCompatibleFields() {
        var players = playerRepository.findByPlayerNameContaining("크루이프");

        assertThat(players).hasSize(10);
        assertThat(players).allSatisfy(player -> {
            assertThat(player.path("playerKor").asString()).contains("크루이프");
            assertThat(player.path("cid").asLong()).isPositive();
            assertThat(player.path("pid").asLong()).isEqualTo(242522);
            assertThat(player.path("ovr").asInt()).isPositive();
            assertThat(player.path("position").asString()).isNotBlank();
            assertThat(player.path("pimage").asString()).isNotBlank();
            assertThat(player.path("bimage").asString()).isNotBlank();
            assertThat(player.has("ACC")).isTrue();
            assertThat(player.has("n8Price0")).isTrue();
            assertThat(player.path("Trait").isArray()).isTrue();
        });
    }
}
