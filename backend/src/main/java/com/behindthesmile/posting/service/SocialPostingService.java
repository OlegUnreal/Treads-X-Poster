package com.behindthesmile.posting.service;

import com.behindthesmile.posting.api.ActionResult;
import com.behindthesmile.posting.api.AccountSelectionResponse;
import com.behindthesmile.posting.api.BrowserXPublishRequest;
import com.behindthesmile.posting.api.DashboardSummary;
import com.behindthesmile.posting.api.GeneratePromptRequest;
import com.behindthesmile.posting.api.GeneratePromptResponse;
import com.behindthesmile.posting.api.PublisherAccountOption;
import com.behindthesmile.posting.api.PublisherAccountSummary;
import com.behindthesmile.posting.api.QueuePostUpsertRequest;
import com.behindthesmile.posting.config.AppProperties;
import com.behindthesmile.posting.model.ContentPlan;
import com.behindthesmile.posting.model.GeneratedPostDraft;
import com.behindthesmile.posting.model.QueuedPost;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicBoolean;

@Service
public class SocialPostingService {
    private static final Logger log = LoggerFactory.getLogger(SocialPostingService.class);

    private final AppProperties appProperties;
    private final OpenAiService openAiService;
    private final DraftService draftService;
    private final QueueService queueService;
    private final OpenSourceImageService openSourceImageService;
    private final ThreadsService threadsService;
    private final XService xService;
    private final XBrowserAutomationService xBrowserAutomationService;
    private final AppPathService appPathService;
    private final AccountConfigService accountConfigService;
    private final MediaStorageService mediaStorageService;
    private final AtomicBoolean autoCreateRunning = new AtomicBoolean(false);

    public SocialPostingService(
            AppProperties appProperties,
            OpenAiService openAiService,
            DraftService draftService,
            QueueService queueService,
            OpenSourceImageService openSourceImageService,
            ThreadsService threadsService,
            XService xService,
            XBrowserAutomationService xBrowserAutomationService,
            AppPathService appPathService,
            AccountConfigService accountConfigService,
            MediaStorageService mediaStorageService
    ) {
        this.appProperties = appProperties;
        this.openAiService = openAiService;
        this.draftService = draftService;
        this.queueService = queueService;
        this.openSourceImageService = openSourceImageService;
        this.threadsService = threadsService;
        this.xService = xService;
        this.xBrowserAutomationService = xBrowserAutomationService;
        this.appPathService = appPathService;
        this.accountConfigService = accountConfigService;
        this.mediaStorageService = mediaStorageService;
    }

    public String draft(Map<String, String> args, boolean publish) throws Exception {
        String topic = args.getOrDefault("topic", appProperties.defaults().topic());
        int count = parseInt(args.get("count"), appProperties.defaults().count());
        String tone = args.getOrDefault("tone", appProperties.defaults().tone());
        String language = args.getOrDefault("language", appProperties.defaults().language());
        String platforms = args.getOrDefault("platforms", "x,threads");

        List<GeneratedPostDraft> posts = enrichDraftsWithUniqueImages(openAiService.generateDrafts(topic, tone, language, count), topic);
        Path savedPath = draftService.saveDraft(posts, topic, appPathService.draftPath());
        int selectedIndex = Math.max(parseInt(args.get("index"), 1) - 1, 0);
        String selectedPost = selectedIndex < posts.size()
                ? posts.get(selectedIndex).getText()
                : posts.getFirst().getText();

        StringBuilder output = new StringBuilder()
                .append("Generated ").append(posts.size()).append(" post option(s).").append(System.lineSeparator())
                .append("Saved drafts to ").append(savedPath.toAbsolutePath()).append(System.lineSeparator())
                .append(System.lineSeparator());

        for (int i = 0; i < posts.size(); i++) {
            output.append(i + 1).append(". ").append(posts.get(i).getText()).append(System.lineSeparator());
            if (posts.get(i).getVisualHint() != null && !posts.get(i).getVisualHint().isBlank()) {
                output.append("   Visual: ").append(posts.get(i).getVisualHint()).append(System.lineSeparator());
            }
        }

        if (publish) {
            List<Object> results = publishPost(selectedPost, platforms, true);
            output.append(System.lineSeparator())
                    .append("Publishing selected post...").append(System.lineSeparator())
                    .append(results);
        }

        return output.toString().trim();
    }

