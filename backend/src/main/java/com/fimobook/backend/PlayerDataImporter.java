package com.fimobook.backend;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Date;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.ObjectNode;

@Component
@ConditionalOnProperty(name = "fimo.players.import-enabled", havingValue = "true")
public class PlayerDataImporter implements ApplicationRunner {

    private static final int BATCH_SIZE = 500;
    private static final DateTimeFormatter BIRTH_DATE = DateTimeFormatter.ofPattern("yyyy/M/d");
    private static final String CDN = "https://fco.vod.nexoncdn.co.kr/jade_assets";

    private static final List<String> STAT_KEYS = List.of(
            "ACC", "SPD", "FIN", "SHO", "LSA", "VOL", "PEN",
            "SPA", "LPA", "VIS", "CRO", "CUR", "FRK",
            "DRI", "BAC", "AGI", "REA", "BAL",
            "MRK", "STT", "SLT", "AWR", "HEA",
            "STR", "AGG", "JMP", "STA",
            "GKD", "GKK", "GKP", "HAN", "REF", "POS", "WFA", "LVO", "LVOD");

    private static final Map<Long, String> TRAIT_ASSET_KEYS = traitAssetKeys();

    private static final String PROFILE_UPSERT = """
            INSERT INTO player_profiles (
                pid, player_name_kor, player_name_eng, nationality_id, nation_name,
                birth_date, height_cm, weight_kg, main_foot
            ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)
            ON DUPLICATE KEY UPDATE
                player_name_kor = VALUES(player_name_kor),
                player_name_eng = VALUES(player_name_eng),
                nationality_id = VALUES(nationality_id),
                nation_name = VALUES(nation_name),
                birth_date = VALUES(birth_date),
                height_cm = VALUES(height_cm),
                weight_kg = VALUES(weight_kg),
                main_foot = VALUES(main_foot)
            """;

    private static final String CLASS_UPSERT = """
            INSERT INTO player_classes (class_id, class_name, image_url)
            VALUES (?, ?, ?)
            ON DUPLICATE KEY UPDATE class_name = VALUES(class_name), image_url = VALUES(image_url)
            """;

    private static final String NATION_UPSERT = """
            INSERT INTO nations (nation_id, name_kor, flag_url)
            VALUES (?, ?, ?)
            ON DUPLICATE KEY UPDATE name_kor = VALUES(name_kor), flag_url = VALUES(flag_url)
            """;

    private static final String LEAGUE_UPSERT = """
            INSERT INTO leagues (league_id, name_kor, logo_url)
            VALUES (?, ?, ?)
            ON DUPLICATE KEY UPDATE name_kor = VALUES(name_kor), logo_url = VALUES(logo_url)
            """;

    private static final String TEAM_UPSERT = """
            INSERT INTO teams (team_id, team_name, logo_url)
            VALUES (?, ?, ?)
            ON DUPLICATE KEY UPDATE team_name = VALUES(team_name), logo_url = VALUES(logo_url)
            """;

    private static final String PLAYER_UPSERT = """
            INSERT INTO players (
                cid, pid, player_name_kor, player_name_eng, player_image_url, background_image_url,
                overall_rating, primary_position, potential_position,
                team_id, team_name, league_id, league_name,
                nationality_id, nation_name, height_cm, weight_kg,
                main_foot, weak_foot_rating, skill_moves_level, skill_moves_name, player_year,
                is_tradeable, is_active, stats_data, prices_data, traits_data,
                positions_data, play_styles_data, skills_data, raw_data,
                price_checked_at, source_seen_at
            ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, TRUE, ?, ?, ?, ?, ?, ?, ?, ?, ?)
            ON DUPLICATE KEY UPDATE
                pid = VALUES(pid), player_name_kor = VALUES(player_name_kor),
                player_name_eng = VALUES(player_name_eng), player_image_url = VALUES(player_image_url),
                background_image_url = VALUES(background_image_url), overall_rating = VALUES(overall_rating),
                primary_position = VALUES(primary_position), potential_position = VALUES(potential_position),
                team_id = VALUES(team_id), team_name = VALUES(team_name), league_id = VALUES(league_id),
                league_name = VALUES(league_name), nationality_id = VALUES(nationality_id),
                nation_name = VALUES(nation_name), height_cm = VALUES(height_cm), weight_kg = VALUES(weight_kg),
                main_foot = VALUES(main_foot), weak_foot_rating = VALUES(weak_foot_rating),
                skill_moves_level = VALUES(skill_moves_level), skill_moves_name = VALUES(skill_moves_name),
                player_year = VALUES(player_year), is_tradeable = VALUES(is_tradeable), is_active = TRUE,
                stats_data = VALUES(stats_data),
                prices_data = IF(
                    price_checked_at IS NULL OR price_checked_at <= VALUES(price_checked_at),
                    VALUES(prices_data), prices_data),
                traits_data = VALUES(traits_data),
                positions_data = VALUES(positions_data), play_styles_data = VALUES(play_styles_data),
                skills_data = VALUES(skills_data), raw_data = VALUES(raw_data),
                price_checked_at = IF(
                    price_checked_at IS NULL OR price_checked_at <= VALUES(price_checked_at),
                    VALUES(price_checked_at), price_checked_at),
                source_seen_at = VALUES(source_seen_at)
            """;

