package com.behindthesmile.posting.service;

import com.behindthesmile.posting.model.ContentPlan;
import com.behindthesmile.posting.model.GeneratedPostDraft;
import com.behindthesmile.posting.model.QueuedPost;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.charset.Charset;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

@Service
public class QueueService {
    private static final Charset WINDOWS_1252 = Charset.forName("windows-1252");
    private final ObjectMapper objectMapper;

    public QueueService(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public List<ContentPlan.Item> readContentPlan(Path filePath) throws IOException {
        if (!Files.exists(filePath)) {
            throw new IllegalStateException("Content plan not found: " + filePath.toAbsolutePath());
        }

        JsonNode root = objectMapper.readTree(Files.readString(filePath, StandardCharsets.UTF_8));
        JsonNode itemsNode = root.get("items");
        if (itemsNode == null || !itemsNode.isArray() || itemsNode.isEmpty()) {
            throw new IllegalStateException("Content plan must include a non-empty items array.");
        }

        List<ContentPlan.Item> items = new ArrayList<>();
        for (JsonNode itemNode : itemsNode) {
            items.add(objectMapper.treeToValue(itemNode, ContentPlan.Item.class));
        }
        return items;
    }

    public Path saveQueuedPosts(
            List<GeneratedPostDraft> posts,
            String topic,
            String tone,
            String language,
            List<String> platforms,
            Path filePath
    ) throws IOException {
        Path absolutePath = filePath.toAbsolutePath();
        Files.createDirectories(absolutePath.getParent());
        String now = Instant.now().toString();
        List<String> lines = new ArrayList<>();

        for (GeneratedPostDraft draft : posts) {
            QueuedPost post = new QueuedPost();
            post.setId(createQueueId());
            post.setStatus("ready");
            post.setCreatedAt(now);
            post.setTopic(topic);
            post.setTone(tone);
            post.setLanguage(language);
            post.setPlatforms(platforms);
            post.setText(draft.getText());
            post.setVisualHint(draft.getVisualHint());
            post.setImageUrl(draft.getImageUrl());
            post.setImageSourcePage(draft.getImageSourcePage());
            post.setImageAttribution(draft.getImageAttribution());
            post.setImageLicense(draft.getImageLicense());
            lines.add(objectMapper.writeValueAsString(post));
        }

        Files.writeString(
                absolutePath,
                String.join(System.lineSeparator(), lines) + System.lineSeparator(),
                StandardCharsets.UTF_8,
                StandardOpenOption.CREATE,
                StandardOpenOption.APPEND
        );
        return absolutePath;
    }

    public List<QueuedPost> readQueuedPosts(Path filePath) throws IOException {
        Path absolutePath = filePath.toAbsolutePath();
        if (!Files.exists(absolutePath)) {
            return List.of();
        }

        List<QueuedPost> posts = new ArrayList<>();
        for (String line : Files.readAllLines(absolutePath, StandardCharsets.UTF_8)) {
            if (line == null || line.isBlank()) {
                continue;
            }
            QueuedPost post = objectMapper.readValue(line, QueuedPost.class);
            posts.add(normalizePostEncoding(post));
        }
        return posts;
    }

    public Path writeQueuedPosts(List<QueuedPost> posts, Path filePath) throws IOException {
        Path absolutePath = filePath.toAbsolutePath();
        Files.createDirectories(absolutePath.getParent());
        List<String> lines = new ArrayList<>();
        for (QueuedPost post : posts) {
            lines.add(objectMapper.writeValueAsString(post));
        }
        String content = lines.isEmpty() ? "" : String.join(System.lineSeparator(), lines) + System.lineSeparator();
        Files.writeString(
                absolutePath,
                content,
                StandardCharsets.UTF_8,
                StandardOpenOption.CREATE,
                StandardOpenOption.TRUNCATE_EXISTING
        );
        return absolutePath;
    }

    public QueuedPost createQueuedPost(
            String topic,
            String text,
            String visualHint,
            String imageUrl,
            String imageSourcePage,
            String imageAttribution,
            String imageLicense,
            String tone,
            String language,
            List<String> platforms,
            String status
    ) {
        QueuedPost post = new QueuedPost();
        post.setId(createQueueId());
        post.setStatus(status == null || status.isBlank() ? "ready" : status);
        post.setCreatedAt(Instant.now().toString());
        post.setTopic(topic);
        post.setTone(tone);
        post.setLanguage(language);
        post.setPlatforms(platforms == null || platforms.isEmpty() ? List.of("x", "threads") : platforms);
        post.setText(text);
        post.setVisualHint(visualHint);
        post.setImageUrl(imageUrl);
        post.setImageSourcePage(imageSourcePage);
        post.setImageAttribution(imageAttribution);
        post.setImageLicense(imageLicense);
        return post;
    }

    public QueuedPost updateQueuedPost(Path filePath, String id, QueuedPost updatedPost) throws IOException {
        List<QueuedPost> posts = readQueuedPosts(filePath);
        for (int i = 0; i < posts.size(); i++) {
            QueuedPost current = posts.get(i);
            if (!id.equals(current.getId())) {
                continue;
            }

            updatedPost.setId(current.getId());
            updatedPost.setCreatedAt(current.getCreatedAt());
            if (updatedPost.getPublished() == null) {
                updatedPost.setPublished(current.getPublished());
            }
            posts.set(i, updatedPost);
            writeQueuedPosts(posts, filePath);
            return updatedPost;
        }

        throw new IllegalStateException("Queued post not found: " + id);
    }

    public void deleteQueuedPost(Path filePath, String id) throws IOException {
        List<QueuedPost> posts = new ArrayList<>(readQueuedPosts(filePath));
        boolean removed = posts.removeIf(post -> id.equals(post.getId()));
        if (!removed) {
            throw new IllegalStateException("Queued post not found: " + id);
        }
        writeQueuedPosts(posts, filePath);
    }

    public QueuedPost moveQueuedPost(Path filePath, String id, int offset) throws IOException {
        if (offset == 0) {
            throw new IllegalStateException("Move offset must not be 0.");
        }

        List<QueuedPost> posts = new ArrayList<>(readQueuedPosts(filePath));
        int currentIndex = -1;
        for (int index = 0; index < posts.size(); index++) {
            if (id.equals(posts.get(index).getId())) {
                currentIndex = index;
                break;
            }
        }

        if (currentIndex < 0) {
            throw new IllegalStateException("Queued post not found: " + id);
        }

        int targetIndex = Math.max(0, Math.min(posts.size() - 1, currentIndex + offset));
        if (targetIndex == currentIndex) {
            return posts.get(currentIndex);
        }

        QueuedPost moved = posts.remove(currentIndex);
        posts.add(targetIndex, moved);
        writeQueuedPosts(posts, filePath);
        return moved;
    }

    public QueuedPost appendQueuedPost(Path filePath, QueuedPost post) throws IOException {
        List<QueuedPost> posts = new ArrayList<>(readQueuedPosts(filePath));
        posts.add(post);
        writeQueuedPosts(posts, filePath);
        return post;
    }

    public void markQueuedPostPublished(Path filePath, String id, String platform, Object result) throws IOException {
        List<QueuedPost> posts = readQueuedPosts(filePath);
        boolean found = false;
        String now = Instant.now().toString();

        for (QueuedPost post : posts) {
            if (!id.equals(post.getId())) {
                continue;
            }

            found = true;
            Map<String, QueuedPost.PublishedInfo> published =
                    post.getPublished() == null ? new HashMap<>() : new HashMap<>(post.getPublished());

            QueuedPost.PublishedInfo info = new QueuedPost.PublishedInfo();
            info.setAt(now);
            info.setResult(result);
            published.put(platform, info);
            post.setPublished(published);

            List<String> expectedPlatforms = post.getPlatforms() == null ? List.of() : post.getPlatforms();
            boolean fullyPublished = !expectedPlatforms.isEmpty()
                    && expectedPlatforms.stream().allMatch(expected -> published.get(expected) != null);

            if (fullyPublished) {
                post.setStatus("posted");
            }
        }

        if (!found) {
            throw new IllegalStateException("Queued post not found: " + id);
        }

        writeQueuedPosts(posts, filePath);
    }

    public long countReadyPosts(Path filePath, String platform) throws IOException {
        return readQueuedPosts(filePath).stream()
                .filter(post -> "ready".equals(post.getStatus()))
                .filter(post -> {
                    if (platform == null || platform.isBlank()) {
                        return true;
                    }
                    List<String> platforms = post.getPlatforms() == null ? List.of() : post.getPlatforms();
                    boolean published = post.getPublished() != null && post.getPublished().get(platform) != null;
                    return platforms.contains(platform) && !published;
                })
                .count();
    }

    public Path saveXComposerLinks(Path queuePath, Path filePath) throws IOException {
        List<QueuedPost> posts = readQueuedPosts(queuePath).stream()
                .filter(post -> "ready".equals(post.getStatus()))
                .filter(post -> {
                    List<String> platforms = post.getPlatforms() == null ? List.of() : post.getPlatforms();
                    boolean published = post.getPublished() != null && post.getPublished().get("x") != null;
                    return platforms.contains("x") && !published;
                })
                .toList();

        Path absolutePath = filePath.toAbsolutePath();
        Files.createDirectories(absolutePath.getParent());
        StringBuilder rows = new StringBuilder();
        for (int i = 0; i < posts.size(); i++) {
            QueuedPost post = posts.get(i);
            String url = "https://x.com/intent/tweet?text=" + URLEncoder.encode(post.getText(), StandardCharsets.UTF_8);
            rows.append("<article>\n")
                    .append("  <h2>Post ").append(i + 1).append("</h2>\n")
                    .append("  <p>").append(escapeHtml(post.getText())).append("</p>\n")
                    .append("  <a href=\"").append(url).append("\" target=\"_blank\" rel=\"noreferrer\">Open in X</a>\n")
                    .append("</article>\n");
        }

        String html = """
                <!doctype html>
                <html lang="uk">
                <head>
                  <meta charset="utf-8">
                  <title>Ready X Posts</title>
                  <style>
                    body { font-family: Arial, sans-serif; max-width: 860px; margin: 32px auto; padding: 0 16px; line-height: 1.5; }
                    article { border: 1px solid #ddd; border-radius: 8px; padding: 16px; margin: 16px 0; }
                    h1 { font-size: 28px; }
                    h2 { font-size: 18px; margin-top: 0; }
                    a { display: inline-block; margin-top: 8px; padding: 8px 12px; background: #111; color: #fff; border-radius: 6px; text-decoration: none; }
                  </style>
                </head>
                <body>
                  <h1>Ready X Posts</h1>
                %s
                </body>
                </html>
                """.formatted(rows.length() == 0 ? "  <p>No ready X posts.</p>" : rows.toString());

        Files.writeString(
                absolutePath,
                html,
                StandardCharsets.UTF_8,
                StandardOpenOption.CREATE,
                StandardOpenOption.TRUNCATE_EXISTING
        );
        return absolutePath;
    }

    public QueuedPost selectQueuedPost(Path queuePath, String platform, int index, String emptyMessage) throws IOException {
        List<QueuedPost> posts = readQueuedPosts(queuePath).stream()
                .filter(post -> "ready".equals(post.getStatus()))
                .filter(post -> {
                    List<String> platforms = post.getPlatforms() == null ? List.of() : post.getPlatforms();
                    boolean published = post.getPublished() != null && post.getPublished().get(platform) != null;
                    return platforms.contains(platform) && !published;
                })
                .toList();

        if (posts.isEmpty()) {
            throw new IllegalStateException(emptyMessage);
        }

        int selectedIndex = Math.max(index - 1, 0);
        return selectedIndex < posts.size() ? posts.get(selectedIndex) : posts.getFirst();
    }

    public List<String> parsePlatforms(String rawPlatforms) {
        if (rawPlatforms == null || rawPlatforms.isBlank()) {
            return List.of("x", "threads");
        }

        List<String> platforms = Arrays.stream(rawPlatforms.split(","))
                .map(value -> value.trim().toLowerCase(Locale.ROOT))
                .filter(value -> !value.isBlank())
                .toList();

        List<String> allowed = List.of("x", "threads");
        List<String> unknown = platforms.stream().filter(value -> !allowed.contains(value)).toList();
        if (!unknown.isEmpty()) {
            throw new IllegalStateException("Unknown platform(s): " + String.join(", ", unknown));
        }

        return platforms.isEmpty() ? List.of("x", "threads") : platforms;
    }

    private String createQueueId() {
        return System.currentTimeMillis() + "-" + UUID.randomUUID().toString().replace("-", "").substring(0, 12);
    }

    private QueuedPost normalizePostEncoding(QueuedPost post) {
        post.setTopic(normalizeBrokenUtf8(post.getTopic()));
        post.setTone(normalizeBrokenUtf8(post.getTone()));
        post.setLanguage(normalizeBrokenUtf8(post.getLanguage()));
        post.setText(normalizeBrokenUtf8(post.getText()));
        post.setVisualHint(normalizeBrokenUtf8(post.getVisualHint()));
        post.setImageSourcePage(normalizeBrokenUtf8(post.getImageSourcePage()));
        post.setImageAttribution(normalizeBrokenUtf8(post.getImageAttribution()));
        post.setImageLicense(normalizeBrokenUtf8(post.getImageLicense()));
        return post;
    }

    private String normalizeBrokenUtf8(String value) {
        if (value == null || value.isBlank() || !looksLikeMojibake(value)) {
            return value;
        }

        String normalized = value;
        for (int attempt = 0; attempt < 3; attempt++) {
            String repaired = new String(normalized.getBytes(WINDOWS_1252), StandardCharsets.UTF_8);
            if (repaired.equals(normalized)) {
                break;
            }
            normalized = repaired;
            if (!looksLikeMojibake(normalized)) {
                break;
            }
        }
        return normalized;
    }

    private boolean looksLikeMojibake(String value) {
        return value.contains("Ã")
                || value.contains("Â")
                || value.contains("â€")
                || value.contains("Ð")
                || value.contains("Ñ");
    }

    private String escapeHtml(String value) {
        return value
                .replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;");
    }
}
