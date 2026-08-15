package com.fimobook.backend;

import java.util.List;
import java.util.Optional;
import java.time.LocalDateTime;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ObjectNode;

@Repository
public class PlayerRepository {

    private static final String SELECT_PLAYERS = """
            SELECT cid, pid,
                   (SELECT JSON_ARRAYAGG(JSON_OBJECT(
                               'id', pc.class_id,
                               'name', pc.class_name,
                               'imageUrl', pc.image_url)
                               ORDER BY pc.class_name)
                      FROM card_classes cc
                      JOIN player_classes pc ON pc.class_id = cc.class_id
                     WHERE cc.cid = players.cid) AS classes_json,
                   player_name_kor, player_name_eng,
                   player_image_url, background_image_url,
                   overall_rating, primary_position, potential_position,
                   team_id, team_name, league_id, league_name,
                   nationality_id, nation_name, height_cm, weight_kg,
                   main_foot, weak_foot_rating, skill_moves_level,
                   skill_moves_name, player_year,
                   is_tradeable, stats_data, prices_data,
                   (SELECT JSON_OBJECTAGG(CONCAT('n8Price', enhancement_level), price)
                      FROM card_prices_current current_price
                     WHERE current_price.cid = players.cid) AS current_prices_json,
                   (SELECT JSON_ARRAYAGG(JSON_OBJECT(
                               'id', t.trait_id,
                               'name', t.trait_name,
                               'iconUrl', t.icon_url)
                               ORDER BY t.trait_id)
                      FROM card_traits ct
                      JOIN traits t ON t.trait_id = ct.trait_id
                     WHERE ct.cid = players.cid) AS traits_json,
                   (SELECT JSON_ARRAYAGG(JSON_OBJECT(
                               'id', ps.play_style_id,
                               'name', COALESCE(ps.play_style_name, ps.play_style_id),
                               'iconUrl', ps.icon_url)
                               ORDER BY cps.slot_order, ps.play_style_id)
                      FROM card_play_styles cps
                      JOIN play_styles ps ON ps.play_style_id = cps.play_style_id
                     WHERE cps.cid = players.cid) AS play_styles_json,
                   traits_data, raw_data
             FROM players
             WHERE LOCATE(:name, player_name_kor) > 0
               AND is_active = TRUE
               AND is_tradeable = TRUE
             ORDER BY overall_rating DESC, cid ASC
             LIMIT 100
            """;

    private static final String SELECT_PLAYER_BY_CID = """
            SELECT cid, pid,
                   (SELECT JSON_ARRAYAGG(JSON_OBJECT(
                               'id', pc.class_id,
                               'name', pc.class_name,
                               'imageUrl', pc.image_url)
                               ORDER BY pc.class_name)
                      FROM card_classes cc
                      JOIN player_classes pc ON pc.class_id = cc.class_id
                     WHERE cc.cid = players.cid) AS classes_json,
                   player_name_kor, player_name_eng,
                   player_image_url, background_image_url,
                   overall_rating, primary_position, potential_position,
                   team_id, team_name, league_id, league_name,
                   nationality_id, nation_name, height_cm, weight_kg,
                   main_foot, weak_foot_rating, skill_moves_level,
                   skill_moves_name, player_year,
                   is_tradeable, stats_data, prices_data,
                   (SELECT JSON_OBJECTAGG(CONCAT('n8Price', enhancement_level), price)
                      FROM card_prices_current current_price
                     WHERE current_price.cid = players.cid) AS current_prices_json,
                   (SELECT JSON_ARRAYAGG(JSON_OBJECT(
                               'id', t.trait_id,
                               'name', t.trait_name,
                               'iconUrl', t.icon_url)
                               ORDER BY t.trait_id)
                      FROM card_traits ct
                      JOIN traits t ON t.trait_id = ct.trait_id
                     WHERE ct.cid = players.cid) AS traits_json,
                   (SELECT JSON_ARRAYAGG(JSON_OBJECT(
                               'id', ps.play_style_id,
                               'name', COALESCE(ps.play_style_name, ps.play_style_id),
                               'iconUrl', ps.icon_url)
                               ORDER BY cps.slot_order, ps.play_style_id)
                      FROM card_play_styles cps
                      JOIN play_styles ps ON ps.play_style_id = cps.play_style_id
                     WHERE cps.cid = players.cid) AS play_styles_json,
                   traits_data, raw_data
              FROM players
             WHERE cid = :cid AND is_active = TRUE
            """;