    public String daily(Map<String, String> args) throws Exception {
        Path queuePath = Path.of(args.getOrDefault("queue", appPathService.queuePath().toString()));
        int minimumReady = parseInt(args.get("minimumReady"), 3);
        int threadsPerRun = parseInt(args.get("threadsPerRun"), 1);
        int xPerRun = parseInt(args.get("xPerRun"), 1);
        long readyCount = queueService.countReadyPosts(queuePath, null);
        StringBuilder output = new StringBuilder();

        if (readyCount < minimumReady) {
            output.append("Only ").append(readyCount).append(" ready post(s) found. Creating more.")
                    .append(System.lineSeparator());
            output.append(autoCreate(args)).append(System.lineSeparator());
        } else {
            output.append(readyCount).append(" ready post(s) found. Skipping generation.")
                    .append(System.lineSeparator());
        }

        for (int index = 0; index < threadsPerRun; index++) {
            long threadsReady = queueService.countReadyPosts(queuePath, "threads");
            if (threadsReady == 0) {
                output.append("No ready Threads posts left to publish.").append(System.lineSeparator());
                break;
            }
            try {
                output.append(publishQueuedThreads(Map.of("queue", queuePath.toString(), "index", "1")))
                        .append(System.lineSeparator());
            } catch (Exception ex) {
                output.append("Threads publish skipped due to error: ").append(ex.getMessage()).append(System.lineSeparator());
                break;
            }
        }

        for (int index = 0; index < xPerRun; index++) {
            long xReadyLoop = queueService.countReadyPosts(queuePath, "x");
            if (xReadyLoop == 0) {
                output.append("No ready X posts left to publish.").append(System.lineSeparator());
                break;
            }
            try {
                output.append(publishQueuedX(Map.of("queue", queuePath.toString(), "index", "1")))
                        .append(System.lineSeparator());
            } catch (Exception ex) {
                output.append("X publish skipped due to error: ").append(ex.getMessage()).append(System.lineSeparator());
                break;
            }
        }

        long xReady = queueService.countReadyPosts(queuePath, "x");
        Path xLinksPath = queueService.saveXComposerLinks(queuePath, appPathService.xLinksPath());
        output.append(xReady).append(" ready X post(s) are available for manual composer posting.")
                .append(System.lineSeparator())
                .append("Saved X composer links to ").append(xLinksPath.toAbsolutePath());

        return output.toString().trim();
    }

    public String buildXLinks(Map<String, String> args) throws Exception {
        Path queuePath = Path.of(args.getOrDefault("queue", appPathService.queuePath().toString()));
        Path savedPath = queueService.saveXComposerLinks(queuePath, appPathService.xLinksPath());
        return "Saved X composer links to " + savedPath.toAbsolutePath();
    }

    public String publishQueuedThreads(Map<String, String> args) throws Exception {
        Path queuePath = Path.of(args.getOrDefault("queue", appPathService.queuePath().toString()));
        QueuedPost selectedPost = queueService.selectQueuedPost(
                queuePath,
                "threads",
                parseInt(args.get("index"), 1),
                "No ready queued Threads posts found."
        );

        Map<String, Object> result = threadsService.publishToThreads(selectedPost.getText(), selectedPost.getImageUrl());
        queueService.markQueuedPostPublished(queuePath, selectedPost.getId(), "threads", result);
        return "Published to Threads: " + result;
    }

