package com.fimobook.backend;

import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Set;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class AssetUrlResolver {

    private static final Set<String> LOCAL_ASSET_HOSTS = Set.of(
            "fco.vod.nexoncdn.co.kr",
            "ssl.nexon.com");

    private final Path localRoot;

    public AssetUrlResolver(@Value("${fimo.assets.local-dir:../data/fcmobile/assets/files}") String localDir) {
        this.localRoot = Path.of(localDir).toAbsolutePath().normalize();
    }

    public String resolve(String sourceUrl) {
        if (sourceUrl == null || sourceUrl.isBlank() || sourceUrl.startsWith("/api/assets/")) {
            return sourceUrl;
        }
        try {
            URI source = URI.create(sourceUrl);
            String host = source.getHost();
            if (host == null || !LOCAL_ASSET_HOSTS.contains(host) || source.getPath() == null) {
                return sourceUrl;
            }
            Path localFile = localRoot.resolve(host).resolve(source.getPath().replaceFirst("^/+", "")).normalize();
            if (!localFile.startsWith(localRoot) || !Files.isRegularFile(localFile)) {
                return sourceUrl;
            }
            return "/api/assets/" + host + source.getRawPath();
        } catch (IllegalArgumentException error) {
            return sourceUrl;
        }
    }

    public Path localRoot() {
        return localRoot;
    }
}