    private static final String PRICE_UPSERT = """
            INSERT INTO card_prices_current (
                cid, enhancement_level, price, observed_at, changed_at
            ) VALUES (?, ?, ?, ?, ?)
            ON DUPLICATE KEY UPDATE
                changed_at = IF(
                    observed_at <= VALUES(observed_at) AND price <> VALUES(price),
                    VALUES(changed_at), changed_at),
                price = IF(observed_at <= VALUES(observed_at), VALUES(price), price),
                observed_at = IF(observed_at <= VALUES(observed_at), VALUES(observed_at), observed_at)
            """;

    private final JdbcTemplate jdbcTemplate;
    private final JsonMapper jsonMapper;
    private final String importPath;

    public PlayerDataImporter(
            JdbcTemplate jdbcTemplate,
            JsonMapper jsonMapper,
            @Value("${fimo.players.import-path:}") String importPath) {
        this.jdbcTemplate = jdbcTemplate;
        this.jsonMapper = jsonMapper;
        this.importPath = importPath;
    }

    @Override
    public void run(ApplicationArguments args) throws IOException {
        JsonNode root = readSource();
        if (!root.isArray()) {
            throw new IllegalArgumentException("Player import root must be a JSON array");
        }
        if (root.isEmpty()) {
            throw new IllegalArgumentException("Player import source contains no cards");
        }

        boolean officialSnapshot = root.get(0).has("player");
        boolean completeSnapshot = officialSnapshot && validateCompleteSnapshot(root.size());
        Timestamp observedAt = utcTimestamp(Instant.now().truncatedTo(ChronoUnit.SECONDS));

        List<CardImport> cards = new ArrayList<>(root.size());
        for (JsonNode item : root) {
            JsonNode player = item.has("player") ? item.path("player") : item;
            List<JsonNode> playerClasses = new ArrayList<>();
            if (item.path("sourceClasses").isArray()) {
                item.path("sourceClasses").forEach(playerClasses::add);
            } else if (item.has("sourceClass")) {
                playerClasses.add(item.path("sourceClass"));
            }
            cards.add(new CardImport(
                    player,
                    List.copyOf(playerClasses),
                    timestamp(item, "sourceObservedAt", observedAt)));
        }

        importReferenceData(cards);
        batch(PLAYER_UPSERT, cards.stream().map(card -> playerRow(card, observedAt)).toList());
        importCardRelations(cards);
        importPrices(cards);

        if (completeSnapshot) {
            jdbcTemplate.update("""
                    UPDATE players
                       SET is_active = FALSE
                     WHERE source_seen_at IS NULL OR source_seen_at <> ?
                    """, observedAt);
        }

        long activeCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM players WHERE is_active = TRUE", Long.class);
        long totalCount = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM players", Long.class);
        System.out.printf(
                "Player import complete: source=%d, active=%d, total=%d, official=%s, complete=%s%n",
                cards.size(), activeCount, totalCount, officialSnapshot, completeSnapshot);
    }

    private JsonNode readSource() throws IOException {
        if (importPath == null || importPath.isBlank()) {
            var resource = new ClassPathResource("data/players.json");
            try (InputStream input = resource.getInputStream()) {
                return jsonMapper.readTree(input);
            }
        }
        try (InputStream input = Files.newInputStream(Path.of(importPath).toAbsolutePath())) {
            return jsonMapper.readTree(input);
        }
    }