    public String publishQueuedX(Map<String, String> args) throws Exception {
        Path queuePath = Path.of(args.getOrDefault("queue", appPathService.queuePath().toString()));
        QueuedPost selectedPost = queueService.selectQueuedPost(
                queuePath,
                "x",
                parseInt(args.get("index"), 1),
                "No ready queued X posts found."
        );

        Map<String, Object> result = publishXWithConfiguredMode(selectedPost.getText(), selectedPost.getImageUrl());
        queueService.markQueuedPostPublished(queuePath, selectedPost.getId(), "x", result);
        return "Published to X: " + result;
    }

    public String autoCreate(Map<String, String> args) throws Exception {
        Path planPath = Path.of(args.getOrDefault("plan", appPathService.contentPlanPath().toString()));
        List<ContentPlan.Item> items = queueService.readContentPlan(planPath);
        int total = 0;
        StringBuilder output = new StringBuilder();
        Set<String> usedImageUrls = readUsedImageUrls();

        for (ContentPlan.Item item : items) {
            String topic = item.getTopic() != null ? item.getTopic() : appProperties.defaults().topic();
            String tone = item.getTone() != null ? item.getTone() : appProperties.defaults().tone();
            String language = item.getLanguage() != null ? item.getLanguage() : appProperties.defaults().language();
            int count = item.getCount() != null ? item.getCount() : appProperties.defaults().count();
            List<String> platforms = item.getPlatforms() != null ? item.getPlatforms() : List.of("x", "threads");

            List<GeneratedPostDraft> posts = enrichDraftsWithUniqueImages(
                    openAiService.generateDrafts(topic, tone, language, count),
                    topic,
                    usedImageUrls
            );
            Path savedPath = queueService.saveQueuedPosts(
                    posts,
                    topic,
                    tone,
                    language,
                    platforms,
                    appPathService.queuePath()
            );

            total += posts.size();
            output.append("Queued ").append(posts.size()).append(" post(s) for topic: ").append(topic).append(System.lineSeparator())
                    .append("Saved queue to ").append(savedPath.toAbsolutePath()).append(System.lineSeparator());
        }

        output.append("Auto-created ").append(total).append(" queued post(s).");
        return output.toString().trim();
    }

    public DashboardSummary getDashboardSummary() throws Exception {
        long queueReady = queueService.countReadyPosts(appPathService.queuePath(), null);
        long threadsReady = queueService.countReadyPosts(appPathService.queuePath(), "threads");
        long xReady = queueService.countReadyPosts(appPathService.queuePath(), "x");

        String postingStatus = queueReady > 0 ? "running" : "stopped";
        String lastDailyMessage = queueReady > 0
                ? queueReady + " ready post(s) currently available."
                : "Queue is empty. Run generation to refill it.";
        String lastThreadsMessage = threadsReady > 0
                ? threadsReady + " Threads-ready post(s) waiting in queue."
                : "No Threads-ready posts waiting right now.";

        return new DashboardSummary(
                queueReady,
                threadsReady,
                xReady,
                postingStatus,
                lastDailyMessage,
                lastThreadsMessage,
                getPublisherAccountSummary(),
                null
        );
    }

    public PublisherAccountSummary getPublisherAccountSummary() {
        PublisherAccountOption active = accountConfigService.activeAccountOption();
        return new PublisherAccountSummary(
                active.id(),
                active.label(),
                resolveXAccountLabel(),
                active.xModeLabel(),
                resolveThreadsAccountLabel(),
                accountConfigService.accountOptions()
        );
    }

    public AccountSelectionResponse getAccounts() {
        return accountConfigService.selection();
    }

    public AccountSelectionResponse switchActiveAccount(String accountId) throws Exception {
        return accountConfigService.switchActiveAccount(accountId);
    }

    public List<QueuedPost> getQueue() throws Exception {
        return queueService.readQueuedPosts(appPathService.queuePath());
    }

    public ActionResult runDailyNow() {
        return executeAction("daily", () -> daily(Map.of("threadsPerRun", "1", "xPerRun", "1", "minimumReady", "8")));
    }

