package com.behindthesmile.posting.service;

import com.behindthesmile.posting.model.GeneratedPostDraft;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class OpenSourceImageService {
    private static final Logger log = LoggerFactory.getLogger(OpenSourceImageService.class);
    private static final String COMMONS_API = "https://commons.wikimedia.org/w/api.php";
    private static final Duration DEFAULT_RATE_LIMIT_COOLDOWN = Duration.ofMinutes(2);

    private final HttpService httpService;
    private final ObjectMapper objectMapper;
    private final Map<String, Optional<ImageMatch>> queryCache = new ConcurrentHashMap<>();
    private volatile Instant rateLimitedUntil;

    public OpenSourceImageService(HttpService httpService, ObjectMapper objectMapper) {
        this.httpService = httpService;
        this.objectMapper = objectMapper;
    }

    public GeneratedPostDraft enrichDraftWithOpenImage(GeneratedPostDraft draft, String topic) {
        return enrichDraftWithOpenImage(draft, topic, Set.of());
    }

    public GeneratedPostDraft enrichDraftWithOpenImage(GeneratedPostDraft draft, String topic, Set<String> excludedImageUrls) {
        if (draft == null || hasImage(draft)) {
            return draft;
        }

        for (String query : buildQueries(draft, topic)) {
            Optional<ImageMatch> match = findMatch(query, excludedImageUrls);
            if (match.isEmpty()) {
                continue;
            }

            ImageMatch image = match.get();
            draft.setImageUrl(image.imageUrl());
            draft.setImageSourcePage(image.sourcePage());
            draft.setImageAttribution(image.attribution());
            draft.setImageLicense(image.license());
            return draft;
        }

        return draft;
    }

    private Optional<ImageMatch> findMatch(String query, Set<String> excludedImageUrls) {
        if (isRateLimited()) {
            log.info("Skipping open-source image lookup for query '{}' during cooldown window.", query);
            return Optional.empty();
        }

        Optional<ImageMatch> cached = queryCache.get(query);
        if (cached != null) {
            return cached.filter(match -> !excludedImageUrls.contains(match.imageUrl()));
        }

        try {
            String url = COMMONS_API
                    + "?action=query&format=json&origin=*&generator=search"
                    + "&gsrnamespace=6&gsrlimit=12&gsrsearch="
                    + URLEncoder.encode("file:" + query, StandardCharsets.UTF_8)
                    + "&prop=imageinfo%7Cinfo&iiprop=url%7Cextmetadata&inprop=url";

            HttpRequest request = HttpRequest.newBuilder(URI.create(url))
                    .header("Accept", "application/json")
                    .header("User-Agent", "BehindTheSmileBot/1.0 (social-posting prototype)")
                    .GET()
                    .build();
            HttpResponse<String> response = httpService.client()
                    .send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));

            if (response.statusCode() == 429) {
                activateRateLimitCooldown(response);
                log.warn("Open-source image lookup returned status 429 for query '{}'", query);
                return Optional.empty();
            }

            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                log.warn("Open-source image lookup returned status {} for query '{}'", response.statusCode(), query);
                return Optional.empty();
            }

            JsonNode root = objectMapper.readTree(response.body());
            JsonNode pages = root.path("query").path("pages");
            if (!pages.isObject()) {
                log.info("Open-source image lookup found no pages for query '{}'", query);
                return Optional.empty();
            }

            List<ImageMatch> candidates = new ArrayList<>();
            pages.fields().forEachRemaining(entry -> {
                JsonNode page = entry.getValue();
                JsonNode imageInfo = page.path("imageinfo");
                if (!imageInfo.isArray() || imageInfo.isEmpty()) {
                    return;
                }

                JsonNode info = imageInfo.get(0);
                String title = page.path("title").asText("");
                String imageUrl = info.path("url").asText("");
                String sourcePage = info.path("descriptionurl").asText(page.path("fullurl").asText(""));
                if (imageUrl.isBlank()
                        || sourcePage.isBlank()
                        || excludedImageUrls.contains(imageUrl)
                        || !isAllowedImage(title, imageUrl)) {
                    return;
                }

                JsonNode metadata = info.path("extmetadata");
                String license = metadata.path("LicenseShortName").path("value").asText("");
                String artist = stripHtml(metadata.path("Artist").path("value").asText(""));
                String credit = stripHtml(metadata.path("Credit").path("value").asText(""));
                String attribution = artist.isBlank() ? credit : artist;
                if (attribution.isBlank()) {
                    attribution = title.replace("File:", "");
                }

                int score = scoreCandidate(title, query, license);
                if (score > 0) {
                    candidates.add(new ImageMatch(imageUrl, sourcePage, attribution, license, score));
                }
            });

            Optional<ImageMatch> match = candidates.stream().max(Comparator.comparingInt(ImageMatch::score));
            queryCache.put(query, match);
            return match.filter(image -> !excludedImageUrls.contains(image.imageUrl()));
        } catch (Exception ex) {
            log.warn("Open-source image lookup failed for query '{}': {}", query, ex.getMessage());
            return Optional.empty();
        }
    }

    private List<String> buildQueries(GeneratedPostDraft draft, String topic) {
        String combined = (safe(topic) + " " + safe(draft.getVisualHint()) + " " + safe(draft.getText()))
                .toLowerCase(Locale.ROOT);

        List<String> queries = new ArrayList<>();
        String visualHintQuery = normalizeVisualHintQuery(draft.getVisualHint());
        if (!visualHintQuery.isBlank()) {
            queries.add(visualHintQuery);
        }
        queries.add(primaryQuery(combined));
        queries.add("window light interior");
        queries.add("quiet room interior");
        queries.add("notebook desk");
        queries.add("coffee mug window");
        queries.add("headphones");
        queries.add("street evening");
        return queries.stream().distinct().limit(4).toList();
    }

    private String normalizeVisualHintQuery(String visualHint) {
        String value = safe(visualHint).trim();
        if (value.isBlank()) {
            return "";
        }

        String normalized = value
                .replaceAll("^[\"'“”]+|[\"'“”]+$", "")
                .replaceAll("(?i)^photo of\\s+", "")
                .replaceAll("(?i)^image of\\s+", "")
                .replaceAll("(?i)^real photo of\\s+", "")
                .replaceAll("[.,;:!?]+", " ")
                .replaceAll("\\s+", " ")
                .trim();

        return normalized.length() > 80 ? normalized.substring(0, 80).trim() : normalized;
    }

    private String primaryQuery(String combined) {
        if (containsAny(combined, "навуш", "пісн", "лірик", "муз", "headphone", "music", "playlist")) {
            return "headphones";
        }
        if (containsAny(combined, "терап", "сес", "therapy", "psychotherapy", "notebook")) {
            return "notebook desk";
        }
        if (containsAny(combined, "вікн", "тиша", "кімнат", "ніч", "вечір", "window", "room")) {
            return "window light interior";
        }
        if (containsAny(combined, "дорог", "прогуля", "вулиц", "street", "walk")) {
            return "street evening";
        }
        if (containsAny(combined, "кава", "чай", "coffee", "tea")) {
            return "coffee mug window";
        }
        return "quiet room interior";
    }

    private boolean isAllowedImage(String title, String imageUrl) {
        String normalizedTitle = safe(title).toLowerCase(Locale.ROOT);
        String normalizedUrl = safe(imageUrl).toLowerCase(Locale.ROOT);
        boolean raster = normalizedTitle.endsWith(".jpg")
                || normalizedTitle.endsWith(".jpeg")
                || normalizedTitle.endsWith(".png")
                || normalizedUrl.contains(".jpg")
                || normalizedUrl.contains(".jpeg")
                || normalizedUrl.contains(".png");
        if (!raster) {
            return false;
        }

        return !containsAny(
                normalizedTitle,
                ".pdf", ".svg", ".gif", ".tif", ".tiff",
                "diagram", "logo", "coat of arms", "flag", "map", "stamp", "icon"
        );
    }

    private int scoreCandidate(String title, String query, String license) {
        int score = 0;
        String normalizedTitle = safe(title).toLowerCase(Locale.ROOT);
        String normalizedLicense = safe(license).toLowerCase(Locale.ROOT);

        for (String token : query.toLowerCase(Locale.ROOT).split("\\s+")) {
            if (normalizedTitle.contains(token)) {
                score += 3;
            }
        }

        if (normalizedLicense.contains("public domain")) {
            score += 3;
        } else if (normalizedLicense.contains("cc")) {
            score += 2;
        }

        if (normalizedTitle.endsWith(".jpg") || normalizedTitle.endsWith(".jpeg") || normalizedTitle.endsWith(".png")) {
            score += 3;
        }

        if (containsAny(normalizedTitle, "headphones", "window", "room", "interior", "coffee", "street", "notebook", "desk")) {
            score += 2;
        }

        if (containsAny(
                normalizedTitle,
                "book", "businesswoman", "archaeological", "museum", "document", "scan",
                "painting", "art.", "art)", "poster", "illustration", "engraving", "drawing", "iwmart"
        )) {
            score -= 8;
        }

        return score;
    }

    private boolean hasImage(GeneratedPostDraft draft) {
        return draft.getImageUrl() != null && !draft.getImageUrl().isBlank();
    }

    private boolean containsAny(String value, String... needles) {
        for (String needle : needles) {
            if (value.contains(needle)) {
                return true;
            }
        }
        return false;
    }

    private String stripHtml(String raw) {
        return raw == null ? "" : raw.replaceAll("<[^>]+>", "").replace("&quot;", "\"").trim();
    }

    private String safe(String value) {
        return value == null ? "" : value;
    }

    private boolean isRateLimited() {
        Instant blockedUntil = rateLimitedUntil;
        return blockedUntil != null && Instant.now().isBefore(blockedUntil);
    }

    private void activateRateLimitCooldown(HttpResponse<String> response) {
        Duration cooldown = parseRetryAfter(response).orElse(DEFAULT_RATE_LIMIT_COOLDOWN);
        rateLimitedUntil = Instant.now().plus(cooldown);
        log.warn("Open-source image lookup entered cooldown for {} seconds.", cooldown.toSeconds());
    }

    private Optional<Duration> parseRetryAfter(HttpResponse<?> response) {
        return response.headers()
                .firstValue("Retry-After")
                .flatMap(value -> {
                    try {
                        long seconds = Long.parseLong(value.trim());
                        return Optional.of(Duration.ofSeconds(Math.max(seconds, 30)));
                    } catch (NumberFormatException ignored) {
                        return Optional.empty();
                    }
                });
    }

    private record ImageMatch(
            String imageUrl,
            String sourcePage,
            String attribution,
            String license,
            int score
    ) {}
}
