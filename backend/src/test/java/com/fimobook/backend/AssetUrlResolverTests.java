package com.fimobook.backend;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class AssetUrlResolverTests {

    @TempDir
    Path temporaryDirectory;

    @Test
    void resolvesCollectedOfficialAssetToApiPath() throws Exception {
        Path asset = temporaryDirectory.resolve(
                "fco.vod.nexoncdn.co.kr/jade_assets/flags/flags_64x64/F_14.png");
        Files.createDirectories(asset.getParent());
        Files.writeString(asset, "test");
        var resolver = new AssetUrlResolver(temporaryDirectory.toString());

        String resolved = resolver.resolve(
                "https://fco.vod.nexoncdn.co.kr/jade_assets/flags/flags_64x64/F_14.png");

        assertThat(resolved).isEqualTo(
                "/api/assets/fco.vod.nexoncdn.co.kr/jade_assets/flags/flags_64x64/F_14.png");
    }

    @Test
    void keepsOriginalUrlWhenLocalAssetIsMissing() {
        var resolver = new AssetUrlResolver(temporaryDirectory.toString());
        String source = "https://fco.vod.nexoncdn.co.kr/jade_assets/missing.png";

        assertThat(resolver.resolve(source)).isEqualTo(source);
    }
}
