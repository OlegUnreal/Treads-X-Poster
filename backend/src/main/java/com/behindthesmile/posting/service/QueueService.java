package com.behindthesmile.posting.service;

import com.behindthesmile.posting.config.AppProperties;
import com.behindthesmile.posting.model.ContentPlan;
import com.behindthesmile.posting.model.GeneratedPostDraft;
import com.behindthesmile.posting.model.QueuedPost;
import com.behindthesmile.posting.persistence.QueuedPostEntity;
import com.behindthesmile.posting.persistence.QueuedPostRepository;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

@Service
public class QueueService {
    private static final Charset WINDOWS_1252 = Charset.forName("windows-1252");
    private final QueuedPostRepository queuedPostRepository;
    private final ObjectMapper objectMapper;
    private final AppProperties appProperties;

    public QueueService(QueuedPostRepository queuedPostRepository, ObjectMapper objectMapper, AppProperties appProperties) {
        this.queuedPostRepository = queuedPostRepository;
        this.objectMapper = objectMapper;
        this.appProperties = appProperties;
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
            String language,
            List<String> platforms,
            Path filePath
    ) throws IOException {
        return saveQueuedPosts(posts, topic, language, platforms, filePath, null, null);
    }

    public Path saveQueuedPosts(
            List<GeneratedPostDraft> posts,
            String topic,
            String language,
            List<String> platforms,
            Path filePath,
            String accountId,
            String accountLabel
    ) throws IOException {
        QueueTarget target = resolveQueueTarget(filePath);
        String resolvedAccountId = target.accountId();
        if (accountId != null && !accountId.isBlank()) {
            resolvedAccountId = accountId.trim();
        }
        String resolvedAccountLabel = firstNonBlank(accountLabel, target.accountLabel());
        if (resolvedAccountId.isBlank()) {
            throw new IllegalStateException("Queue target account id is not known.");
        }

        int nextDisplayOrder = nextDisplayOrder(resolvedAccountId, target.platformScope());
        List<QueuedPost> savedPosts = new ArrayList<>();

        for (GeneratedPostDraft draft : posts) {
            QueuedPost post = createQueuedPostFromDraft(
                    draft,
                    topic,
                    language,
                    platforms,
                    resolvedAccountId,
                    resolvedAccountLabel
            );
            post.setPlatforms((platforms == null || platforms.isEmpty()) ? List.of(target.platformScope()) : platforms.stream()
                    .map(this::normalizeQueuePlatform)
                    .toList());
            post.setPlatforms(post.getPlatforms().stream().filter(Objects::nonNull).distinct().toList());
            saveQueuedPost(post, target.platformScope(), nextDisplayOrder++);
            savedPosts.add(post);
        }

        return target.path().toAbsolutePath();
    }

    public List<QueuedPost> readQueuedPosts(Path filePath) throws IOException {
        QueueTarget target = resolveQueueTarget(filePath);
        ensureLegacyQueueMigrated(target);

        List<QueuedPostEntity> entities;
        if ("all".equals(target.platformScope())) {
            entities = queuedPostRepository.findByAccountIdOrderByDisplayOrderAsc(target.accountId());
        } else {
            entities = queuedPostRepository.findByAccountIdAndPlatformScopeOrderByDisplayOrderAsc(
                    target.accountId(),
                    target.platformScope()
            );
        }
        return entities.stream().map(this::toModel).toList();
    }

    public Path writeQueuedPosts(List<QueuedPost> posts, Path filePath) throws IOException {
        QueueTarget target = resolveQueueTarget(filePath);
        queuedPostRepository.deleteByAccountIdAndPlatformScope(target.accountId(), target.platformScope());

        int displayOrder = 0;
        for (QueuedPost post : posts) {
            if (post == null) {
                continue;
            }
            post.setAccountId(firstNonBlank(post.getAccountId(), target.accountId()));
            post.setAccountLabel(firstNonBlank(post.getAccountLabel(), target.accountLabel()));
            post.setPlatforms(post.getPlatforms() == null || post.getPlatforms().isEmpty()
                    ? List.of(target.platformScope())
                    : post.getPlatforms()
            );
            saveQueuedPost(post, target.platformScope(), displayOrder++);
        }
        return target.path().toAbsolutePath();
    }

