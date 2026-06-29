package com.behindthesmile.posting.service;

import com.behindthesmile.posting.config.AppProperties;
import com.behindthesmile.posting.model.GeneratedPostDraft;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.TimeUnit;

@Service
public class OpenAiService {
    private static final Logger log = LoggerFactory.getLogger(OpenAiService.class);
    private static final URI RESPONSES_URI = URI.create("https://api.openai.com/v1/responses");
    private static final URI IMAGE_GENERATIONS_URI = URI.create("https://api.openai.com/v1/images/generations");

    private final HttpService httpService;
    private final ObjectMapper objectMapper;
    private final AppProperties appProperties;

    public OpenAiService(HttpService httpService, ObjectMapper objectMapper, AppProperties appProperties) {
        this.httpService = httpService;
        this.objectMapper = objectMapper;
        this.appProperties = appProperties;
    }

    public List<String> generatePosts(String topic, String tone, String language, int count)
            throws IOException, InterruptedException {
        return generateDrafts(topic, tone, language, count).stream()
                .map(GeneratedPostDraft::getText)
                .toList();
    }

    public List<GeneratedPostDraft> generateDrafts(String topic, String tone, String language, int count)
            throws IOException, InterruptedException {
        String prompt = String.join("\n",
                "Create " + count + " personal social media post options.",
                "Topic: " + topic,
                "Language: " + language,
                "Tone: " + tone,
                "Each post must be suitable for both X and Threads.",
                "Keep every post under 260 characters.",
                "Write like a real person sharing a lived moment, not like a campaign, NGO, or awareness poster.",
                "Make the writing feel like a quiet voice-over from a personal vlog.",
                "Prefer short sentences, natural pauses, and emotionally precise wording.",
                "Use first-person reflection when it fits naturally.",
                "Prefer small concrete details, emotional honesty, and gentle wording over slogans or broad statements.",
                "Do not mention Behind The Smile unless the topic explicitly asks for it.",
                "Do not include direct medical advice beyond encouraging professional care and avoiding self-medication.",
                "For each post also provide visualHint as a short English search phrase for a real non-AI photo.",
                "visualHint should be 2 to 6 words, concrete, and searchable on open photo sources.",
                "Good examples: headphones on bed, notebook after therapy, evening window light, empty room at dusk, coffee mug by window, quiet street at night.",
                "Return JSON as: {\"posts\":[{\"text\":\"...\",\"visualHint\":\"...\"}]}"
        );
        return generateDraftsFromPrompt(prompt);
    }

    public List<String> generatePostsFromPrompt(String prompt)
            throws IOException, InterruptedException {
        String apiKey = requireValue(appProperties.openAi().apiKey(), "OPENAI_API_KEY");
        String model = appProperties.openAi().model();
        long startedAt = System.nanoTime();
        log.info("Generating text-only posts with OpenAI: model={}, promptChars={}", model, textLength(prompt));

        Map<String, Object> payload = Map.of(
                "model", model,
                "input", List.of(
                        Map.of(
                                "role", "system",
                                "content", buildSystemPrompt()
                        ),
                        Map.of(
                                "role", "user",
                                "content", prompt
                        )
                ),
                "text", Map.of(
                        "format", Map.of(
                                "type", "json_schema",
                                "name", "social_posts",
                                "schema", Map.of(
                                        "type", "object",
                                        "additionalProperties", false,
                                        "required", List.of("posts"),
                                        "properties", Map.of(
                                                "posts", Map.of(
                                                        "type", "array",
                                                        "minItems", 1,
                                                        "items", Map.of(
                                                                "type", "string",
                                                                "minLength", 1,
                                                                "maxLength", 280
                                                        )
                                                )
                                        )
                                )
                        )
                )
        );

        JsonNode parsed = sendAndParse(apiKey, model, payload);
        JsonNode postsNode = parsed.get("posts");
        if (postsNode == null || !postsNode.isArray()) {
            throw new IllegalStateException("OpenAI response did not include posts array.");
        }

        List<String> posts = new ArrayList<>();
        for (JsonNode post : postsNode) {
            posts.add(post.asText());
        }
        log.info("Generated text-only posts with OpenAI: count={}, elapsedMs={}", posts.size(), elapsedMillis(startedAt));
        return posts;
    }