    public ActionResult runAutoCreateNow() {
        if (!autoCreateRunning.compareAndSet(false, true)) {
            return new ActionResult(false, "auto-create", "Post generation is already running. Wait for it to finish before starting another one.");
        }

        CompletableFuture.runAsync(() -> {
            try {
                String result = autoCreate(Map.of());
                log.info("Background auto-create completed: {}", result);
            } catch (Exception ex) {
                log.warn("Background auto-create failed: {}", ex.getMessage(), ex);
            } finally {
                autoCreateRunning.set(false);
            }
        });

        return new ActionResult(true, "auto-create", "Post generation started in the background. Refresh the dashboard in a minute.");
    }

    public ActionResult runPublishThreadNow() {
        return executeAction("publish-queued-threads", () -> publishQueuedThreads(Map.of("index", "1")));
    }

    public ActionResult runPublishXNow() {
        return executeAction("publish-queued-x", () -> publishQueuedX(Map.of("index", "1")));
    }

    public ActionResult runPublishXViaBrowser(BrowserXPublishRequest request) {
        return executeAction("publish-x-via-selenium", () -> {
            String queuePostId = request == null ? null : request.queuePostId();
            String requestedText = request == null ? null : request.text();
            String requestedImageUrl = request == null ? null : request.imageUrl();
            boolean markPublished = request == null || request.markPublished() == null || request.markPublished();

            String text = firstNonBlank(requestedText, "");
            String imageUrl = firstNonBlank(requestedImageUrl, "");
            if ((text == null || text.isBlank()) && queuePostId != null && !queuePostId.isBlank()) {
                QueuedPost selected = queueService.readQueuedPosts(appPathService.queuePath()).stream()
                        .filter(post -> queuePostId.equals(post.getId()))
                        .findFirst()
                        .orElseThrow(() -> new IllegalStateException("Queued X post not found: " + queuePostId));
                text = selected.getText();
                imageUrl = firstNonBlank(selected.getImageUrl(), "");
            }

            Map<String, Object> result = xBrowserAutomationService.publishToX(text, imageUrl);
            if (queuePostId != null && !queuePostId.isBlank() && markPublished) {
                queueService.markQueuedPostPublished(appPathService.queuePath(), queuePostId, "x", result);
            }
            return "Published to X with Selenium: " + result;
        });
    }

    public ActionResult openXLoginBrowser() {
        return executeAction("open-x-login-browser", () -> {
            Map<String, Object> result = xBrowserAutomationService.openLoginBrowser();
            return "Opened X Selenium login browser: " + result;
        });
    }

    public ActionResult attachImagesToReadyQueue() {
        return executeAction("attach-open-images", () -> {
            List<QueuedPost> posts = queueService.readQueuedPosts(appPathService.queuePath());
            int updatedCount = 0;
            Set<String> usedImageUrls = readUsedImageUrls();

            for (QueuedPost post : posts) {
                if (!"ready".equals(post.getStatus())) {
                    continue;
                }
                if (post.getImageUrl() != null && !post.getImageUrl().isBlank()) {
                    continue;
                }

                GeneratedPostDraft draft = new GeneratedPostDraft();
                draft.setText(post.getText());
                draft.setVisualHint(post.getVisualHint());
                GeneratedPostDraft enriched = openSourceImageService.enrichDraftWithOpenImage(draft, post.getTopic(), usedImageUrls);
                if (enriched.getImageUrl() == null || enriched.getImageUrl().isBlank()) {
                    continue;
                }

                String originalImageUrl = enriched.getImageUrl();
                String storedImageUrl;
                try {
                    storedImageUrl = mediaStorageService.storeRemoteQueueImage(originalImageUrl, post.getId());
                } catch (Exception ex) {
                    log.warn("Could not store queue image for post {}: {}", post.getId(), ex.getMessage());
                    usedImageUrls.add(originalImageUrl);
                    continue;
                }
                post.setImageOriginalUrl(originalImageUrl);
                post.setImageUrl(storedImageUrl);
                post.setImageSourcePage(enriched.getImageSourcePage());
                post.setImageAttribution(enriched.getImageAttribution());
                post.setImageLicense(enriched.getImageLicense());
                usedImageUrls.add(originalImageUrl);
                usedImageUrls.add(storedImageUrl);
                updatedCount++;
            }

            queueService.writeQueuedPosts(posts, appPathService.queuePath());
            return updatedCount == 0
                    ? "No ready posts were updated with open-source images."
                    : "Attached open-source images to " + updatedCount + " ready post(s).";
        });
    }

