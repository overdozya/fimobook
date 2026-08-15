package com.fimobook.backend;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;

@SpringBootTest(properties = "fimo.price-refresh.enabled=false")
@EnabledIfEnvironmentVariable(named = "FIMO_DB_TEST", matches = "true")
class PlayerRepositoryTests {

    @Autowired
    private PlayerRepository playerRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    void findsCruijffCardsWithVueCompatibleFields() {
        var players = playerRepository.findByPlayerNameContaining("크루이프");

        assertThat(players).isNotEmpty();
        assertThat(players).allSatisfy(player -> {
            assertThat(player.path("playerKor").asString()).contains("크루이프");
            assertThat(player.path("cid").asLong()).isPositive();
            assertThat(player.path("pid").asLong()).isEqualTo(242522);
            assertThat(player.path("ovr").asInt()).isPositive();
            assertThat(player.path("position").asString()).isNotBlank();
            assertThat(player.path("pimage").asString()).isNotBlank();
            assertThat(player.path("bimage").asString()).isNotBlank();
            assertThat(player.path("pimage").asString()).startsWith("/api/assets/");
            assertThat(player.path("bimage").asString()).startsWith("/api/assets/");
            assertThat(player.path("assets").path("flag").asString()).startsWith("/api/assets/");
            assertThat(player.path("cardTheme").path("name").asString()).matches("#[0-9A-Fa-f]{6}");
            assertThat(player.path("noTrade").asInt()).isZero();
            assertThat(player.has("ACC")).isTrue();
            assertThat(player.has("n8Price0")).isTrue();
            assertThat(player.path("Trait").isArray()).isTrue();
        });
    }

    @Test
    void findsCardDetailByCid() {
        var player = playerRepository.findByCid(22901979L);
        long currentPrice = jdbcTemplate.queryForObject("""
                SELECT price
                  FROM card_prices_current
                 WHERE cid = 22901979 AND enhancement_level = 0
                """, Long.class);

        assertThat(player).isPresent();
        assertThat(player.orElseThrow().path("playerKor").asString()).isEqualTo("요한 크루이프");
        assertThat(player.orElseThrow().path("n8Price0").asLong()).isEqualTo(currentPrice);
        assertThat(player.orElseThrow().path("classes").isArray()).isTrue();
        assertThat(player.orElseThrow().path("classes").get(0).path("imageUrl").asString())
                .startsWith("/api/assets/");
        assertThat(player.orElseThrow().path("Trait").get(0).path("iconUrl").asString())
                .startsWith("/api/assets/");
        assertThat(player.orElseThrow().path("playStyles").get(0).path("iconUrl").asString())
                .startsWith("/api/assets/");
    }

    @Test
    void filtersCardsThroughManyToManyClassMembership() {
        var criteria = new PlayerSearchCriteria(
                "조 콜", "", "PROGRAM_ICONS",
                null, null, null, true, null, null,
                0, null, null, null, "", "ovrDesc", 0, 20);

        var result = playerRepository.search(criteria);

        assertThat(result.totalElements()).isPositive();
        assertThat(result.players()).allSatisfy(player ->
                assertThat(player.classes()).anySatisfy(playerClass ->
                        assertThat(playerClass.path("id").asString()).isEqualTo("PROGRAM_ICONS")));
    }

    @Test
    void defaultsPaginatedSearchToTradeableCards() {
        var criteria = new PlayerSearchCriteria(
                "", "", "",
                null, null, null, null, null, null,
                0, null, null, null, "", "ovrDesc", 0, 50);

        var result = playerRepository.search(criteria);
        long tradeableCards = jdbcTemplate.queryForObject("""
                SELECT COUNT(*)
                  FROM players
                 WHERE is_active = TRUE AND is_tradeable = TRUE
                """, Long.class);

        assertThat(result.totalElements()).isEqualTo(tradeableCards);
        assertThat(result.players()).hasSize(50).allMatch(PlayerSummary::tradeable);
        assertThat(result.players()).allSatisfy(player -> {
            assertThat(player.assets()).isNotNull();
            assertThat(player.cardTheme().name()).matches("#[0-9A-Fa-f]{6}");
        });
    }

    @Test
    void explicitUntradeableSearchRemainsAvailableForDiagnostics() {
        var criteria = new PlayerSearchCriteria(
                "", "", "",
                null, null, null, false, null, null,
                0, null, null, null, "", "ovrDesc", 0, 20);

        var result = playerRepository.search(criteria);

        assertThat(result.totalElements()).isPositive();
        assertThat(result.players()).isNotEmpty().allMatch(player -> !player.tradeable());
    }
}