    public List<GeneratedPostDraft> generateDraftsFromPrompt(String prompt)
            throws IOException, InterruptedException {
        String apiKey = requireValue(appProperties.openAi().apiKey(), "OPENAI_API_KEY");
        String model = appProperties.openAi().model();
        long startedAt = System.nanoTime();
        log.info("Generating post drafts with OpenAI: model={}, promptChars={}", model, textLength(prompt));

        Map<String, Object> payload = Map.of(
                "model", model,
                "input", List.of(
                        Map.of(
                                "role", "system",
                                "content", buildSystemPrompt()
                        ),
                        Map.of(
                                "role", "user",
                                "content", prompt
                        )
                ),
                "text", Map.of(
                        "format", Map.of(
                                "type", "json_schema",
                                "name", "social_posts",
                                "schema", Map.of(
                                        "type", "object",
                                        "additionalProperties", false,
                                        "required", List.of("posts"),
                                        "properties", Map.of(
                                                "posts", Map.of(
                                                        "type", "array",
                                                        "minItems", 1,
                                                        "items", Map.of(
                                                                "type", "object",
                                                                "additionalProperties", false,
                                                                "required", List.of("text", "visualHint"),
                                                                "properties", Map.of(
                                                                        "text", Map.of(
                                                                                "type", "string",
                                                                                "minLength", 1,
                                                                                "maxLength", 280
                                                                        ),
                                                                        "visualHint", Map.of(
                                                                                "type", "string",
                                                                                "minLength", 3,
                                                                                "maxLength", 180
                                                                        )
                                                                )
                                                        )
                                                )
                                        )
                                )
                        )
                )
        );

        JsonNode parsed = sendAndParse(apiKey, model, payload);
        JsonNode postsNode = parsed.get("posts");
        if (postsNode == null || !postsNode.isArray()) {
            throw new IllegalStateException("OpenAI response did not include posts array.");
        }

        List<GeneratedPostDraft> posts = new ArrayList<>();
        for (JsonNode post : postsNode) {
            GeneratedPostDraft draft = new GeneratedPostDraft();
            draft.setText(post.path("text").asText());
            draft.setVisualHint(post.path("visualHint").asText());
            posts.add(draft);
        }
        log.info("Generated post drafts with OpenAI: count={}, elapsedMs={}", posts.size(), elapsedMillis(startedAt));
        return posts;
    }

    public GeneratedPostDraft generateDraftForImage(
            byte[] imageBytes,
            String mimeType,
            String fileName,
            String prompt,
            String topic,
            String tone,
            String language
    ) throws IOException, InterruptedException {
        String apiKey = requireValue(appProperties.openAi().apiKey(), "OPENAI_API_KEY");
        String model = appProperties.openAi().model();
        String dataUrl = "data:" + normalizeMimeType(mimeType, fileName) + ";base64,"
                + Base64.getEncoder().encodeToString(imageBytes);
        String userPrompt = buildImageCaptionPrompt(prompt, topic, tone, language, fileName);
        long startedAt = System.nanoTime();
        log.info("Generating image-based post with OpenAI: model={}, fileName={}, promptChars={}",
                model,
                fileName,
                textLength(userPrompt));

        Map<String, Object> payload = Map.of(
                "model", model,
                "input", List.of(
                        Map.of(
                                "role", "system",
                                "content", buildSystemPrompt()
                        ),
                        Map.of(
                                "role", "user",
                                "content", List.of(
                                        Map.of("type", "input_text", "text", userPrompt),
                                        Map.of("type", "input_image", "image_url", dataUrl)
                                )
                        )
                ),
                "text", Map.of(
                        "format", Map.of(
                                "type", "json_schema",
                                "name", "image_social_post",
                                "schema", Map.of(
                                        "type", "object",
                                        "additionalProperties", false,
                                        "required", List.of("text", "visualHint"),
                                        "properties", Map.of(
                                                "text", Map.of(
                                                        "type", "string",
                                                        "minLength", 1,
                                                        "maxLength", 280
                                                ),
                                                "visualHint", Map.of(
                                                        "type", "string",
                                                        "minLength", 3,
                                                        "maxLength", 180
                                                )
                                        )
                                )
                        )
                )
        );

        JsonNode parsed = sendAndParse(apiKey, model, payload);
        GeneratedPostDraft draft = new GeneratedPostDraft();
        draft.setText(parsed.path("text").asText());
        draft.setVisualHint(parsed.path("visualHint").asText());
        log.info("Generated image-based post with OpenAI: elapsedMs={}", elapsedMillis(startedAt));
        return draft;
    }