    public QueuedPost createQueuedPost(
            String topic,
            String text,
            String visualHint,
            String imageUrl,
            String imageSourcePage,
            String imageAttribution,
            String imageLicense,
            String language,
            List<String> platforms,
            String status
    ) {
        return createQueuedPost(
                topic,
                text,
                visualHint,
                imageUrl,
                imageSourcePage,
                imageAttribution,
                imageLicense,
                language,
                platforms,
                status,
                null,
                null
        );
    }

    public QueuedPost createQueuedPost(
            String topic,
            String text,
            String visualHint,
            String imageUrl,
            String imageSourcePage,
            String imageAttribution,
            String imageLicense,
            String language,
            List<String> platforms,
            String status,
            String accountId,
            String accountLabel
    ) {
        QueuedPost post = new QueuedPost();
        post.setId(createQueueId());
        post.setStatus(status == null || status.isBlank() ? "ready" : status);
        post.setCreatedAt(Instant.now().toString());
        post.setAccountId(accountId);
        post.setAccountLabel(accountLabel);
        post.setTopic(topic);
        post.setLanguage(language);
        List<String> normalizedPlatforms = platforms == null || platforms.isEmpty()
                ? List.of("x", "threads")
                : platforms.stream().map(this::normalizeQueuePlatform).distinct().toList();
        post.setPlatforms(normalizedPlatforms);
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
            if (updatedPost == null) {
                throw new IllegalStateException("Updated post is required.");
            }

            updatedPost.setId(current.getId());
            updatedPost.setCreatedAt(current.getCreatedAt());
            if (updatedPost.getAccountId() == null || updatedPost.getAccountId().isBlank()) {
                updatedPost.setAccountId(current.getAccountId());
            }
            if (updatedPost.getAccountLabel() == null || updatedPost.getAccountLabel().isBlank()) {
                updatedPost.setAccountLabel(current.getAccountLabel());
            }
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

    public int clearDuplicateImages(Path filePath) throws IOException {
        List<QueuedPost> posts = new ArrayList<>(readQueuedPosts(filePath));
        Set<String> seenImageUrls = new LinkedHashSet<>();
        int clearedCount = 0;

        for (QueuedPost post : posts) {
            String imageUrl = post.getImageUrl();
            if (imageUrl == null || imageUrl.isBlank()) {
                continue;
            }

            String normalizedImageUrl = imageUrl.trim();
            if (!seenImageUrls.contains(normalizedImageUrl)) {
                seenImageUrls.add(normalizedImageUrl);
                continue;
            }

            post.setImageUrl("");
            post.setImageSourcePage("");
            post.setImageAttribution("");
            post.setImageLicense("");
            post.setImageOriginalUrl("");
            clearedCount++;
        }

        if (clearedCount > 0) {
            writeQueuedPosts(posts, filePath);
        }
        return clearedCount;
    }

    public QueuedPost appendQueuedPost(Path filePath, QueuedPost post) throws IOException {
        QueueTarget target = resolveQueueTarget(filePath);
        if (post == null) {
            throw new IllegalStateException("Queued post is required.");
        }

        post.setAccountId(firstNonBlank(post.getAccountId(), target.accountId()));
        post.setAccountLabel(firstNonBlank(post.getAccountLabel(), target.accountLabel()));
        post.setId(firstNonBlank(post.getId(), createQueueId()));
        post.setCreatedAt(firstNonBlank(post.getCreatedAt(), Instant.now().toString()));
        post.setPlatforms(post.getPlatforms() == null || post.getPlatforms().isEmpty()
                ? List.of(target.platformScope())
                : post.getPlatforms()
        );

        int displayOrder = nextDisplayOrder(target.accountId(), target.platformScope());
        saveQueuedPost(post, target.platformScope(), displayOrder);
        return post;
    }

    public void markQueuedPostPublished(Path filePath, String id, String platform, Object result) throws IOException {
        QueueTarget target = resolveQueueTarget(filePath);
        String normalizedPlatform = normalizeQueuePlatform(platform);

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
            published.put(normalizedPlatform, info);
            post.setPublished(published);

            List<String> expectedPlatforms = post.getPlatforms() == null ? List.of() : post.getPlatforms();
            boolean expectedHasPlatform = expectedPlatforms.isEmpty() || expectedPlatforms.contains(normalizedPlatform) || "all".equals(normalizedPlatform);
            boolean fullyPublished = expectedHasPlatform
                    && expectedPlatforms.stream().allMatch(p -> published.get(p) != null);
            if (fullyPublished) {
                post.setStatus("posted");
            }

            long accountIndex = queuedPostRepository.findByAccountIdAndPlatformScopeAndStatusOrderByDisplayOrderAsc(
                    target.accountId(),
                    target.platformScope(),
                    "ready"
            ).size();
            if (post.getStatus() == null || post.getStatus().isBlank()) {
                post.setStatus(expectedPlatforms.isEmpty() ? "ready" : post.getStatus());
            }
            saveQueuedPost(post, target.platformScope(), (int) accountIndex);
            break;
        }

        if (!found) {
            throw new IllegalStateException("Queued post not found: " + id);
        }
        writeQueuedPosts(posts, filePath);
    }