    public String publishQueuedXViaBrowser(Map<String, String> args) throws Exception {
        Path queuePath = Path.of(args.getOrDefault("queue", appPathService.queuePath().toString()));
        QueuedPost selectedPost = queueService.selectQueuedPost(
                queuePath,
                "x",
                parseInt(args.get("index"), 1),
                "No ready queued X posts found."
        );

        Map<String, Object> result = xBrowserAutomationService.publishToX(selectedPost.getText(), selectedPost.getImageUrl());
        queueService.markQueuedPostPublished(queuePath, selectedPost.getId(), "x", result);
        return "Published to X with Selenium: " + result;
    }

    public QueuedPost updateQueuedPost(String id, QueuePostUpsertRequest request) throws Exception {
        QueuedPost updated = queueService.createQueuedPost(
                request.topic(),
                request.text(),
                request.visualHint(),
                request.imageUrl(),
                request.imageSourcePage(),
                request.imageAttribution(),
                request.imageLicense(),
                request.tone(),
                request.language(),
                request.platforms(),
                request.status()
        );
        return queueService.updateQueuedPost(appPathService.queuePath(), id, updated);
    }

    public ActionResult deleteQueuedPost(String id) {
        return executeAction("delete-queue-post", () -> {
            queueService.deleteQueuedPost(appPathService.queuePath(), id);
            return "Queued post removed.";
        });
    }

    public ActionResult moveQueuedPost(String id, String direction) {
        return executeAction("move-queue-post", () -> {
            int offset = switch (direction == null ? "" : direction.trim().toLowerCase(Locale.ROOT)) {
                case "up" -> -1;
                case "down" -> 1;
                default -> throw new IllegalStateException("Move direction must be up or down.");
            };

            queueService.moveQueuedPost(appPathService.queuePath(), id, offset);
            return offset < 0 ? "Queued post moved up." : "Queued post moved down.";
        });
    }

    public ActionResult clearDuplicateQueueImages() {
        return executeAction("clean-duplicate-images", () -> {
            int clearedCount = queueService.clearDuplicateImages(appPathService.queuePath());
            return clearedCount == 0
                    ? "No duplicate queue photos found."
                    : "Removed duplicate photos from " + clearedCount + " queued post(s).";
        });
    }