    private boolean validateCompleteSnapshot(int sourceSize) {
        if (importPath == null || importPath.isBlank()) {
            return false;
        }
        Path source = Path.of(importPath).toAbsolutePath();
        Path manifestPath = source.resolveSibling("manifest.json");
        if (!Files.exists(manifestPath)) {
            return false;
        }
        try (InputStream input = Files.newInputStream(manifestPath)) {
            JsonNode manifest = jsonMapper.readTree(input);
            boolean complete = manifest.path("complete").asBoolean(false)
                    && "COMPLETE".equals(manifest.path("state").asString());
            if (!complete) {
                return false;
            }
            long manifestCount = manifest.path("uniqueCardCount").asLong(-1);
            long reportedCount = manifest.path("allCardsSummary").path("reportedCardCount").asLong(-1);
            if (manifestCount != sourceSize || reportedCount != sourceSize || sourceSize <= 0) {
                throw new IllegalStateException(
                        "Complete snapshot count mismatch: source=" + sourceSize
                                + ", unique=" + manifestCount + ", reported=" + reportedCount);
            }
            return true;
        } catch (IOException error) {
            throw new IllegalStateException("Failed to read snapshot manifest " + manifestPath, error);
        }
    }

    private void importReferenceData(List<CardImport> cards) {
        Map<Long, Object[]> profiles = new HashMap<>();
        Map<String, Object[]> classes = new HashMap<>();
        Map<Long, Object[]> nations = new HashMap<>();
        Map<Long, Object[]> leagues = new HashMap<>();
        Map<Long, Object[]> teams = new HashMap<>();

        for (CardImport card : cards) {
            JsonNode player = card.player();
            long pid = player.path("pid").longValue();
            profiles.put(pid, new Object[] {
                    pid, text(player, "playerKor"), text(player, "playerEng"),
                    number(player, "nationality"), text(player, "nation"),
                    date(player, "bday"), number(player, "height"), number(player, "weight"),
                    number(player, "mainFoot")
            });

            for (JsonNode playerClass : card.playerClasses()) {
                if (playerClass.hasNonNull("id")) {
                    String classId = playerClass.path("id").asString();
                    classes.put(classId, new Object[] {
                            classId, text(playerClass, "name"), text(playerClass, "image")
                    });
                }
            }

            addReference(nations, player, "nationality", new Object[] {
                    number(player, "nationality"), text(player, "nation"),
                    flagUrl(number(player, "nationality"))
            });
            addReference(leagues, player, "leagueid", new Object[] {
                    number(player, "leagueid"), text(player, "league"),
                    leagueLogoUrl(number(player, "leagueid"))
            });
            addReference(teams, player, "teamid", new Object[] {
                    number(player, "teamid"), text(player, "team"),
                    teamLogoUrl(number(player, "teamid"))
            });
        }

        batch(PROFILE_UPSERT, new ArrayList<>(profiles.values()));
        batch(CLASS_UPSERT, new ArrayList<>(classes.values()));
        batch(NATION_UPSERT, new ArrayList<>(nations.values()));
        batch(LEAGUE_UPSERT, new ArrayList<>(leagues.values()));
        batch(TEAM_UPSERT, new ArrayList<>(teams.values()));
    }

    private void addReference(Map<Long, Object[]> target, JsonNode player, String key, Object[] row) {
        Number value = number(player, key);
        if (value != null && row[1] != null) {
            target.put(value.longValue(), row);
        }
    }

    private Object[] playerRow(CardImport card, Timestamp observedAt) {
        JsonNode player = card.player();
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
        ObjectNode positions = jsonMapper.createObjectNode();
        copy(positions, player, "positionOrg", "postion2", "postionPanalty2", "postion3", "postionPanalty3");
        ObjectNode playStyles = jsonMapper.createObjectNode();
        copy(playStyles, player, "playStyleSlotMaxLevels", "staticPlayStyles", "staticPlayStyles_org");

        return new Object[] {
                player.path("cid").longValue(), player.path("pid").longValue(),
                text(player, "playerKor"), text(player, "playerEng"),
                text(player, "pimage"), text(player, "bimage"),
                player.path("ovr").intValue(), text(player, "position"), text(player, "potentialPosition"),
                number(player, "teamid"), text(player, "team"), number(player, "leagueid"), text(player, "league"),
                number(player, "nationality"), text(player, "nation"), number(player, "height"), number(player, "weight"),
                number(player, "mainFoot"), number(player, "WFA"), number(player, "skillMovesLevel"),
                text(player, "skillMovesName"), number(player, "PlayerYear"),
                player.path("noTrade").asInt(0) == 0,
                stats.toString(), prices.toString(), array(player, "Trait").toString(),
                positions.toString(), playStyles.toString(), array(player, "skillInfo").toString(),
                player.toString(), card.priceObservedAt(), observedAt
        };
    }