    private final JdbcClient jdbcClient;
    private final JsonMapper jsonMapper;
    private final AssetUrlResolver assetUrlResolver;
    private final CardVisualThemeService cardVisualThemeService;

    public PlayerRepository(
            JdbcClient jdbcClient,
            JsonMapper jsonMapper,
            AssetUrlResolver assetUrlResolver,
            CardVisualThemeService cardVisualThemeService) {
        this.jdbcClient = jdbcClient;
        this.jsonMapper = jsonMapper;
        this.assetUrlResolver = assetUrlResolver;
        this.cardVisualThemeService = cardVisualThemeService;
    }

    public List<JsonNode> findByPlayerNameContaining(String name) {
        return jdbcClient.sql(SELECT_PLAYERS)
                .param("name", name)
                .query((resultSet, rowNum) -> toApiJson(resultSet))
                .list();
    }

    public PlayerSearchResponse search(PlayerSearchCriteria criteria) {
        StringBuilder where = new StringBuilder(" WHERE p.is_active = TRUE AND p.is_tradeable = :tradeable AND LOCATE(:name, p.player_name_kor) > 0 ");
        if (!criteria.position().isBlank()) {
            where.append(" AND (p.primary_position = :position OR EXISTS (SELECT 1 FROM card_positions pos WHERE pos.cid=p.cid AND pos.position_code=:position)) ");
        }
        if (!criteria.classId().isBlank()) {
            where.append(" AND EXISTS (SELECT 1 FROM card_classes cc WHERE cc.cid=p.cid AND cc.class_id=:classId) ");
        }
        if (criteria.leagueId() != null) {
            where.append(" AND p.league_id = :leagueId ");
        }
        if (criteria.teamId() != null) {
            where.append(" AND p.team_id = :teamId ");
        }
        if (criteria.nationId() != null) {
            where.append(" AND p.nationality_id = :nationId ");
        }
        if (criteria.minOvr() != null) {
            where.append(" AND p.overall_rating >= :minOvr ");
        }
        if (criteria.maxOvr() != null) {
            where.append(" AND p.overall_rating <= :maxOvr ");
        }
        if (criteria.minPrice() != null) {
            where.append(" AND COALESCE(cp.price, 0) >= :minPrice ");
        }
        if (criteria.maxPrice() != null) {
            where.append(" AND COALESCE(cp.price, 0) <= :maxPrice ");
        }
        if (criteria.traitId() != null) {
            where.append(" AND EXISTS (SELECT 1 FROM card_traits ct WHERE ct.cid=p.cid AND ct.trait_id=:traitId) ");
        }
        if (!criteria.playStyleId().isBlank()) {
            where.append(" AND EXISTS (SELECT 1 FROM card_play_styles cps WHERE cps.cid=p.cid AND cps.play_style_id=:playStyleId) ");
        }

        String from = """
                 FROM players p
                 LEFT JOIN card_prices_current cp
                   ON cp.cid=p.cid AND cp.enhancement_level=:priceLevel
                 LEFT JOIN card_prices_current cp0
                   ON cp0.cid=p.cid AND cp0.enhancement_level=0
                """;
        var countQuery = bind(jdbcClient.sql("SELECT COUNT(*)" + from + where), criteria);
        long total = countQuery.query(Long.class).single();

        String orderBy = switch (criteria.sort()) {
            case "ovrAsc" -> "p.overall_rating ASC, p.cid ASC";
            case "priceDesc" -> "COALESCE(cp.price, 0) DESC, p.overall_rating DESC, p.cid ASC";
            case "priceAsc" -> "COALESCE(cp.price, 0) ASC, p.overall_rating DESC, p.cid ASC";
            case "nameAsc" -> "p.player_name_kor ASC, p.overall_rating DESC, p.cid ASC";
            default -> "p.overall_rating DESC, p.cid ASC";
        };

        String select = """
                SELECT p.cid, p.pid,
                       (SELECT JSON_ARRAYAGG(JSON_OBJECT(
                                   'id', pc.class_id,
                                   'name', pc.class_name,
                                   'imageUrl', pc.image_url)
                                   ORDER BY pc.class_name)
                          FROM card_classes cc
                          JOIN player_classes pc ON pc.class_id = cc.class_id
                         WHERE cc.cid = p.cid) AS classes_json,
                       p.player_name_kor, p.player_name_eng,
                       p.overall_rating, p.primary_position,
                       p.team_id, p.team_name, p.league_id, p.league_name,
                       p.nationality_id, p.nation_name,
                       p.player_image_url, p.background_image_url, p.is_tradeable,
                       COALESCE(cp0.price, JSON_VALUE(p.prices_data, '$.n8Price0'), 0) AS base_price,
                       COALESCE(cp.price, 0) AS selected_price
                """;
        var searchQuery = bind(jdbcClient.sql(select + from + where
                + " ORDER BY " + orderBy + " LIMIT :size OFFSET :offset"), criteria)
                .param("size", criteria.size())
                .param("offset", criteria.page() * criteria.size());

        List<PlayerSummary> players = searchQuery
                .query((rs, rowNum) -> {
                    String backgroundImageUrl = rs.getString("background_image_url");
                    return new PlayerSummary(
                            rs.getLong("cid"), rs.getLong("pid"),
                            readClasses(rs.getString("classes_json")),
                            rs.getString("player_name_kor"), rs.getString("player_name_eng"),
                            rs.getInt("overall_rating"), rs.getString("primary_position"),
                            rs.getString("team_name"), rs.getString("league_name"),
                            rs.getString("nation_name"),
                            assetUrlResolver.resolve(rs.getString("player_image_url")),
                            assetUrlResolver.resolve(backgroundImageUrl),
                            cardAssets(rs), cardVisualThemeService.resolve(backgroundImageUrl),
                            rs.getBoolean("is_tradeable"), rs.getLong("base_price"),
                            criteria.priceLevel(), rs.getLong("selected_price"));
                })
                .list();

        int totalPages = total == 0 ? 0 : (int) Math.ceil((double) total / criteria.size());
        return new PlayerSearchResponse(players, criteria.page(), criteria.size(), total, totalPages);
    }