    public ActionResult fillMissingQueuePhotos() {
        return executeAction("fill-missing-photos", () -> {
            List<QueuedPost> posts = queueService.readQueuedPosts(appPathService.queuePath());
            Set<String> usedImageUrls = readUsedImageUrls();
            int updatedCount = 0;

            for (QueuedPost post : posts) {
                if (!"ready".equals(post.getStatus()) || (post.getImageUrl() != null && !post.getImageUrl().isBlank())) {
                    continue;
                }

                GeneratedPostDraft draft = new GeneratedPostDraft();
                draft.setText(post.getText());
                draft.setVisualHint(post.getVisualHint());
                GeneratedPostDraft enriched = openSourceImageService.enrichDraftWithOpenImage(draft, post.getTopic(), usedImageUrls);
                if (enriched.getImageUrl() == null || enriched.getImageUrl().isBlank()) {
                    continue;
                }

                String originalImageUrl = enriched.getImageUrl();
                String storedImageUrl;
                try {
                    storedImageUrl = mediaStorageService.storeRemoteQueueImage(originalImageUrl, post.getId());
                } catch (Exception ex) {
                    log.warn("Could not store queue image for post {}: {}", post.getId(), ex.getMessage());
                    usedImageUrls.add(originalImageUrl);
                    continue;
                }
                post.setImageOriginalUrl(originalImageUrl);
                post.setImageUrl(storedImageUrl);
                post.setImageSourcePage(enriched.getImageSourcePage());
                post.setImageAttribution(enriched.getImageAttribution());
                post.setImageLicense(enriched.getImageLicense());
                usedImageUrls.add(originalImageUrl);
                usedImageUrls.add(storedImageUrl);
                updatedCount++;
            }

            if (updatedCount > 0) {
                queueService.writeQueuedPosts(posts, appPathService.queuePath());
            }
            return updatedCount == 0
                    ? "No missing queue photos were filled."
                    : "Filled missing photos for " + updatedCount + " queued post(s).";
        });
    }

    public QueuedPost createQueuedPost(QueuePostUpsertRequest request) throws Exception {
        if (request.text() == null || request.text().isBlank()) {
            throw new IllegalStateException("Post text is required.");
        }

        QueuedPost post = queueService.createQueuedPost(
                request.topic(),
                request.text(),
                request.visualHint(),
                request.imageUrl(),
                request.imageSourcePage(),
                request.imageAttribution(),
                request.imageLicense(),
                request.tone(),
                request.language(),
                request.platforms(),
                request.status()
        );
        return queueService.appendQueuedPost(appPathService.queuePath(), post);
    }

    public GeneratePromptResponse generateFromCustomPrompt(GeneratePromptRequest request) throws Exception {
        String prompt = buildCustomPrompt(request);
        List<String> posts = openAiService.generatePostsFromPrompt(prompt);

        if (request.saveToQueue()) {
            String topic = firstNonBlank(request.topic(), "Custom prompt");
            String tone = firstNonBlank(request.tone(), appProperties.defaults().tone());
            String language = firstNonBlank(request.language(), appProperties.defaults().language());
            List<String> platforms = request.platforms() == null || request.platforms().isEmpty()
                    ? List.of("x", "threads")
                    : request.platforms();
            List<GeneratedPostDraft> drafts = posts.stream().map(text -> {
                GeneratedPostDraft draft = new GeneratedPostDraft();
                draft.setText(text);
                draft.setVisualHint("Personal photo idea: notebook, headphones, evening window, or a quiet street.");
                return draft;
            }).toList();
            drafts = enrichDraftsWithUniqueImages(drafts, topic, readUsedImageUrls());

            queueService.saveQueuedPosts(
                    drafts,
                    topic,
                    tone,
                    language,
                    platforms,
                    appPathService.queuePath()
            );
        }

        return new GeneratePromptResponse(
                posts,
                request.saveToQueue(),
                request.saveToQueue()
                        ? "Generated " + posts.size() + " post(s) and saved them to the queue."
                        : "Generated " + posts.size() + " post(s)."
        );
    }

    public ActionResult markQueuedPostPublishedManually(String id, String platform) {
        return executeAction("mark-queued-post-published", () -> {
            String normalizedPlatform = platform == null ? "" : platform.trim().toLowerCase(Locale.ROOT);
            if (!List.of("x", "threads").contains(normalizedPlatform)) {
                throw new IllegalStateException("Unsupported platform: " + platform);
            }

            queueService.markQueuedPostPublished(
                    appPathService.queuePath(),
                    id,
                    normalizedPlatform,
                    Map.of("manual", true, "source", "web-composer")
            );
            return "Queued post " + id + " marked as published for " + normalizedPlatform + ".";
        });
    }