    public GeneratedImage generateQueueImage(String postText, String topic, String visualHint)
            throws IOException, InterruptedException {
        String apiKey = requireValue(appProperties.openAi().apiKey(), "OPENAI_API_KEY");
        String model = appProperties.openAi().imageModel();
        String prompt = buildImagePrompt(postText, topic, visualHint);
        long startedAt = System.nanoTime();
        log.info("Generating queue image with OpenAI: model={}, promptChars={}", model, textLength(prompt));

        Map<String, Object> payload = Map.of(
                "model", model,
                "prompt", prompt,
                "size", "1024x1024",
                "quality", "low",
                "output_format", "jpeg"
        );

        HttpRequest request = HttpRequest.newBuilder(IMAGE_GENERATIONS_URI)
                .header("Authorization", "Bearer " + apiKey)
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(objectMapper.writeValueAsString(payload), StandardCharsets.UTF_8))
                .build();

        HttpResponse<String> response = httpService.client().send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        JsonNode responseJson = objectMapper.readTree(response.body());
        log.info("OpenAI image response received: status={}, elapsedMs={}, bodyChars={}",
                response.statusCode(),
                elapsedMillis(startedAt),
                textLength(response.body()));

        if (response.statusCode() < 200 || response.statusCode() >= 300) {
            String error = formatApiError(responseJson);
            log.warn("OpenAI image request failed: status={}, error={}", response.statusCode(), error);
            throw new IllegalStateException("OpenAI image request failed: " + error);
        }

        String b64 = responseJson.path("data").path(0).path("b64_json").asText("");
        if (b64.isBlank()) {
            throw new IllegalStateException("OpenAI image response did not include b64_json.");
        }