    private JdbcClient.StatementSpec bind(JdbcClient.StatementSpec query, PlayerSearchCriteria criteria) {
        query = query.param("name", criteria.name())
                .param("priceLevel", criteria.priceLevel())
                .param("tradeable", criteria.tradeable() == null || criteria.tradeable());
        if (!criteria.position().isBlank()) query = query.param("position", criteria.position());
        if (!criteria.classId().isBlank()) query = query.param("classId", criteria.classId());
        if (criteria.leagueId() != null) query = query.param("leagueId", criteria.leagueId());
        if (criteria.teamId() != null) query = query.param("teamId", criteria.teamId());
        if (criteria.nationId() != null) query = query.param("nationId", criteria.nationId());
        if (criteria.minOvr() != null) query = query.param("minOvr", criteria.minOvr());
        if (criteria.maxOvr() != null) query = query.param("maxOvr", criteria.maxOvr());
        if (criteria.minPrice() != null) query = query.param("minPrice", criteria.minPrice());
        if (criteria.maxPrice() != null) query = query.param("maxPrice", criteria.maxPrice());
        if (criteria.traitId() != null) query = query.param("traitId", criteria.traitId());
        if (!criteria.playStyleId().isBlank()) query = query.param("playStyleId", criteria.playStyleId());
        return query;
    }

    public Optional<JsonNode> findByCid(long cid) {
        return jdbcClient.sql(SELECT_PLAYER_BY_CID)
                .param("cid", cid)
                .query((resultSet, rowNum) -> toApiJson(resultSet))
                .optional();
    }

    public Optional<PriceRefreshCandidate> findRefreshCandidate(long cid) {
        return jdbcClient.sql("""
                SELECT cid, pid, is_tradeable, price_checked_at
                  FROM players
                 WHERE cid = :cid AND is_active = TRUE
                """)
                .param("cid", cid)
                .query((resultSet, rowNum) -> {
                    var checked = resultSet.getTimestamp("price_checked_at");
                    LocalDateTime checkedAt = checked == null ? null : checked.toLocalDateTime();
                    return new PriceRefreshCandidate(
                            resultSet.getLong("cid"),
                            resultSet.getLong("pid"),
                            resultSet.getBoolean("is_tradeable"),
                            checkedAt);
                })
                .optional();
    }

