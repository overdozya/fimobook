package com.fimobook.backend;

import java.util.List;
import java.util.Optional;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ObjectNode;

@Repository
public class PlayerRepository {

    private static final String SELECT_PLAYERS = """
            SELECT cid, pid, player_name_kor, player_name_eng,
                   player_image_url, background_image_url,
                   overall_rating, primary_position, potential_position,
                   team_id, team_name, league_id, league_name,
                   nationality_id, nation_name, height_cm, weight_kg,
                   main_foot, weak_foot_rating, skill_moves_level,
                   skill_moves_name, player_year,
                   stats_data, prices_data, traits_data, raw_data
              FROM players
             WHERE LOCATE(:name, player_name_kor) > 0
             ORDER BY overall_rating DESC, cid ASC
            """;

    private final JdbcClient jdbcClient;
    private final JsonMapper jsonMapper;

    public PlayerRepository(JdbcClient jdbcClient, JsonMapper jsonMapper) {
        this.jdbcClient = jdbcClient;
        this.jsonMapper = jsonMapper;
    }

    public List<JsonNode> findByPlayerNameContaining(String name) {
        return jdbcClient.sql(SELECT_PLAYERS)
                .param("name", name)
                .query((resultSet, rowNum) -> toApiJson(resultSet))
                .list();
    }

    public PlayerSearchResponse search(String name, String position, int page, int size) {
        String positionFilter = position == null || position.isBlank() ? "" : position;
        String where = " LOCATE(:name, player_name_kor) > 0 "
                + "AND (:position = '' OR primary_position = :position) ";

        long total = jdbcClient.sql("SELECT COUNT(*) FROM players WHERE " + where)
                .param("name", name)
                .param("position", positionFilter)
                .query(Long.class)
                .single();

        List<PlayerSummary> players = jdbcClient.sql("""
                SELECT cid, pid, player_name_kor, player_name_eng,
                       overall_rating, primary_position, team_name, league_name,
                       nation_name, player_image_url, background_image_url,
                       COALESCE(JSON_VALUE(prices_data, '$.n8Price0'), 0) AS base_price
                  FROM players
                 WHERE """ + where + """
                 ORDER BY overall_rating DESC, cid ASC
                 LIMIT :size OFFSET :offset
                """)
                .param("name", name)
                .param("position", positionFilter)
                .param("size", size)
                .param("offset", page * size)
                .query((rs, rowNum) -> new PlayerSummary(
                        rs.getLong("cid"), rs.getLong("pid"),
                        rs.getString("player_name_kor"), rs.getString("player_name_eng"),
                        rs.getInt("overall_rating"), rs.getString("primary_position"),
                        rs.getString("team_name"), rs.getString("league_name"),
                        rs.getString("nation_name"), rs.getString("player_image_url"),
                        rs.getString("background_image_url"), rs.getLong("base_price")))
                .list();

        int totalPages = total == 0 ? 0 : (int) Math.ceil((double) total / size);
        return new PlayerSearchResponse(players, page, size, total, totalPages);
    }

    public Optional<JsonNode> findByCid(long cid) {
        return jdbcClient.sql(SELECT_PLAYERS.replace(
                        "WHERE LOCATE(:name, player_name_kor) > 0",
                        "WHERE cid = :cid"))
                .param("cid", cid)
                .query((resultSet, rowNum) -> toApiJson(resultSet))
                .optional();
    }

    private JsonNode toApiJson(java.sql.ResultSet resultSet) throws java.sql.SQLException {
        try {
            ObjectNode player = (ObjectNode) jsonMapper.readTree(resultSet.getString("raw_data"));

            player.put("cid", resultSet.getLong("cid"));
            player.put("pid", resultSet.getLong("pid"));
            put(player, "playerKor", resultSet.getString("player_name_kor"));
            put(player, "playerEng", resultSet.getString("player_name_eng"));
            put(player, "pimage", resultSet.getString("player_image_url"));
            put(player, "bimage", resultSet.getString("background_image_url"));
            player.put("ovr", resultSet.getInt("overall_rating"));
            put(player, "position", resultSet.getString("primary_position"));
            put(player, "potentialPosition", resultSet.getString("potential_position"));
            putNumber(player, "teamid", resultSet, "team_id");
            put(player, "team", resultSet.getString("team_name"));
            putNumber(player, "leagueid", resultSet, "league_id");
            put(player, "league", resultSet.getString("league_name"));
            putNumber(player, "nationality", resultSet, "nationality_id");
            put(player, "nation", resultSet.getString("nation_name"));
            putNumber(player, "height", resultSet, "height_cm");
            putNumber(player, "weight", resultSet, "weight_kg");
            putNumber(player, "mainFoot", resultSet, "main_foot");
            putNumber(player, "WFA", resultSet, "weak_foot_rating");
            putNumber(player, "skillMovesLevel", resultSet, "skill_moves_level");
            put(player, "skillMovesName", resultSet.getString("skill_moves_name"));
            putNumber(player, "PlayerYear", resultSet, "player_year");

            merge(player, resultSet.getString("stats_data"));
            merge(player, resultSet.getString("prices_data"));
            player.set("Trait", jsonMapper.readTree(resultSet.getString("traits_data")));

            return player;
        } catch (Exception exception) {
            throw new java.sql.SQLException("Failed to map player JSON response", exception);
        }
    }

    private void merge(ObjectNode target, String json) throws Exception {
        JsonNode source = jsonMapper.readTree(json);
        source.forEachEntry(target::set);
    }

    private void put(ObjectNode target, String key, String value) {
        if (value == null) {
            target.putNull(key);
        } else {
            target.put(key, value);
        }
    }

    private void putNumber(ObjectNode target, String key, java.sql.ResultSet resultSet, String column)
            throws java.sql.SQLException {
        long value = resultSet.getLong(column);
        if (resultSet.wasNull()) {
            target.putNull(key);
        } else {
            target.put(key, value);
        }
    }
}