    private void importCardRelations(List<CardImport> cards) {
        List<Object[]> cids = cards.stream().map(card -> new Object[] { card.player().path("cid").longValue() }).toList();
        batch("DELETE FROM card_positions WHERE cid = ?", cids);
        batch("DELETE FROM card_classes WHERE cid = ?", cids);
        batch("DELETE FROM card_traits WHERE cid = ?", cids);
        batch("DELETE FROM card_play_styles WHERE cid = ?", cids);
        batch("DELETE FROM card_skills WHERE cid = ?", cids);

        List<Object[]> positions = new ArrayList<>();
        List<Object[]> classRows = new ArrayList<>();
        List<Object[]> traitRows = new ArrayList<>();
        Map<Long, Object[]> traits = new HashMap<>();
        List<Object[]> playStyleRows = new ArrayList<>();
        Map<String, Object[]> playStyles = new HashMap<>();
        List<Object[]> skillRows = new ArrayList<>();
        Map<String, Object[]> skills = new HashMap<>();

        for (CardImport card : cards) {
            JsonNode player = card.player();
            long cid = player.path("cid").longValue();
            for (JsonNode playerClass : card.playerClasses()) {
                if (playerClass.hasNonNull("id")) {
                    classRows.add(new Object[] { cid, playerClass.path("id").asString() });
                }
            }
            addPosition(positions, cid, text(player, "position"), "PRIMARY", null, 0);
            addPosition(positions, cid, text(player, "potentialPosition"), "POTENTIAL", null, 0);
            addPositions(positions, cid, array(player, "postion2"), "SECONDARY", number(player, "postionPanalty2"));
            addPositions(positions, cid, array(player, "postion3"), "TERTIARY", number(player, "postionPanalty3"));

            for (JsonNode trait : array(player, "Trait")) {
                long traitId = trait.path("id").longValue();
                String assetKey = TRAIT_ASSET_KEYS.get(traitId);
                traits.put(traitId, new Object[] {
                        traitId, text(trait, "name"), assetKey,
                        assetKey == null ? null : CDN + "/traits/playertraits_ICN_TRAIT_" + assetKey + ".png"
                });
                traitRows.add(new Object[] { cid, traitId });
            }

            int order = 0;
            for (JsonNode style : array(player, "staticPlayStyles")) {
                String id = style.asString();
                playStyles.put(id, new Object[] {
                        id, null, null, CDN + "/playstyle/playstyle_128/" + id + ".png"
                });
                playStyleRows.add(new Object[] { cid, id, order++, contains(array(player, "staticPlayStyles_org"), id) });
            }

            for (JsonNode skill : array(player, "skillInfo")) {
                String id = skill.path("id").asString();
                skills.put(id, new Object[] { id, null, null });
                skillRows.add(new Object[] { cid, id, skill.path("lv").asInt(0) });
            }
        }

        batch("INSERT INTO traits (trait_id, trait_name, asset_key, icon_url) VALUES (?, ?, ?, ?) "
                + "ON DUPLICATE KEY UPDATE trait_name=VALUES(trait_name), asset_key=VALUES(asset_key), icon_url=VALUES(icon_url)",
                new ArrayList<>(traits.values()));
        batch("INSERT INTO play_styles (play_style_id, play_style_name, description, icon_url) VALUES (?, ?, ?, ?) "
                + "ON DUPLICATE KEY UPDATE icon_url=VALUES(icon_url)", new ArrayList<>(playStyles.values()));
        batch("INSERT INTO skills (skill_id, skill_name, icon_url) VALUES (?, ?, ?) "
                + "ON DUPLICATE KEY UPDATE skill_id=VALUES(skill_id)", new ArrayList<>(skills.values()));

        batch("INSERT INTO card_classes (cid, class_id) VALUES (?, ?)", classRows);
        batch("INSERT INTO card_positions (cid, position_code, position_kind, penalty, sort_order) VALUES (?, ?, ?, ?, ?)", positions);
        batch("INSERT INTO card_traits (cid, trait_id) VALUES (?, ?)", traitRows);
        batch("INSERT INTO card_play_styles (cid, play_style_id, slot_order, is_original) VALUES (?, ?, ?, ?)", playStyleRows);
        batch("INSERT INTO card_skills (cid, skill_id, skill_level) VALUES (?, ?, ?)", skillRows);
    }

    private void importPrices(List<CardImport> cards) {
        List<Object[]> rows = new ArrayList<>(cards.size() * 16);
        for (CardImport card : cards) {
            JsonNode player = card.player();
            long cid = player.path("cid").longValue();
            for (int level = 0; level <= 15; level++) {
                rows.add(new Object[] {
                        cid, level, player.path("n8Price" + level).longValue(),
                        card.priceObservedAt(), card.priceObservedAt()
                });
            }
        }
        batch(PRICE_UPSERT, rows);
    }

