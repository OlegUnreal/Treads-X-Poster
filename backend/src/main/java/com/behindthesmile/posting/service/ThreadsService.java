package com.behindthesmile.posting.service;

import com.behindthesmile.posting.config.AppProperties;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URLEncoder;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;

@Service
public class ThreadsService {
    private static final String THREADS_API_BASE_URL = "https://graph.threads.net/v1.0";
    private static final Duration PROFILE_CACHE_TTL = Duration.ofMinutes(10);

    private final HttpService httpService;
    private final ObjectMapper objectMapper;
    private final AppProperties appProperties;
    private Instant cachedProfileAt;
    private String cachedAccountLabel;

    public ThreadsService(HttpService httpService, ObjectMapper objectMapper, AppProperties appProperties) {
        this.httpService = httpService;
        this.objectMapper = objectMapper;
        this.appProperties = appProperties;
    }

    public Map<String, Object> publishToThreads(String text) throws IOException, InterruptedException {
        return publishToThreads(text, null);
    }

    public Map<String, Object> publishToThreads(String text, String imageUrl) throws IOException, InterruptedException {
        String accessToken = requireValue(appProperties.threads().accessToken(), "THREADS_ACCESS_TOKEN");
        String userId = requireValue(appProperties.threads().userId(), "THREADS_USER_ID");

        String creationId = imageUrl == null || imageUrl.isBlank()
                ? createTextContainer(accessToken, userId, text)
                : createImageContainer(accessToken, userId, text, imageUrl);
        JsonNode published = publishContainer(accessToken, userId, creationId);

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("platform", "threads");
        result.put("id", published.path("id").asText(null));
        result.put("creationId", creationId);
        if (imageUrl != null && !imageUrl.isBlank()) {
            result.put("imageUrl", imageUrl);
        }
        return result;
    }

    public synchronized String fetchCurrentAccountLabel() {
        if (cachedAccountLabel != null && cachedProfileAt != null && cachedProfileAt.plus(PROFILE_CACHE_TTL).isAfter(Instant.now())) {
            return cachedAccountLabel;
        }

        String accessToken = appProperties.threads().accessToken();
        String userId = appProperties.threads().userId();
        if (accessToken == null || accessToken.isBlank() || userId == null || userId.isBlank()) {
            return null;
        }

        try {
            JsonNode payload = readUserProfile(accessToken, userId);
            String username = textValue(payload, "username");
            String name = textValue(payload, "name");

            String resolved = null;
            if (username != null && !username.isBlank()) {
              resolved = username.startsWith("@") ? username : "@" + username;
            } else if (name != null && !name.isBlank()) {
              resolved = name;
            }

            cachedAccountLabel = resolved;
            cachedProfileAt = Instant.now();
            return resolved;
        } catch (Exception ignored) {
            return null;
        }
    }

    private String createTextContainer(String accessToken, String userId, String text) throws IOException, InterruptedException {
        String body = form(Map.of(
                "media_type", "TEXT",
                "text", text,
                "access_token", accessToken
        ));

        HttpResponse<String> response = performRequest(
                THREADS_API_BASE_URL + "/" + userId + "/threads",
                body
        );

        JsonNode payload = objectMapper.readTree(response.body());
        if (response.statusCode() < 200 || response.statusCode() >= 300) {
            throw new IllegalStateException("Threads container creation failed: " + formatApiError(payload));
        }
        if (!payload.hasNonNull("id")) {
            throw new IllegalStateException("Threads container creation did not return an id.");
        }
        return payload.get("id").asText();
    }