        byte[] bytes = Base64.getDecoder().decode(b64);
        log.info("Generated queue image with OpenAI: bytes={}, elapsedMs={}", bytes.length, elapsedMillis(startedAt));
        return new GeneratedImage(bytes, ".jpg");
    }

    private JsonNode sendAndParse(String apiKey, String model, Map<String, Object> payload)
            throws IOException, InterruptedException {
        long startedAt = System.nanoTime();
        log.info("Sending OpenAI request: model={}", model);
        HttpRequest request = HttpRequest.newBuilder(RESPONSES_URI)
                .header("Authorization", "Bearer " + apiKey)
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(objectMapper.writeValueAsString(payload), StandardCharsets.UTF_8))
                .build();

        HttpResponse<String> response = httpService.client().send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        log.info("OpenAI response received: status={}, elapsedMs={}, bodyChars={}",
                response.statusCode(),
                elapsedMillis(startedAt),
                textLength(response.body()));
        JsonNode responseJson = objectMapper.readTree(response.body());

        if (response.statusCode() < 200 || response.statusCode() >= 300) {
            String error = formatApiError(responseJson);
            log.warn("OpenAI request failed: status={}, error={}", response.statusCode(), error);
            throw new IllegalStateException("OpenAI request failed: " + error);
        }

        String outputText = extractOutputText(responseJson);
        log.info("OpenAI output text extracted: chars={}", textLength(outputText));
        return objectMapper.readTree(outputText);
    }

    private String buildSystemPrompt() {
        return String.join(" ",
                "You write concise, natural social media posts. Return only valid JSON.",
                "The voice must feel personal, reflective, and emotionally honest, never corporate, preachy, or campaign-like.",
                "The writing should feel like a soft voice-over from an introspective YouTube vlog.",
                "Prefer rhythm, pauses, and short sentences over polished copywriting.",
                "Prefer concrete moments, small details, and humane language over slogans, awareness copy, or abstract advocacy.",
                "Posts should feel cinematic in a simple everyday way: room light, walking, headphones, windows, evening, silence, notes, ordinary objects.",
                "Do not force brand mentions. Do not mention Behind The Smile unless the user explicitly asks.",
                "For health, medicine, military, trauma, pain, PTSD, sleep, anxiety, addiction, or therapy topics: do not give dosages, treatment plans, diagnosis, sourcing instructions, or advice to use controlled medicines.",
                "Do not glamorize substances, self-destruction, or untreated suffering.",
                "If therapy, dependence, or pain appears, frame it as lived experience, reflection, and support for professional care rather than instruction.",
                "Prefer soft, stigma-reducing, Ukraine-aware language that sounds like one person talking to another."
        );
    }

    private String buildImagePrompt(String postText, String topic, String visualHint) {
        return String.join("\n",
                "Create one square social media image for a reflective Ukrainian personal post.",
                "Use the post itself as the main creative brief, but do not put any text, captions, logos, UI, watermarks, or readable words in the image.",
                "Style: natural documentary photo, soft light, ordinary real-life objects, quiet cinematic mood, human but not staged.",
                "Avoid medical scenes, pills, alcohol, self-harm imagery, crying closeups, hospital rooms, weapons, and anything sensational.",
                "Prefer symbolic everyday visuals: room light, window, mug, notebook, headphones, empty chair, street at dusk, kitchen table, blanket, phone face down.",
                "No identifiable person unless the post clearly requires it; if a person appears, keep them anonymous from behind or cropped.",
                "Topic: " + safeForPrompt(topic),
                "Visual idea: " + safeForPrompt(visualHint),
                "Post text: " + safeForPrompt(postText)
        );
    }

    private String buildImageCaptionPrompt(String prompt, String topic, String tone, String language, String fileName) {
        return String.join("\n",
                "Look at the attached photo and create exactly one social media post caption for it.",
                "Topic/context: " + safeForPrompt(topic),
                "Language: " + safeForPrompt(language),
                "Tone: " + safeForPrompt(tone),
                "Original file name: " + safeForPrompt(fileName),
                "User instructions: " + safeForPrompt(prompt),
                "The post must fit both X and Threads.",
                "Keep it under 260 characters.",
                "Make the caption feel connected to the real image details, not generic.",
                "Do not describe the photo mechanically. Use it as a lived moment.",
                "Do not invent private facts about identifiable people in the photo.",
                "Avoid hashtags unless the user explicitly asks.",
                "Return JSON as: {\"text\":\"...\",\"visualHint\":\"...\"}"
        );
    }

    private String normalizeMimeType(String mimeType, String fileName) {
        String normalized = mimeType == null ? "" : mimeType.toLowerCase(Locale.ROOT);
        if (List.of("image/jpeg", "image/png", "image/webp").contains(normalized)) {
            return normalized;
        }
        String lowerName = fileName == null ? "" : fileName.toLowerCase(Locale.ROOT);
        if (lowerName.endsWith(".png")) {
            return "image/png";
        }
        if (lowerName.endsWith(".webp")) {
            return "image/webp";
        }
        return "image/jpeg";
    }

    private String safeForPrompt(String value) {
        if (value == null || value.isBlank()) {
            return "";
        }
        String normalized = value.replaceAll("\\s+", " ").trim();
        return normalized.length() > 900 ? normalized.substring(0, 900).trim() : normalized;
    }

    private String extractOutputText(JsonNode payload) {
        JsonNode direct = payload.get("output_text");
        if (direct != null && direct.isTextual()) {
            return direct.asText();
        }

        JsonNode output = payload.get("output");
        if (output != null && output.isArray()) {
            for (JsonNode item : output) {
                JsonNode content = item.get("content");
                if (content == null || !content.isArray()) {
                    continue;
                }
                for (JsonNode piece : content) {
                    if ("output_text".equals(piece.path("type").asText()) && piece.hasNonNull("text")) {
                        return piece.get("text").asText();
                    }
                }
            }
        }

        throw new IllegalStateException("OpenAI response did not include output text.");
    }

    private String formatApiError(JsonNode payload) throws IOException {
        JsonNode errorMessage = payload.path("error").path("message");
        return errorMessage.isTextual() ? errorMessage.asText() : objectMapper.writeValueAsString(payload);
    }

    private String requireValue(String value, String name) {
        if (value == null || value.isBlank()) {
            throw new IllegalStateException("Missing required setting: " + name);
        }
        String trimmed = value.trim();
        String normalized = trimmed.toLowerCase(Locale.ROOT);
        if (normalized.startsWith("bearer ")) {
            throw new IllegalStateException("Required setting " + name + " must contain only the raw token, without Bearer prefix.");
        }
        if (trimmed.chars().anyMatch(Character::isWhitespace)) {
            throw new IllegalStateException("Required setting " + name + " contains whitespace. Paste only the raw token.");
        }
        if (normalized.startsWith("your_")
                || normalized.startsWith("your-")
                || normalized.contains("api_key_here")
                || normalized.contains("your api")
                || normalized.contains("твій")) {
            throw new IllegalStateException("Required setting " + name + " still contains a placeholder value.");
        }
        return trimmed;
    }

    private long elapsedMillis(long startedAt) {
        return TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startedAt);
    }

    private int textLength(String value) {
        return value == null ? 0 : value.length();
    }

    public record GeneratedImage(byte[] bytes, String extension) {}
}