    private String buildCustomPrompt(GeneratePromptRequest request) {
        int count = request.count() == null || request.count() < 1 ? 3 : request.count();
        String language = firstNonBlank(request.language(), appProperties.defaults().language());
        String tone = firstNonBlank(request.tone(), appProperties.defaults().tone());
        String topic = firstNonBlank(request.topic(), "Custom prompt");
        String customPrompt = firstNonBlank(request.prompt(), "");

        StringBuilder builder = new StringBuilder();
        builder.append("Create ").append(count).append(" social media post options.").append(System.lineSeparator());
        builder.append("Topic: ").append(topic).append(System.lineSeparator());
        builder.append("Language: ").append(language).append(System.lineSeparator());
        builder.append("Tone: ").append(tone).append(System.lineSeparator());
        builder.append("Each post must be suitable for both X and Threads.").append(System.lineSeparator());
        builder.append("Keep every post under 260 characters.").append(System.lineSeparator());
        builder.append("Write like a real person, not like a campaign or public awareness poster.").append(System.lineSeparator());
        builder.append("Make it feel like a quiet voice-over from a personal vlog.").append(System.lineSeparator());
        builder.append("Prefer short sentences, pauses, and small visual details from ordinary life.").append(System.lineSeparator());
        builder.append("Avoid forcing the phrase Behind The Smile unless explicitly requested.").append(System.lineSeparator());
        builder.append("Do not include direct medical advice beyond encouraging professional care and avoiding self-medication.").append(System.lineSeparator());
        if (!customPrompt.isBlank()) {
            builder.append("Additional prompt instructions:").append(System.lineSeparator());
            builder.append(customPrompt).append(System.lineSeparator());
        }
        builder.append("Return JSON as: {\"posts\":[\"...\"]}");
        return builder.toString();
    }

    private ActionResult executeAction(String command, ServiceAction action) {
        try {
            return new ActionResult(true, command, action.run());
        } catch (Exception ex) {
            return new ActionResult(false, command, ex.getMessage());
        }
    }

    private List<GeneratedPostDraft> enrichDraftsWithUniqueImages(List<GeneratedPostDraft> drafts, String topic) {
        try {
            return enrichDraftsWithUniqueImages(drafts, topic, readUsedImageUrls());
        } catch (Exception ex) {
            log.warn("Could not read existing queue images before enrichment: {}", ex.getMessage());
            return enrichDraftsWithUniqueImages(drafts, topic, new HashSet<>());
        }
    }

    private List<GeneratedPostDraft> enrichDraftsWithUniqueImages(
            List<GeneratedPostDraft> drafts,
            String topic,
            Set<String> usedImageUrls
    ) {
        List<GeneratedPostDraft> enrichedDrafts = new ArrayList<>();
        for (GeneratedPostDraft draft : drafts) {
            GeneratedPostDraft enriched = openSourceImageService.enrichDraftWithOpenImage(draft, topic, usedImageUrls);
            if (enriched.getImageUrl() != null && !enriched.getImageUrl().isBlank()) {
                String originalImageUrl = enriched.getImageUrl();
                try {
                    String storedImageUrl = mediaStorageService.storeRemoteQueueImage(originalImageUrl, topic);
                    enriched.setImageOriginalUrl(originalImageUrl);
                    enriched.setImageUrl(storedImageUrl);
                    usedImageUrls.add(originalImageUrl);
                    usedImageUrls.add(storedImageUrl);
                } catch (Exception ex) {
                    log.warn("Could not store queue image on server: {}", ex.getMessage());
                    usedImageUrls.add(originalImageUrl);
                }
            }
            enrichedDrafts.add(enriched);
        }
        return enrichedDrafts;
    }

    private Set<String> readUsedImageUrls() throws Exception {
        Set<String> usedImageUrls = new HashSet<>();
        for (QueuedPost post : queueService.readQueuedPosts(appPathService.queuePath())) {
            if (post.getImageUrl() != null && !post.getImageUrl().isBlank()) {
                usedImageUrls.add(post.getImageUrl());
            }
            if (post.getImageOriginalUrl() != null && !post.getImageOriginalUrl().isBlank()) {
                usedImageUrls.add(post.getImageOriginalUrl());
            }
        }
        return usedImageUrls;
    }

