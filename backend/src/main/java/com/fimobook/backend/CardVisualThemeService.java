package com.fimobook.backend;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

@Component
public class CardVisualThemeService {

    private static final Logger LOGGER = LoggerFactory.getLogger(CardVisualThemeService.class);

    private final Map<String, CardVisualTheme> themes;

    public CardVisualThemeService(
            JsonMapper jsonMapper,
            @Value("${fimo.assets.card-colors-path:../data/fcmobile/assets/files/fco.vod.nexoncdn.co.kr/jade_assets_stage_bQ4IlcltxH6c8s5/card_colors/card_colors.json}")
            String cardColorsPath) {
        this.themes = loadThemes(jsonMapper, Path.of(cardColorsPath).toAbsolutePath().normalize());
    }

    public CardVisualTheme resolve(String backgroundImageUrl) {
        String imageName = imageName(backgroundImageUrl);
        return imageName == null
                ? CardVisualTheme.DEFAULT
                : themes.getOrDefault(imageName.toLowerCase(Locale.ROOT), CardVisualTheme.DEFAULT);
    }

    private Map<String, CardVisualTheme> loadThemes(JsonMapper jsonMapper, Path path) {
        if (!Files.isRegularFile(path)) {
            LOGGER.warn("Card color mapping was not found: {}", path);
            return Map.of();
        }
        try {
            JsonNode root = jsonMapper.readTree(path.toFile());
            Map<String, CardVisualTheme> loaded = new HashMap<>();
            for (JsonNode item : root) {
                String imageName = nullableText(item, "imgName");
                if (imageName == null) {
                    continue;
                }
                loaded.putIfAbsent(imageName.toLowerCase(Locale.ROOT), new CardVisualTheme(
                        color(item, "ovr"), color(item, "pos"), color(item, "name")));
            }
            return Map.copyOf(loaded);
        } catch (Exception error) {
            LOGGER.warn("Failed to load card color mapping: {}", path, error);
            return Map.of();
        }
    }

    private String imageName(String url) {
        if (url == null || url.isBlank()) {
            return null;
        }
        int slash = url.lastIndexOf('/');
        String filename = slash >= 0 ? url.substring(slash + 1) : url;
        int extension = filename.toLowerCase(Locale.ROOT).lastIndexOf(".png");
        return extension > 0 ? filename.substring(0, extension) : filename;
    }

    private String color(JsonNode item, String field) {
        String color = nullableText(item, field);
        return color == null || !color.matches("#[0-9A-Fa-f]{6}") ? "#ffffff" : color;
    }

    private String nullableText(JsonNode item, String field) {
        JsonNode value = item.get(field);
        return value == null || value.isNull() || value.asString().isBlank() ? null : value.asString();
    }
}
