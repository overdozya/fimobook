package com.fimobook.backend;

import java.io.IOException;
import java.net.CookieManager;
import java.net.CookiePolicy;
import java.net.HttpCookie;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

@Component
@ConditionalOnProperty(name = "fimo.price-refresh.enabled", havingValue = "true")
public class SquadMakerPriceClient {

    private static final URI PAGE_URI = URI.create("https://fcmobile.nexon.com/DataCenterWeb/SquadMaker");
    private static final URI INIT_URI = URI.create(
            "https://fcmobile.nexon.com/datacenterweb/SquadMakerAjaxInfo?strMethod=Init");
    private static final URI PLAYER_CLASS_URI = URI.create(
            "https://fcmobile.nexon.com/datacenterweb/SquadMakerAjaxInfo?strMethod=PlayerClass");
    private static final String TOKEN_COOKIE = "_dpvmTldhsfkdls_xhfptm";
    private static final String USER_AGENT =
            "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) "
                    + "AppleWebKit/537.36 (KHTML, like Gecko) Chrome/151 Safari/537.36";

    private final JsonMapper jsonMapper;
    private final Duration timeout;
    private CookieManager cookieManager;
    private HttpClient httpClient;
    private boolean initialized;

    public SquadMakerPriceClient(
            JsonMapper jsonMapper,
            @Value("${fimo.price-refresh.request-timeout-seconds:15}") long timeoutSeconds) {
        this.jsonMapper = jsonMapper;
        this.timeout = Duration.ofSeconds(timeoutSeconds);
        resetClient();
    }

    public synchronized List<JsonNode> fetchPlayerClasses(long pid) {
        try {
            if (!initialized) {
                initialize();
            }
            JsonNode response = playerClassRequest(pid);
            if (response.path("ResultCode").asInt() != 1) {
                initialize();
                response = playerClassRequest(pid);
            }
            if (response.path("ResultCode").asInt() != 1) {
                throw new IllegalStateException(
                        "SquadMaker PlayerClass failed: " + response.path("ResultMsg").asString());
            }
            JsonNode list = response.path("ResultData").path("PlayerList");
            if (!list.isArray()) {
                throw new IllegalStateException("SquadMaker PlayerClass response has no PlayerList");
            }
            return List.copyOf(list.values());
        } catch (IOException error) {
            initialized = false;
            throw new IllegalStateException("Failed to read SquadMaker price response", error);
        } catch (InterruptedException error) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("SquadMaker price request was interrupted", error);
        }
    }

    private void initialize() throws IOException, InterruptedException {
        resetClient();
        HttpRequest pageRequest = HttpRequest.newBuilder(PAGE_URI)
                .timeout(timeout)
                .header("Accept", "text/html")
                .header("User-Agent", USER_AGENT)
                .GET()
                .build();
        send(pageRequest);

        JsonNode init = post(INIT_URI, Map.of());
        if (init.path("ResultCode").asInt() != 1) {
            throw new IllegalStateException(
                    "SquadMaker Init failed: " + init.path("ResultMsg").asString());
        }
        initialized = true;
    }

    private JsonNode playerClassRequest(long pid) throws IOException, InterruptedException {
        Map<String, String> form = new LinkedHashMap<>();
        form.put("n8Pid", Long.toString(pid));
        return post(PLAYER_CLASS_URI, form);
    }

    private JsonNode post(URI uri, Map<String, String> values) throws IOException, InterruptedException {
        Map<String, String> form = new LinkedHashMap<>(values);
        form.put("__RequestVerificationToken", verificationToken());
        String body = form.entrySet().stream()
                .map(entry -> encode(entry.getKey()) + "=" + encode(entry.getValue()))
                .reduce((left, right) -> left + "&" + right)
                .orElse("");
        HttpRequest request = HttpRequest.newBuilder(uri)
                .timeout(timeout)
                .header("Accept", "application/json, text/plain, */*")
                .header("Content-Type", "application/x-www-form-urlencoded")
                .header("Origin", "https://fcmobile.nexon.com")
                .header("Referer", PAGE_URI.toString())
                .header("User-Agent", USER_AGENT)
                .header("X-Requested-With", "XMLHttpRequest")
                .POST(HttpRequest.BodyPublishers.ofString(body))
                .build();
        return jsonMapper.readTree(send(request));
    }

    private String send(HttpRequest request) throws IOException, InterruptedException {
        HttpResponse<String> response = httpClient.send(
                request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        if (response.statusCode() < 200 || response.statusCode() >= 300) {
            throw new IOException(
                    "SquadMaker returned HTTP " + response.statusCode() + " for " + request.uri());
        }
        return response.body();
    }

    private String verificationToken() {
        return cookieManager.getCookieStore().getCookies().stream()
                .filter(cookie -> TOKEN_COOKIE.equals(cookie.getName()))
                .map(HttpCookie::getValue)
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("SquadMaker verification cookie is missing"));
    }

    private void resetClient() {
        cookieManager = new CookieManager(null, CookiePolicy.ACCEPT_ALL);
        httpClient = HttpClient.newBuilder()
                .cookieHandler(cookieManager)
                .connectTimeout(timeout)
                .followRedirects(HttpClient.Redirect.NORMAL)
                .build();
        initialized = false;
    }

    private String encode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }
}