    private String createImageContainer(String accessToken, String userId, String text, String imageUrl)
            throws IOException, InterruptedException {
        String body = form(Map.of(
                "media_type", "IMAGE",
                "image_url", imageUrl,
                "text", text,
                "access_token", accessToken
        ));

        HttpResponse<String> response = performRequest(
                THREADS_API_BASE_URL + "/" + userId + "/threads",
                body
        );

        JsonNode payload = objectMapper.readTree(response.body());
        if (response.statusCode() < 200 || response.statusCode() >= 300) {
            throw new IllegalStateException("Threads image container creation failed: " + formatApiError(payload));
        }
        if (!payload.hasNonNull("id")) {
            throw new IllegalStateException("Threads image container creation did not return an id.");
        }
        return payload.get("id").asText();
    }

    private JsonNode publishContainer(String accessToken, String userId, String creationId) throws IOException, InterruptedException {
        String body = form(Map.of(
                "creation_id", creationId,
                "access_token", accessToken
        ));

        HttpResponse<String> response = performRequest(
                THREADS_API_BASE_URL + "/" + userId + "/threads_publish",
                body
        );

        JsonNode payload = objectMapper.readTree(response.body());
        if (response.statusCode() < 200 || response.statusCode() >= 300) {
            throw new IllegalStateException("Threads publish failed: " + formatApiError(payload));
        }
        return payload;
    }

    private JsonNode readUserProfile(String accessToken, String userId) throws IOException, InterruptedException, URISyntaxException {
        String url = THREADS_API_BASE_URL + "/" + userId
                + "?fields=username,name,threads_profile_picture_url"
                + "&access_token=" + URLEncoder.encode(accessToken, StandardCharsets.UTF_8);

        HttpRequest request = HttpRequest.newBuilder(new URI(url))
                .GET()
                .build();

        HttpResponse<String> response = httpService.client().send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        JsonNode payload = objectMapper.readTree(response.body());
        if (response.statusCode() < 200 || response.statusCode() >= 300) {
            throw new IllegalStateException("Threads profile lookup failed: " + formatApiError(payload));
        }
        return payload;
    }

    private HttpResponse<String> performRequest(String url, String body) throws IOException, InterruptedException {
        HttpRequest request = HttpRequest.newBuilder(URI.create(url))
                .header("Content-Type", "application/x-www-form-urlencoded")
                .POST(HttpRequest.BodyPublishers.ofString(body, StandardCharsets.UTF_8))
                .build();

        try {
            return httpService.client().send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        } catch (IOException ex) {
            throw new IOException(buildNetworkErrorMessage(ex), ex);
        }
    }

    private String buildNetworkErrorMessage(IOException error) {
        StringBuilder details = new StringBuilder();
        if (error.getMessage() != null && !error.getMessage().isBlank()) {
            details.append(error.getMessage());
        }
        Throwable cause = error.getCause();
        if (cause != null && cause.getMessage() != null && !cause.getMessage().isBlank()) {
            if (!details.isEmpty()) {
                details.append(", ");
            }
            details.append(cause.getMessage());
        }
        return details.isEmpty()
                ? "Threads request failed before the API responded"
                : "Threads request failed before the API responded (" + details + ")";
    }

    private String formatApiError(JsonNode payload) throws IOException {
        JsonNode errorMessage = payload.path("error").path("message");
        return errorMessage.isTextual() ? errorMessage.asText() : objectMapper.writeValueAsString(payload);
    }

    private String form(Map<String, String> values) {
        StringBuilder builder = new StringBuilder();
        for (Map.Entry<String, String> entry : values.entrySet()) {
            if (!builder.isEmpty()) {
                builder.append("&");
            }
            builder.append(URLEncoder.encode(entry.getKey(), StandardCharsets.UTF_8))
                    .append("=")
                    .append(URLEncoder.encode(entry.getValue(), StandardCharsets.UTF_8));
        }
        return builder.toString();
    }

    private String requireValue(String value, String name) {
        if (value == null || value.isBlank()) {
            throw new IllegalStateException("Missing required setting: " + name);
        }
        return value;
    }

    private String textValue(JsonNode payload, String field) {
        JsonNode node = payload.path(field);
        return node.isTextual() ? node.asText() : null;
    }
}