    private JsonNode toApiJson(java.sql.ResultSet resultSet) throws java.sql.SQLException {
        try {
            ObjectNode player = (ObjectNode) jsonMapper.readTree(resultSet.getString("raw_data"));

            player.put("cid", resultSet.getLong("cid"));
            player.put("pid", resultSet.getLong("pid"));
            player.set("classes", readClasses(resultSet.getString("classes_json")));
            put(player, "playerKor", resultSet.getString("player_name_kor"));
            put(player, "playerEng", resultSet.getString("player_name_eng"));
            String backgroundImageUrl = resultSet.getString("background_image_url");
            put(player, "pimage", assetUrlResolver.resolve(resultSet.getString("player_image_url")));
            put(player, "bimage", assetUrlResolver.resolve(backgroundImageUrl));
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
            player.put("noTrade", resultSet.getBoolean("is_tradeable") ? 0 : 1);

            ObjectNode assets = jsonMapper.createObjectNode();
            putAsset(assets, "flag", assetUrl(resultSet, "nationality_id", "/flags/flags_64x64/F_", ".png"));
            putAsset(assets, "team", assetUrl(resultSet, "team_id", "/team_logos/team_logos_64x64/L", ".png"));
            putAsset(assets, "league", assetUrl(resultSet, "league_id", "/league_logos/league_logos_256x256/L", ".png"));
            player.set("assets", assets);
            player.set("cardTheme", jsonMapper.valueToTree(cardVisualThemeService.resolve(backgroundImageUrl)));

            merge(player, resultSet.getString("stats_data"));
            merge(player, resultSet.getString("prices_data"));
            merge(player, resultSet.getString("current_prices_json"));
            JsonNode traits = readAssetArray(resultSet.getString("traits_json"));
            player.set("Trait", traits.isEmpty()
                    ? jsonMapper.readTree(resultSet.getString("traits_data"))
                    : traits);
            player.set("playStyles", readAssetArray(resultSet.getString("play_styles_json")));

            return player;
        } catch (Exception exception) {
            throw new java.sql.SQLException("Failed to map player JSON response", exception);
        }
    }

    private void merge(ObjectNode target, String json) throws Exception {
        if (json == null) {
            return;
        }
        JsonNode source = jsonMapper.readTree(json);
        source.forEachEntry(target::set);
    }

    private JsonNode readJsonArray(String json) throws java.sql.SQLException {
        try {
            return json == null ? jsonMapper.createArrayNode() : jsonMapper.readTree(json);
        } catch (Exception exception) {
            throw new java.sql.SQLException("Failed to parse class memberships", exception);
        }
    }

    private JsonNode readClasses(String json) throws java.sql.SQLException {
        JsonNode classes = readJsonArray(json);
        for (JsonNode playerClass : classes) {
            if (playerClass instanceof ObjectNode objectClass) {
                String imageUrl = objectClass.path("imageUrl").asString(null);
                put(objectClass, "imageUrl", assetUrlResolver.resolve(imageUrl));
            }
        }
        return classes;
    }

    private JsonNode readAssetArray(String json) throws java.sql.SQLException {
        JsonNode assets = readJsonArray(json);
        for (JsonNode asset : assets) {
            if (asset instanceof ObjectNode objectAsset) {
                String imageUrl = objectAsset.path("iconUrl").asString(null);
                put(objectAsset, "iconUrl", assetUrlResolver.resolve(imageUrl));
            }
        }
        return assets;
    }

    private PlayerCardAssets cardAssets(java.sql.ResultSet resultSet) throws java.sql.SQLException {
        return new PlayerCardAssets(
                assetUrl(resultSet, "nationality_id", "/flags/flags_64x64/F_", ".png"),
                assetUrl(resultSet, "team_id", "/team_logos/team_logos_64x64/L", ".png"),
                assetUrl(resultSet, "league_id", "/league_logos/league_logos_256x256/L", ".png"));
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

    private String assetUrl(
            java.sql.ResultSet resultSet, String column, String prefix, String suffix)
            throws java.sql.SQLException {
        long id = resultSet.getLong(column);
        String sourceUrl = resultSet.wasNull()
                ? null
                : "https://fco.vod.nexoncdn.co.kr/jade_assets" + prefix + id + suffix;
        return assetUrlResolver.resolve(sourceUrl);
    }

    private void putAsset(ObjectNode target, String key, String value) {
        if (value == null) {
            target.putNull(key);
        } else {
            target.put(key, value);
        }
    }
}
