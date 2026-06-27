package com.behindthesmile.posting.service;

import com.behindthesmile.posting.config.AppProperties;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

@Service
public class XService {
    private static final String X_POST_URL = "https://api.x.com/2/tweets";

    private final HttpService httpService;
    private final ObjectMapper objectMapper;
    private final AppProperties appProperties;
    private final SecureRandom secureRandom = new SecureRandom();

    public XService(HttpService httpService, ObjectMapper objectMapper, AppProperties appProperties) {
        this.httpService = httpService;
        this.objectMapper = objectMapper;
        this.appProperties = appProperties;
    }

    public Map<String, Object> publishToX(String text) throws IOException, InterruptedException {
        AppProperties.X x = appProperties.x();

        if (notBlank(x.apiKey()) && notBlank(x.apiSecret()) && notBlank(x.accessTokenSecret())) {
            return publishToXWithOAuth1(text, x);
        }

        String accessToken = requireValue(x.accessToken(), "X_ACCESS_TOKEN");
        HttpRequest request = HttpRequest.newBuilder(URI.create(X_POST_URL))
                .header("Authorization", "Bearer " + accessToken)
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(objectMapper.writeValueAsString(Map.of("text", text)), StandardCharsets.UTF_8))
                .build();

        HttpResponse<String> response = httpService.client().send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        JsonNode payload = objectMapper.readTree(response.body());
        if (response.statusCode() < 200 || response.statusCode() >= 300) {
            throw new IllegalStateException("X publish failed: " + formatApiError(payload));
        }
        return buildResult(payload);
    }

    private Map<String, Object> publishToXWithOAuth1(String text, AppProperties.X x) throws IOException, InterruptedException {
        String apiKey = requireValue(x.apiKey(), "X_API_KEY");
        String apiSecret = requireValue(x.apiSecret(), "X_API_SECRET");
        String accessToken = requireValue(x.accessToken(), "X_ACCESS_TOKEN");
        String accessTokenSecret = requireValue(x.accessTokenSecret(), "X_ACCESS_TOKEN_SECRET");

        HttpRequest request = HttpRequest.newBuilder(URI.create(X_POST_URL))
                .header("Authorization", createOAuth1Header("POST", X_POST_URL, apiKey, apiSecret, accessToken, accessTokenSecret))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(objectMapper.writeValueAsString(Map.of("text", text)), StandardCharsets.UTF_8))
                .build();

        HttpResponse<String> response = httpService.client().send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        JsonNode payload = objectMapper.readTree(response.body());
        if (response.statusCode() < 200 || response.statusCode() >= 300) {
            throw new IllegalStateException("X publish failed: " + formatApiError(payload));
        }
        return buildResult(payload);
    }

    private Map<String, Object> buildResult(JsonNode payload) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("platform", "x");
        result.put("id", payload.path("data").path("id").asText(null));
        result.put("text", payload.path("data").path("text").asText(null));
        return result;
    }

    private String createOAuth1Header(
            String method,
            String url,
            String apiKey,
            String apiSecret,
            String accessToken,
            String accessTokenSecret
    ) {
        Map<String, String> oauth = new LinkedHashMap<>();
        oauth.put("oauth_consumer_key", apiKey);
        oauth.put("oauth_nonce", randomHex(24));
        oauth.put("oauth_signature_method", "HMAC-SHA1");
        oauth.put("oauth_timestamp", String.valueOf(System.currentTimeMillis() / 1000));
        oauth.put("oauth_token", accessToken);
        oauth.put("oauth_version", "1.0");

        List<String> signatureParams = new ArrayList<>();
        oauth.entrySet().stream()
                .sorted(Map.Entry.comparingByKey())
                .forEach(entry -> signatureParams.add(percentEncode(entry.getKey()) + "=" + percentEncode(entry.getValue())));

        String signatureBase = method.toUpperCase(Locale.ROOT)
                + "&" + percentEncode(url)
                + "&" + percentEncode(String.join("&", signatureParams));

        String signingKey = percentEncode(apiSecret) + "&" + percentEncode(accessTokenSecret);
        String signature = sign(signatureBase, signingKey);

        Map<String, String> headerValues = new LinkedHashMap<>(oauth);
        headerValues.put("oauth_signature", signature);

        List<String> parts = new ArrayList<>();
        for (Map.Entry<String, String> entry : headerValues.entrySet()) {
            parts.add(percentEncode(entry.getKey()) + "=\"" + percentEncode(entry.getValue()) + "\"");
        }
        return "OAuth " + String.join(", ", parts);
    }

    private String sign(String value, String key) {
        try {
            Mac mac = Mac.getInstance("HmacSHA1");
            mac.init(new SecretKeySpec(key.getBytes(StandardCharsets.UTF_8), "HmacSHA1"));
            return Base64.getEncoder().encodeToString(mac.doFinal(value.getBytes(StandardCharsets.UTF_8)));
        } catch (GeneralSecurityException ex) {
            throw new IllegalStateException("Failed to create OAuth signature.", ex);
        }
    }

    private String randomHex(int size) {
        byte[] bytes = new byte[size];
        secureRandom.nextBytes(bytes);
        StringBuilder builder = new StringBuilder(size * 2);
        for (byte value : bytes) {
            builder.append(String.format("%02x", value));
        }
        return builder.toString();
    }

    private String percentEncode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8)
                .replace("+", "%20")
                .replace("*", "%2A")
                .replace("%7E", "~");
    }

    private String formatApiError(JsonNode payload) throws IOException {
        JsonNode errorMessage = payload.path("error").path("message");
        return errorMessage.isTextual() ? errorMessage.asText() : objectMapper.writeValueAsString(payload);
    }

    private String requireValue(String value, String name) {
        if (value == null || value.isBlank()) {
            throw new IllegalStateException("Missing required setting: " + name);
        }
        return value;
    }

    private boolean notBlank(String value) {
        return value != null && !value.isBlank();
    }
}