    private List<Object> publishPost(String text, String platforms, boolean shouldPublish) throws Exception {
        List<String> normalizedPlatforms = queueService.parsePlatforms(platforms);
        if (!shouldPublish) {
            return normalizedPlatforms.stream()
                    .map(platform -> Map.of("platform", platform, "dryRun", true, "text", text))
                    .map(value -> (Object) value)
                    .toList();
        }

        ArrayList<Object> results = new ArrayList<>();
        for (String platform : normalizedPlatforms) {
            if ("x".equals(platform)) {
                results.add(publishXWithConfiguredMode(text, null));
            } else if ("threads".equals(platform)) {
                results.add(threadsService.publishToThreads(text));
            }
        }
        return results;
    }

    private Map<String, Object> publishXWithConfiguredMode(String text, String imageUrl) throws Exception {
        String publishMode = firstNonBlank(accountConfigService.activeAccount().x().publishMode(), "api").trim().toLowerCase(Locale.ROOT);
        return switch (publishMode) {
            case "selenium" -> xBrowserAutomationService.publishToX(text, imageUrl);
            case "auto" -> {
                try {
                    yield xService.publishToX(text);
                } catch (Exception ex) {
                    String message = ex.getMessage() == null ? "" : ex.getMessage().toLowerCase(Locale.ROOT);
                    boolean shouldFallback = message.contains("creditsdepleted")
                            || message.contains("unsupported authentication")
                            || message.contains("unauthorized")
                            || message.contains("forbidden");
                    if (!shouldFallback) {
                        throw ex;
                    }
                    yield xBrowserAutomationService.publishToX(text, imageUrl);
                }
            }
            default -> xService.publishToX(text);
        };
    }

    private int parseInt(String value, int fallback) {
        return value == null || value.isBlank() ? fallback : Integer.parseInt(value);
    }

    private String resolveXAccountLabel() {
        AppProperties.X x = accountConfigService.activeAccount().x();
        if (x.accountLabel() != null && !x.accountLabel().isBlank()) {
            return x.accountLabel().trim();
        }
        if ("selenium".equalsIgnoreCase(firstNonBlank(x.publishMode(), "api"))) {
            return "X account from Selenium browser profile";
        }
        if (x.accessToken() != null || x.apiKey() != null) {
            return "Configured X account";
        }
        return "X account is not configured";
    }

    private String resolveXModeLabel() {
        String publishMode = firstNonBlank(appProperties.x().publishMode(), "api").trim().toLowerCase(Locale.ROOT);
        return switch (publishMode) {
            case "selenium" -> "Posting through Selenium browser";
            case "auto" -> "API first, Selenium fallback";
            default -> "Posting through X API";
        };
    }

    private String resolveThreadsAccountLabel() {
        AppProperties.Threads threads = accountConfigService.activeAccount().threads();
        if (threads.accountLabel() != null && !threads.accountLabel().isBlank()) {
            return threads.accountLabel().trim();
        }
        String fetchedLabel = threadsService.fetchCurrentAccountLabel();
        if (fetchedLabel != null && !fetchedLabel.isBlank()) {
            return fetchedLabel;
        }
        if (threads.userId() != null && !threads.userId().isBlank()) {
            return "Threads user ID ending in " + trailing(threads.userId(), 4);
        }
        if (threads.accessToken() != null && !threads.accessToken().isBlank()) {
            return "Configured Threads account";
        }
        return "Threads account is not configured";
    }

    private String trailing(String value, int size) {
        if (value == null || value.isBlank()) {
            return "unknown";
        }
        return value.length() <= size ? value : value.substring(value.length() - size);
    }

    private String firstNonBlank(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value;
    }

    @FunctionalInterface
    private interface ServiceAction {
        String run() throws Exception;
    }
}