    private void addPosition(
            List<Object[]> rows, long cid, String position, String kind, Number penalty, int order) {
        if (position != null && !position.isBlank()) {
            rows.add(new Object[] { cid, position, kind, penalty, order });
        }
    }

    private void addPositions(List<Object[]> rows, long cid, ArrayNode values, String kind, Number penalty) {
        int order = 0;
        for (JsonNode value : values) {
            addPosition(rows, cid, value.asString(), kind, penalty, order++);
        }
    }

    private void batch(String sql, List<Object[]> rows) {
        for (int start = 0; start < rows.size(); start += BATCH_SIZE) {
            jdbcTemplate.batchUpdate(sql, rows.subList(start, Math.min(start + BATCH_SIZE, rows.size())));
        }
    }

    private void copy(ObjectNode target, JsonNode source, String... keys) {
        for (String key : keys) {
            if (source.has(key)) {
                target.set(key, source.get(key));
            }
        }
    }

    private ArrayNode array(JsonNode node, String key) {
        JsonNode value = node.get(key);
        return value instanceof ArrayNode array ? array : jsonMapper.createArrayNode();
    }

    private boolean contains(ArrayNode values, String expected) {
        for (JsonNode value : values) {
            if (expected.equals(value.asString())) {
                return true;
            }
        }
        return false;
    }

    private String text(JsonNode node, String key) {
        JsonNode value = node.get(key);
        return value == null || value.isNull() ? null : value.asString();
    }

    private Number number(JsonNode node, String key) {
        JsonNode value = node.get(key);
        return value == null || value.isNull() || !value.isNumber() ? null : value.numberValue();
    }

    private Timestamp timestamp(JsonNode node, String key, Timestamp fallback) {
        String value = text(node, key);
        if (value == null || value.isBlank()) {
            return fallback;
        }
        try {
            return utcTimestamp(Instant.parse(value));
        } catch (DateTimeParseException ignored) {
            return fallback;
        }
    }

    private Timestamp utcTimestamp(Instant instant) {
        return Timestamp.valueOf(LocalDateTime.ofInstant(instant, ZoneOffset.UTC));
    }

    private Date date(JsonNode player, String key) {
        String value = text(player, key);
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return Date.valueOf(LocalDate.parse(value, BIRTH_DATE));
        } catch (DateTimeParseException ignored) {
            return null;
        }
    }

    private String flagUrl(Number id) {
        return id == null ? null : CDN + "/flags/flags_64x64/F_" + id.longValue() + ".png";
    }

    private String leagueLogoUrl(Number id) {
        return id == null ? null : CDN + "/league_logos/league_logos_256x256/L" + id.longValue() + ".png";
    }

    private String teamLogoUrl(Number id) {
        return id == null ? null : CDN + "/team_logos/team_logos_64x64/L" + id.longValue() + ".png";
    }

    private static Map<Long, String> traitAssetKeys() {
        String[] keys = {
                "INFLEXIBILITY", "LONGTHROWIN", "TAKESPOWERFULDRIVENFREEKICKS", "DIVER",
                "INJURYPRONE", "SOLIDPLAYER", "AVOIDSUSINGWEAKERFOOT", "DIVESINTOTACKLES",
                "TRIESTODEATOFFSIDETRAP", "SELFISH", "LEADERSHIP", "ARGUESWITHOFFICIALS",
                "EARLYCROSSER", "FINESSESHOT", "FLAIR", "LONGPASSER", "LONGSHOTTAKER",
                "SPEEDDRIDDLER", "PLAYMAKER", "PUSHESUPFORCORNERS", "PUNCHER", "GKLONGTHROWER",
                "POWERHEADER", "GKONEONONES", "GIANTTHROWIN", "OUTSIDEFOOTSHOT", "CROWDFAVOURITE",
                "SWERVE", "SECONDWIND", "ACROBATICCLEARANCE", "FANCYFEET", "FANCYPASSES",
                "FANCYFLICKS", "STUTTERPENALTY", "CHIPPEDPENALTY", "BICYCLEKICKS", "DIVINGHEADER",
                "DRIVENPASS", "GKFLATKICK", "ONECLUBPLAYER", "TEAMPLAYER"
        };
        Map<Long, String> values = new HashMap<>();
        for (int index = 0; index < keys.length; index++) {
            values.put((long) index, keys[index]);
        }
        return Map.copyOf(values);
    }

    private record CardImport(
            JsonNode player,
            List<JsonNode> playerClasses,
            Timestamp priceObservedAt) {
    }
}
