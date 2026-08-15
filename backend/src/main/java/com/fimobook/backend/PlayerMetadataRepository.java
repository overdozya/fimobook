package com.fimobook.backend;

import java.util.List;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

@Repository
public class PlayerMetadataRepository {

    private final JdbcClient jdbcClient;
    private final AssetUrlResolver assetUrlResolver;

    public PlayerMetadataRepository(JdbcClient jdbcClient, AssetUrlResolver assetUrlResolver) {
        this.jdbcClient = jdbcClient;
        this.assetUrlResolver = assetUrlResolver;
    }

    public PlayerFilterMetadata filters() {
        return new PlayerFilterMetadata(
                options("""
                        SELECT pc.class_id AS option_id, pc.class_name AS option_name, pc.image_url
                          FROM player_classes pc
                         WHERE EXISTS (
                               SELECT 1
                                 FROM card_classes cc
                                 JOIN players p ON p.cid = cc.cid
                                WHERE cc.class_id = pc.class_id
                                  AND p.is_active = TRUE
                                  AND p.is_tradeable = TRUE)
                         ORDER BY pc.class_name
                        """),
                options("""
                        SELECT n.nation_id AS option_id, n.name_kor AS option_name, n.flag_url AS image_url
                          FROM nations n
                         WHERE EXISTS (
                               SELECT 1 FROM players p
                                WHERE p.nationality_id = n.nation_id
                                  AND p.is_active = TRUE
                                  AND p.is_tradeable = TRUE)
                         ORDER BY n.name_kor
                        """),
                options("""
                        SELECT l.league_id AS option_id, l.name_kor AS option_name, l.logo_url AS image_url
                          FROM leagues l
                         WHERE EXISTS (
                               SELECT 1 FROM players p
                                WHERE p.league_id = l.league_id
                                  AND p.is_active = TRUE
                                  AND p.is_tradeable = TRUE)
                         ORDER BY l.name_kor
                        """),
                options("""
                        SELECT t.trait_id AS option_id, t.trait_name AS option_name, t.icon_url AS image_url
                          FROM traits t
                         WHERE EXISTS (
                               SELECT 1
                                 FROM card_traits ct
                                 JOIN players p ON p.cid = ct.cid
                                WHERE ct.trait_id = t.trait_id
                                  AND p.is_active = TRUE
                                  AND p.is_tradeable = TRUE)
                         ORDER BY t.trait_name
                        """),
                options("""
                        SELECT ps.play_style_id AS option_id,
                               COALESCE(ps.play_style_name, ps.play_style_id) AS option_name,
                               ps.icon_url AS image_url
                          FROM play_styles ps
                         WHERE EXISTS (
                               SELECT 1
                                 FROM card_play_styles cps
                                 JOIN players p ON p.cid = cps.cid
                                WHERE cps.play_style_id = ps.play_style_id
                                  AND p.is_active = TRUE
                                  AND p.is_tradeable = TRUE)
                         ORDER BY option_name
                        """));
    }

    public List<FilterOption> teams(Long leagueId, String name, int limit) {
        String leagueWhere = leagueId == null ? "" : " AND p.league_id = :leagueId ";
        var query = jdbcClient.sql("""
                SELECT DISTINCT CAST(p.team_id AS CHAR) AS option_id,
                       p.team_name AS option_name,
                       t.logo_url AS image_url
                 FROM players p
                  LEFT JOIN teams t ON t.team_id = p.team_id
                 WHERE p.is_active = TRUE
                   AND p.is_tradeable = TRUE
                   AND p.team_id IS NOT NULL
                   AND LOCATE(:name, p.team_name) > 0
                """ + leagueWhere + " ORDER BY p.team_name LIMIT :limit")
                .param("name", name)
                .param("limit", limit);
        if (leagueId != null) {
            query = query.param("leagueId", leagueId);
        }
        return query.query((resultSet, rowNum) -> new FilterOption(
                resultSet.getString("option_id"),
                resultSet.getString("option_name"),
                assetUrlResolver.resolve(resultSet.getString("image_url")))).list();
    }

    private List<FilterOption> options(String sql) {
        return jdbcClient.sql(sql).query((resultSet, rowNum) -> new FilterOption(
                resultSet.getString("option_id"),
                resultSet.getString("option_name"),
                assetUrlResolver.resolve(resultSet.getString("image_url")))).list();
    }
}
