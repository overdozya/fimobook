package com.fimobook.backend;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ObjectNode;

@Component
@ConditionalOnProperty(name = "fimo.players.import-enabled", havingValue = "true")
public class PlayerDataImporter implements ApplicationRunner {

    private static final List<String> STAT_KEYS = List.of(
            "ACC", "SPD", "FIN", "SHO", "LSA", "VOL", "PEN",
            "SPA", "LPA", "VIS", "CRO", "CUR", "FRK",
            "DRI", "BAC", "AGI", "REA", "BAL",
            "MRK", "STT", "SLT", "AWR", "HEA",
            "STR", "AGG", "JMP", "STA");

    private static final String UPSERT_SQL = """
            INSERT INTO players (
                cid, pid, player_name_kor, player_name_eng,
                player_image_url, background_image_url,
                overall_rating, primary_position, potential_position,
                team_id, team_name, league_id, league_name,
                nationality_id, nation_name, height_cm, weight_kg,
                main_foot, weak_foot_rating, skill_moves_level,
                skill_moves_name, player_year,
                stats_data, prices_data, traits_data, raw_data
            ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
            ON DUPLICATE KEY UPDATE
                pid = VALUES(pid),
                player_name_kor = VALUES(player_name_kor),
                player_name_eng = VALUES(player_name_eng),
                player_image_url = VALUES(player_image_url),
                background_image_url = VALUES(background_image_url),
                overall_rating = VALUES(overall_rating),
                primary_position = VALUES(primary_position),
                potential_position = VALUES(potential_position),
                team_id = VALUES(team_id),
                team_name = VALUES(team_name),
                league_id = VALUES(league_id),
                league_name = VALUES(league_name),
                nationality_id = VALUES(nationality_id),
                nation_name = VALUES(nation_name),
                height_cm = VALUES(height_cm),
                weight_kg = VALUES(weight_kg),
                main_foot = VALUES(main_foot),
                weak_foot_rating = VALUES(weak_foot_rating),
                skill_moves_level = VALUES(skill_moves_level),
                skill_moves_name = VALUES(skill_moves_name),
                player_year = VALUES(player_year),
                stats_data = VALUES(stats_data),
                prices_data = VALUES(prices_data),
                traits_data = VALUES(traits_data),
                raw_data = VALUES(raw_data)
            """;

    private final JdbcTemplate jdbcTemplate;
    private final JsonMapper jsonMapper;

    public PlayerDataImporter(JdbcTemplate jdbcTemplate, JsonMapper jsonMapper) {
        this.jdbcTemplate = jdbcTemplate;
        this.jsonMapper = jsonMapper;
    }

    @Override
    public void run(ApplicationArguments args) throws IOException {
        var resource = new ClassPathResource("data/players.json");
        JsonNode players = jsonMapper.readTree(resource.getInputStream());
        List<Object[]> rows = new ArrayList<>();

        for (JsonNode player : players) {
            rows.add(toRow(player));
        }

        jdbcTemplate.batchUpdate(UPSERT_SQL, rows);
        Integer storedCount = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM players", Integer.class);

        System.out.printf("Player import complete: source=%d, stored=%d%n", players.size(), storedCount);
    }

    private Object[] toRow(JsonNode player) {
        ObjectNode stats = jsonMapper.createObjectNode();
        for (String key : STAT_KEYS) {
            if (player.has(key)) {
                stats.set(key, player.get(key));
            }
        }

        ObjectNode prices = jsonMapper.createObjectNode();
        for (int level = 0; level <= 15; level++) {
            String key = "n8Price" + level;
            if (player.has(key)) {
                prices.set(key, player.get(key));
            }
        }

        return new Object[] {
                player.path("cid").longValue(),
                player.path("pid").longValue(),
                text(player, "playerKor"),
                text(player, "playerEng"),
                text(player, "pimage"),
                text(player, "bimage"),
                player.path("ovr").intValue(),
                text(player, "position"),
                text(player, "potentialPosition"),
                number(player, "teamid"),
                text(player, "team"),
                number(player, "leagueid"),
                text(player, "league"),
                number(player, "nationality"),
                text(player, "nation"),
                number(player, "height"),
                number(player, "weight"),
                number(player, "mainFoot"),
                number(player, "WFA"),
                number(player, "skillMovesLevel"),
                text(player, "skillMovesName"),
                number(player, "PlayerYear"),
                stats.toString(),
                prices.toString(),
                player.path("Trait").toString(),
                player.toString()
        };
    }

    private String text(JsonNode player, String key) {
        JsonNode value = player.get(key);
        return value == null || value.isNull() ? null : value.asString();
    }

    private Number number(JsonNode player, String key) {
        JsonNode value = player.get(key);
        return value == null || value.isNull() ? null : value.numberValue();
    }
}