    public long countReadyPosts(Path filePath, String platform) throws IOException {
        QueueTarget target = resolveQueueTarget(filePath);
        ensureLegacyQueueMigrated(target);

        List<QueuedPostEntity> readyPosts = queuedPostRepository.findByAccountIdAndPlatformScopeAndStatusOrderByDisplayOrderAsc(
                target.accountId(),
                target.platformScope(),
                "ready"
        );
        if (platform == null || platform.isBlank()) {
            return readyPosts.size();
        }

        return readyPosts.stream()
                .map(this::toModel)
                .filter(post -> {
                    List<String> platforms = post.getPlatforms() == null ? List.of() : post.getPlatforms();
                    Map<String, QueuedPost.PublishedInfo> published = post.getPublished();
                    return (platforms.isEmpty() || platforms.contains(platform)) && (published == null || published.get(platform) == null);
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
        return posts.get(selectedIndex < posts.size() ? selectedIndex : posts.size() - 1);
    }

    public List<String> parsePlatforms(String rawPlatforms) {
        if (rawPlatforms == null || rawPlatforms.isBlank()) {
            return List.of("x", "threads");
        }

        List<String> platforms = java.util.Arrays.stream(rawPlatforms.split(","))
                .map(value -> value.trim().toLowerCase(Locale.ROOT))
                .filter(value -> !value.isBlank())
                .map(this::normalizeQueuePlatform)
                .toList();

        List<String> allowed = List.of("x", "threads");
        List<String> unknown = platforms.stream().filter(value -> !allowed.contains(value)).toList();
        if (!unknown.isEmpty()) {
            throw new IllegalStateException("Unknown platform(s): " + String.join(", ", unknown));
        }

        return platforms.isEmpty() ? List.of("x", "threads") : platforms.stream().distinct().toList();
    }

    private String createQueueId() {
        return System.currentTimeMillis() + "-" + UUID.randomUUID().toString().replace("-", "").substring(0, 12);
    }

    private QueuedPost normalizePostEncoding(QueuedPost post) {
        post.setTopic(normalizeBrokenUtf8(post.getTopic()));
        post.setAccountId(normalizeBrokenUtf8(post.getAccountId()));
        post.setAccountLabel(normalizeBrokenUtf8(post.getAccountLabel()));
        post.setLanguage(normalizeBrokenUtf8(post.getLanguage()));
        post.setText(normalizeBrokenUtf8(post.getText()));
        post.setVisualHint(normalizeBrokenUtf8(post.getVisualHint()));
        post.setImageSourcePage(normalizeBrokenUtf8(post.getImageSourcePage()));
        post.setImageAttribution(normalizeBrokenUtf8(post.getImageAttribution()));
        post.setImageLicense(normalizeBrokenUtf8(post.getImageLicense()));
        post.setImageOriginalUrl(normalizeBrokenUtf8(post.getImageOriginalUrl()));
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
        return value.contains("Ãƒ")
                || value.contains("Ã‚")
                || value.contains("Ã¢â‚¬")
                || value.contains("Ã")
                || value.contains("Ã‘");
    }

    private String escapeHtml(String value) {
        return value
                .replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;");
    }

    private String firstNonBlank(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value;
    }

    private QueueTarget resolveQueueTarget(Path filePath) {
        if (filePath == null) {
            throw new IllegalStateException("Queue path is required.");
        }

        String fileName = filePath.getFileName().toString();
        if ("queue.jsonl".equals(fileName)) {
            String legacyAccount = appProperties.accounts().isEmpty() ? "legacy" : appProperties.accounts().getFirst().id();
            return new QueueTarget(filePath, legacyAccount, "x", firstNonBlank(legacyAccount, "default"));
        }

        String normalized = fileName.toLowerCase(Locale.ROOT);
        if (normalized.endsWith(".jsonl") && normalized.lastIndexOf('-') > 0) {
            String base = normalized.substring(0, normalized.length() - ".jsonl".length());
            int lastDash = base.lastIndexOf('-');
            String account = base.substring(0, lastDash);
            String platform = base.substring(lastDash + 1);
            return new QueueTarget(filePath, account, normalizeQueuePlatform(platform), account);
        }

        throw new IllegalStateException("Queue path does not contain account/platform: " + filePath);
    }

    private int nextDisplayOrder(String accountId, String platformScope) {
        List<QueuedPostEntity> existing = queuedPostRepository.findByAccountIdAndPlatformScopeOrderByDisplayOrderAsc(accountId, platformScope);
        return existing.isEmpty() ? 0 : existing.getLast().getDisplayOrder() + 1;
    }

    private void saveQueuedPost(QueuedPost post, String platformScope, int displayOrder) {
        QueuedPostEntity entity = toEntity(post, platformScope, displayOrder);
        queuedPostRepository.save(entity);
    }

    private QueuedPost toModel(QueuedPostEntity entity) {
        QueuedPost post = new QueuedPost();
        post.setId(entity.getId());
        post.setStatus(entity.getStatus());
        post.setCreatedAt(entity.getCreatedAt());
        post.setAccountId(entity.getAccountId());
        post.setAccountLabel(entity.getAccountLabel());
        post.setTopic(entity.getTopic());
        post.setLanguage(entity.getLanguage());
        post.setText(entity.getText());
        post.setVisualHint(entity.getVisualHint());
        post.setImageUrl(entity.getImageUrl());
        post.setImageSourcePage(entity.getImageSourcePage());
        post.setImageAttribution(entity.getImageAttribution());
        post.setImageLicense(entity.getImageLicense());
        post.setImageOriginalUrl(entity.getImageOriginalUrl());
        post.setPlatforms(splitCommaList(entity.getPlatforms()));
        post.setPublished(parsePublishedMap(entity.getPublished()));
        return normalizePostEncoding(post);
    }

    private QueuedPostEntity toEntity(QueuedPost post, String platformScope, int displayOrder) {
        QueuedPostEntity entity = new QueuedPostEntity();
        entity.setId(post.getId() == null || post.getId().isBlank() ? createQueueId() : post.getId());
        entity.setStatus(firstNonBlank(post.getStatus(), "ready"));
        entity.setCreatedAt(firstNonBlank(post.getCreatedAt(), Instant.now().toString()));
        entity.setAccountId(firstNonBlank(post.getAccountId(), "legacy"));
        entity.setAccountLabel(post.getAccountLabel());
        entity.setTopic(post.getTopic());
        entity.setLanguage(post.getLanguage());
        entity.setPlatforms(joinPlatforms(post.getPlatforms()));
        entity.setText(post.getText());
        entity.setVisualHint(post.getVisualHint());
        entity.setImageUrl(post.getImageUrl());
        entity.setImageSourcePage(post.getImageSourcePage());
        entity.setImageAttribution(post.getImageAttribution());
        entity.setImageLicense(post.getImageLicense());
        entity.setImageOriginalUrl(post.getImageOriginalUrl());
        entity.setPublished(serializePublishedMap(post.getPublished()));
        entity.setDisplayOrder(displayOrder);
        entity.setPlatformScope(firstNonBlank(platformScope, "x"));
        return entity;
    }

    private String joinPlatforms(List<String> platforms) {
        if (platforms == null || platforms.isEmpty()) {
            return "";
        }
        return platforms.stream().filter(value -> value != null && !value.isBlank())
                .map(this::normalizeQueuePlatform)
                .distinct()
                .toList()
                .toString()
                .replaceAll("^\\[|\\]$", "")
                .replace(", ", ",");
    }

    private List<String> splitCommaList(String rawPlatforms) {
        if (rawPlatforms == null || rawPlatforms.isBlank()) {
            return List.of("x");
        }
        return java.util.Arrays.stream(rawPlatforms.split(","))
                .map(String::trim)
                .filter(value -> !value.isBlank())
                .map(this::normalizeQueuePlatform)
                .distinct()
                .toList();
    }

    private String serializePublishedMap(Map<String, QueuedPost.PublishedInfo> published) {
        if (published == null || published.isEmpty()) {
            return null;
        }
        try {
            return objectMapper.writeValueAsString(published);
        } catch (IOException ex) {
            throw new IllegalStateException("Could not serialize published markers: " + ex.getMessage(), ex);
        }
    }

    @SuppressWarnings("unchecked")
    private Map<String, QueuedPost.PublishedInfo> parsePublishedMap(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        try {
            return objectMapper.readValue(raw, new TypeReference<Map<String, QueuedPost.PublishedInfo>>() {});
        } catch (IOException ex) {
            throw new IllegalStateException("Could not parse post publish markers: " + ex.getMessage(), ex);
        }
    }

    private String normalizeQueuePlatform(String platform) {
        String normalized = platform == null ? "" : platform.trim().toLowerCase(Locale.ROOT);
        return "threads".equals(normalized) ? "threads" : "x";
    }

    private QueuedPost createQueuedPostFromDraft(
            GeneratedPostDraft draft,
            String topic,
            String language,
            List<String> platforms,
            String accountId,
            String accountLabel
    ) {
        return createQueuedPost(
                topic,
                draft.getText(),
                draft.getVisualHint(),
                draft.getImageUrl(),
                draft.getImageSourcePage(),
                draft.getImageAttribution(),
                draft.getImageLicense(),
                language,
                platforms,
                "ready",
                accountId,
                accountLabel
        );
    }

    private void ensureLegacyQueueMigrated(QueueTarget target) {
        if (!"queue.jsonl".equals(target.path().getFileName().toString().toLowerCase(Locale.ROOT))) {
            return;
        }
        if (queuedPostRepository.findByAccountIdAndPlatformScopeAndStatusOrderByDisplayOrderAsc(
                target.accountId(),
                target.platformScope(),
                "ready"
        ).size() > 0
                || queuedPostRepository.findByAccountIdAndPlatformScopeAndStatusOrderByDisplayOrderAsc(
                target.accountId(),
                target.platformScope(),
                "posted"
        ).size() > 0) {
            return;
        }

        try {
            if (!Files.exists(target.path())) {
                return;
            }

            List<String> lines = Files.readAllLines(target.path(), StandardCharsets.UTF_8);
            if (lines.isEmpty()) {
                return;
            }
            int nextDisplayOrder = nextDisplayOrder(target.accountId(), target.platformScope());
            for (String line : lines) {
                if (line == null || line.isBlank()) {
                    continue;
                }
                QueuedPost post = objectMapper.readValue(line, QueuedPost.class);
                post.setAccountId(firstNonBlank(post.getAccountId(), target.accountId()));
                post.setAccountLabel(firstNonBlank(post.getAccountLabel(), target.accountLabel()));
                if (post.getPlatforms() == null || post.getPlatforms().isEmpty()) {
                    post.setPlatforms(List.of(target.platformScope()));
                }
                saveQueuedPost(post, target.platformScope(), nextDisplayOrder++);
            }
        } catch (Exception ignored) {
            // Legacy migration is optional. If it fails, continue operating on DB-only data.
        }
    }

    private record QueueTarget(Path path, String accountId, String platformScope, String accountLabel) {
    }
}
