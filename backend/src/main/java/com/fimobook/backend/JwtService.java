package com.fimobook.backend;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.Base64;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ObjectNode;

@Service
public class JwtService {

    public record Claims(long userId, String email) {
    }

    private static final Base64.Encoder BASE64_URL_ENCODER = Base64.getUrlEncoder().withoutPadding();
    private static final Base64.Decoder BASE64_URL_DECODER = Base64.getUrlDecoder();

    private final byte[] secret;
    private final long expirationSeconds;
    private final JsonMapper jsonMapper;

    public JwtService(
            @Value("${fimo.jwt.secret}") String secret,
            @Value("${fimo.jwt.expiration-seconds}") long expirationSeconds,
            JsonMapper jsonMapper) {
        if (secret.length() < 32) {
            throw new IllegalArgumentException("JWT secret must be at least 32 characters");
        }
        this.secret = secret.getBytes(StandardCharsets.UTF_8);
        this.expirationSeconds = expirationSeconds;
        this.jsonMapper = jsonMapper;
    }

    public String create(long userId, String email) {
        try {
            String header = encode("{\"alg\":\"HS256\",\"typ\":\"JWT\"}".getBytes(StandardCharsets.UTF_8));
            ObjectNode payload = jsonMapper.createObjectNode();
            payload.put("sub", userId);
            payload.put("email", email);
            payload.put("iat", Instant.now().getEpochSecond());
            payload.put("exp", Instant.now().plusSeconds(expirationSeconds).getEpochSecond());
            String body = encode(jsonMapper.writeValueAsBytes(payload));
            String unsignedToken = header + "." + body;
            return unsignedToken + "." + encode(sign(unsignedToken));
        } catch (Exception exception) {
            throw new IllegalStateException("JWT creation failed", exception);
        }
    }

    public Claims verify(String token) {
        try {
            String[] parts = token.split("\\.");
            if (parts.length != 3) {
                throw new IllegalArgumentException("Invalid JWT format");
            }
            byte[] expected = sign(parts[0] + "." + parts[1]);
            byte[] actual = BASE64_URL_DECODER.decode(parts[2]);
            if (!MessageDigest.isEqual(expected, actual)) {
                throw new IllegalArgumentException("Invalid JWT signature");
            }

            var payload = jsonMapper.readTree(BASE64_URL_DECODER.decode(parts[1]));
            if (payload.path("exp").asLong() <= Instant.now().getEpochSecond()) {
                throw new IllegalArgumentException("JWT expired");
            }
            return new Claims(payload.path("sub").asLong(), payload.path("email").asString());
        } catch (IllegalArgumentException exception) {
            throw exception;
        } catch (Exception exception) {
            throw new IllegalArgumentException("Invalid JWT", exception);
        }
    }

    private byte[] sign(String value) throws Exception {
        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec(secret, "HmacSHA256"));
        return mac.doFinal(value.getBytes(StandardCharsets.UTF_8));
    }

    private String encode(byte[] value) {
        return BASE64_URL_ENCODER.encodeToString(value);
    }
}
