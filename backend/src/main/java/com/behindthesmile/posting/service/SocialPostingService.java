package com.behindthesmile.posting.service;

import com.behindthesmile.posting.api.ActionResult;
import com.behindthesmile.posting.api.AccountConfigRequest;
import com.behindthesmile.posting.api.AccountConfigResponse;
import com.behindthesmile.posting.api.AccountSelectionResponse;
import com.behindthesmile.posting.api.AccountWorkspaceAccount;
import com.behindthesmile.posting.api.AccountWorkspaceSummary;
import com.behindthesmile.posting.api.BrowserXPublishRequest;
import com.behindthesmile.posting.api.DashboardSummary;
import com.behindthesmile.posting.api.GeneratePromptRequest;
import com.behindthesmile.posting.api.GeneratePromptResponse;
import com.behindthesmile.posting.api.PublisherAccountOption;
import com.behindthesmile.posting.api.PublisherAccountSummary;
import com.behindthesmile.posting.api.QueuePostUpsertRequest;
import com.behindthesmile.posting.api.ThreadsProfileLookupResponse;
import com.behindthesmile.posting.config.AppProperties;
import com.behindthesmile.posting.model.ContentPlan;
import com.behindthesmile.posting.model.GeneratedPostDraft;
import com.behindthesmile.posting.model.QueuedPost;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashSet;
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
        String language = args.getOrDefault("language", appProperties.defaults().language());
        String platforms = args.getOrDefault("platforms", "x,threads");

        List<GeneratedPostDraft> posts = enrichDraftsWithUniqueImages(openAiService.generateDrafts(topic, language, count), topic);
        draftService.saveDraft(posts, topic, appPathService.draftPath());
        int selectedIndex = Math.max(parseInt(args.get("index"), 1) - 1, 0);
        String selectedPost = selectedIndex < posts.size()
                ? posts.get(selectedIndex).getText()
                : posts.getFirst().getText();

        StringBuilder output = new StringBuilder()
                .append("Generated ").append(posts.size()).append(" post option(s).").append(System.lineSeparator())
                .append("Saved drafts to database.").append(System.lineSeparator())
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
        int minimumReady = parseInt(args.get("minimumReady"), 3);
        int threadsPerRun = parseInt(args.get("threadsPerRun"), 1);
        int xPerRun = parseInt(args.get("xPerRun"), 1);
        long readyCount = queueService.countReadyPosts(activeQueuePath("x"), null)
                + queueService.countReadyPosts(activeQueuePath("threads"), null);
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
            long threadsReady = queueService.countReadyPosts(activeQueuePath("threads"), "threads");
            if (threadsReady == 0) {
                output.append("No ready Threads posts left to publish.").append(System.lineSeparator());
                break;
            }
            try {
                output.append(publishQueuedThreads(Map.of("queue", activeQueuePath("threads").toString(), "index", "1")))
                        .append(System.lineSeparator());
            } catch (Exception ex) {
                output.append("Threads publish skipped due to error: ").append(ex.getMessage()).append(System.lineSeparator());
                break;
            }
        }

        for (int index = 0; index < xPerRun; index++) {
            long xReadyLoop = queueService.countReadyPosts(activeQueuePath("x"), "x");
            if (xReadyLoop == 0) {
                output.append("No ready X posts left to publish.").append(System.lineSeparator());
                break;
            }
            try {
                output.append(publishQueuedX(Map.of("queue", activeQueuePath("x").toString(), "index", "1")))
                        .append(System.lineSeparator());
            } catch (Exception ex) {
                output.append("X publish skipped due to error: ").append(ex.getMessage()).append(System.lineSeparator());
                break;
            }
        }

        long xReady = queueService.countReadyPosts(activeQueuePath("x"), "x");
        Path xLinksPath = queueService.saveXComposerLinks(activeQueuePath("x"), appPathService.xLinksPath());
        output.append(xReady).append(" ready X post(s) are available for manual composer posting.")
                .append(System.lineSeparator())
                .append("Saved X composer links to ").append(xLinksPath.toAbsolutePath());

        return output.toString().trim();
    }

    public String dailyForAllAccounts(Map<String, String> args) throws Exception {
        StringBuilder output = new StringBuilder();
        List<PublisherAccountOption> accounts = accountConfigService.accountOptions();
        if (accounts.isEmpty()) {
            return daily(args);
        }

        for (PublisherAccountOption account : accounts) {
            String result = accountConfigService.withAccount(account.id(), () -> daily(args));
            output.append("Account: ").append(account.label()).append(System.lineSeparator())
                    .append(result)
                    .append(System.lineSeparator())
                    .append(System.lineSeparator());
        }

        return output.toString().trim();
    }

    public String buildXLinks(Map<String, String> args) throws Exception {
        Path queuePath = Path.of(args.getOrDefault("queue", activeQueuePath("x").toString()));
        Path savedPath = queueService.saveXComposerLinks(queuePath, appPathService.xLinksPath());
        return "Saved X composer links to " + savedPath.toAbsolutePath();
    }

    public String publishQueuedThreads(Map<String, String> args) throws Exception {
        Path queuePath = Path.of(args.getOrDefault("queue", activeQueuePath("threads").toString()));
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
        Path queuePath = Path.of(args.getOrDefault("queue", activeQueuePath("x").toString()));
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
        AppProperties.Account activeAccount = accountConfigService.activeAccount();
        String activeAccountLabel = firstNonBlank(activeAccount.label(), activeAccount.id());

        for (ContentPlan.Item item : items) {
            String topic = item.getTopic() != null ? item.getTopic() : appProperties.defaults().topic();
            String language = item.getLanguage() != null ? item.getLanguage() : appProperties.defaults().language();
            int count = item.getCount() != null ? item.getCount() : appProperties.defaults().count();
            List<String> platforms = item.getPlatforms() != null ? item.getPlatforms() : List.of("x", "threads");

            List<GeneratedPostDraft> posts = enrichDraftsWithUniqueImages(
                    openAiService.generateDrafts(topic, language, count),
                    topic,
                    usedImageUrls
            );
            Path savedPath = null;
            for (String platform : normalizePlatformList(platforms)) {
                savedPath = queueService.saveQueuedPosts(
                        posts,
                        topic,
                        language,
                        List.of(platform),
                        activeQueuePath(platform),
                        activeAccount.id(),
                        activeAccountLabel
                );
            }

            total += posts.size();
            output.append("Queued ").append(posts.size()).append(" post(s) for topic: ").append(topic).append(System.lineSeparator())
                    .append("Saved queue to ").append(savedPath.toAbsolutePath()).append(System.lineSeparator());
        }

        output.append("Auto-created ").append(total).append(" queued post(s).");
        return output.toString().trim();
    }

    public DashboardSummary getDashboardSummary() throws Exception {
        if (accountConfigService.accountOptions().isEmpty()) {
            return new DashboardSummary(
                    0,
                    0,
                    0,
                    "stopped",
                    "No accounts configured. Add one in Accounts page.",
                    "No accounts configured.",
                    getPublisherAccountSummary(),
                    null
            );
        }

        long threadsReady = queueService.countReadyPosts(activeQueuePath("threads"), "threads");
        long xReady = queueService.countReadyPosts(activeQueuePath("x"), "x");
        long queueReady = threadsReady + xReady;

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
        java.util.List<PublisherAccountOption> options = accountConfigService.accountOptions();
        if (options.isEmpty()) {
            return new PublisherAccountSummary(
                    "",
                    "No account configured",
                    "No X account",
                    "Posting through browser",
                    "No Threads account",
                    options
            );
        }

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

    public List<AccountConfigResponse> getAccountConfigs() {
        return accountConfigService.accountConfigs();
    }

    public AccountConfigResponse saveAccountConfig(AccountConfigRequest request) throws Exception {
        return accountConfigService.upsertAccount(request);
    }

    public void deleteAccountConfig(String accountId) throws Exception {
        accountConfigService.deleteUiAccount(accountId);
    }

    public ThreadsProfileLookupResponse lookupThreadsProfile(AccountConfigRequest request) throws Exception {
        if (request == null) {
            throw new IllegalStateException("Threads settings are required.");
        }
        return threadsService.lookupProfile(request.threadsAccessToken(), request.threadsUserId());
    }

    public AccountWorkspaceSummary getAccountWorkspaceSummary() throws Exception {
        Map<String, AccountConfigResponse> configsById = accountConfigService.accountConfigs().stream()
                .collect(java.util.stream.Collectors.toMap(
                        AccountConfigResponse::id,
                        config -> config,
                        (first, second) -> second,
                        java.util.LinkedHashMap::new
                ));
        List<AccountWorkspaceAccount> accountSummaries = new ArrayList<>();
        long totalReady = 0;
        long totalFailed = 0;
        long totalPublished = 0;

        for (PublisherAccountOption account : accountConfigService.accountOptions()) {
            AccountConfigResponse config = configsById.get(account.id());
            List<QueuedPost> xPosts = queueService.readQueuedPosts(appPathService.queuePath(account.id(), "x"));
            List<QueuedPost> threadsPosts = queueService.readQueuedPosts(appPathService.queuePath(account.id(), "threads"));
            List<QueuedPost> allPosts = new ArrayList<>();
            allPosts.addAll(xPosts);
            allPosts.addAll(threadsPosts);

            long xReady = countReadyForPlatform(xPosts, "x");
            long threadsReady = countReadyForPlatform(threadsPosts, "threads");
            long xFailed = countStatus(xPosts, "failed");
            long threadsFailed = countStatus(threadsPosts, "failed");
            long published = allPosts.stream().filter(post -> "posted".equals(post.getStatus())).count();
            long mediaAttached = allPosts.stream().filter(post -> post.getImageUrl() != null && !post.getImageUrl().isBlank()).count();
            long textOnly = allPosts.stream().filter(post -> post.getImageUrl() == null || post.getImageUrl().isBlank()).count();

            totalReady += xReady + threadsReady;
            totalFailed += xFailed + threadsFailed;
            totalPublished += published;

            accountSummaries.add(new AccountWorkspaceAccount(
                    account.id(),
                    account.label(),
                    config == null ? "" : config.prompt(),
                    config == null ? appProperties.defaults().language() : config.language(),
                    config == null ? appProperties.defaults().count() : config.defaultPostCount(),
                    account.xAccountLabel(),
                    account.xModeLabel(),
                    isConfiguredLabel(account.xAccountLabel()),
                    xReady,
                    xFailed,
                    account.threadsAccountLabel(),
                    isConfiguredLabel(account.threadsAccountLabel()),
                    threadsReady,
                    threadsFailed,
                    mediaAttached,
                    textOnly,
                    published
            ));
        }

        return new AccountWorkspaceSummary(
                accountConfigService.accountOptions().isEmpty() ? "" : accountConfigService.activeAccount().id(),
                totalReady,
                totalFailed,
                totalPublished,
                accountSummaries
        );
    }

    public AccountSelectionResponse switchActiveAccount(String accountId) throws Exception {
        return accountConfigService.switchActiveAccount(accountId);
    }

    public List<String> parsePlatforms(String platforms) {
        return queueService.parsePlatforms(platforms);
    }

    public List<String> parseAccountIds(String accountIds) {
        if (accountIds == null || accountIds.isBlank()) {
            return List.of();
        }
        return java.util.Arrays.stream(accountIds.split(","))
                .map(String::trim)
                .filter(value -> !value.isBlank())
                .toList();
    }

    public List<String> parseTargetProfiles(String targetProfiles) {
        return parseAccountIds(targetProfiles);
    }

    public String activeAccountId() {
        return accountConfigService.activeAccount().id();
    }

    public AppProperties.Account requireAccountForJobTargets(String accountId) {
        return accountId == null || accountId.isBlank()
                ? accountConfigService.activeAccount()
                : accountConfigService.requireAccount(accountId);
    }

    public String resolveAccountLabel(String accountId) {
        AppProperties.Account account = requireAccountForJobTargets(accountId);
        return firstNonBlank(account.label(), account.id());
    }

    public List<QueuedPost> getQueue(String platform, String accountId) throws Exception {
        return queueService.readQueuedPosts(queuePathForProfile(accountId, platform));
    }

    public List<QueuedPost> getQueue(String platform) throws Exception {
        return queueService.readQueuedPosts(activeQueuePath(normalizeQueuePlatform(platform)));
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
                QueuedPost selected = queueService.readQueuedPosts(activeQueuePath()).stream()
                        .filter(post -> queuePostId.equals(post.getId()))
                        .findFirst()
                        .orElseThrow(() -> new IllegalStateException("Queued X post not found: " + queuePostId));
                text = selected.getText();
                imageUrl = firstNonBlank(selected.getImageUrl(), "");
            }

            Map<String, Object> result = xBrowserAutomationService.publishToX(text, imageUrl);
            if (queuePostId != null && !queuePostId.isBlank() && markPublished) {
                queueService.markQueuedPostPublished(activeQueuePath(), queuePostId, "x", result);
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
            List<QueuedPost> posts = queueService.readQueuedPosts(activeQueuePath());
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

            queueService.writeQueuedPosts(posts, activeQueuePath());
            return updatedCount == 0
                    ? "No ready posts were updated with open-source images."
                    : "Attached open-source images to " + updatedCount + " ready post(s).";
        });
    }

    public String publishQueuedXViaBrowser(Map<String, String> args) throws Exception {
        Path queuePath = Path.of(args.getOrDefault("queue", activeQueuePath().toString()));
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

    public ActionResult publishQueuedPost(String id, String platform, String accountId) {
        return executeAction("publish-queued-post", () ->
                accountConfigService.withAccount(
                        requireAccountForJobTargets(accountId).id(),
                        () -> {
                            String normalizedPlatform = normalizeQueuePlatform(platform);
                            Path queuePath = queuePathForProfile(accountId, normalizedPlatform);
                            QueuedPost selectedPost = queueService.readQueuedPosts(queuePath).stream()
                                    .filter(post -> id != null && id.equals(post.getId()))
                                    .findFirst()
                                    .orElseThrow(() -> new IllegalStateException("Queued post not found: " + id));

                            if (selectedPost.getPublished() != null && selectedPost.getPublished().get(normalizedPlatform) != null) {
                                throw new IllegalStateException("Selected queued post is not ready for publishing.");
                            }

                            Map<String, Object> result = switch (normalizedPlatform) {
                                case "threads" -> threadsService.publishToThreads(selectedPost.getText(), selectedPost.getImageUrl());
                                case "x" -> publishXWithConfiguredMode(selectedPost.getText(), selectedPost.getImageUrl());
                                default -> throw new IllegalStateException("Unsupported platform: " + normalizedPlatform);
                            };
                            queueService.markQueuedPostPublished(queuePath, selectedPost.getId(), normalizedPlatform, result);
                            return "Published post " + id + " to " + normalizedPlatform + ".";
                        }
                )
        );
    }

    public QueuedPost updateQueuedPost(String id, String platform, QueuePostUpsertRequest request) throws Exception {
        return updateQueuedPost(id, platform, null, request);
    }

    public QueuedPost updateQueuedPost(String id, String platform, String accountId, QueuePostUpsertRequest request) throws Exception {
        QueuedPost updated = queueService.createQueuedPost(
                request.topic(),
                request.text(),
                request.visualHint(),
                request.imageUrl(),
                request.imageSourcePage(),
                request.imageAttribution(),
                request.imageLicense(),
                request.language(),
                request.platforms(),
                request.status()
        );
        return queueService.updateQueuedPost(queuePathForProfile(accountId, platform), id, updated);
    }

    public ActionResult deleteQueuedPost(String id, String platform) {
        return deleteQueuedPost(id, platform, null);
    }

    public ActionResult deleteQueuedPost(String id, String platform, String accountId) {
        return executeAction("delete-queue-post", () -> {
            queueService.deleteQueuedPost(queuePathForProfile(accountId, platform), id);
            return "Queued post removed.";
        });
    }

    public ActionResult moveQueuedPost(String id, String direction, String platform) {
        return moveQueuedPost(id, direction, platform, null);
    }

    public ActionResult moveQueuedPost(String id, String direction, String platform, String accountId) {
        return executeAction("move-queue-post", () -> {
            int offset = switch (direction == null ? "" : direction.trim().toLowerCase(Locale.ROOT)) {
                case "up" -> -1;
                case "down" -> 1;
                default -> throw new IllegalStateException("Move direction must be up or down.");
            };

            queueService.moveQueuedPost(queuePathForProfile(accountId, platform), id, offset);
            return offset < 0 ? "Queued post moved up." : "Queued post moved down.";
        });
    }

    public ActionResult clearDuplicateQueueImages(String platform) {
        return clearDuplicateQueueImages(platform, null);
    }

    public ActionResult clearDuplicateQueueImages(String platform, String accountId) {
        return executeAction("clean-duplicate-images", () -> {
            int clearedCount = queueService.clearDuplicateImages(queuePathForProfile(accountId, platform));
            return clearedCount == 0
                    ? "No duplicate queue photos found."
                    : "Removed duplicate photos from " + clearedCount + " queued post(s).";
        });
    }

    public ActionResult fillMissingQueuePhotos(String platform) {
        return fillMissingQueuePhotos(platform, null);
    }

    public ActionResult fillMissingQueuePhotos(String platform, String accountId) {
        return executeAction("fill-missing-photos", () -> {
            Path queuePath = queuePathForProfile(accountId, platform);
            List<QueuedPost> posts = queueService.readQueuedPosts(queuePath);
            Set<String> usedImageUrls = readUsedImageUrls(accountId, platform);
            int updatedCount = 0;
            int missingCount = 0;
            int skippedWithoutMatchCount = 0;
            int skippedStorageErrorCount = 0;
            int aiGeneratedCount = 0;
            int aiErrorCount = 0;
            int aiFillLimit = Math.max(appProperties.openAi().imageFillLimit(), 0);

            for (QueuedPost post : posts) {
                if (!"ready".equals(post.getStatus()) || (post.getImageUrl() != null && !post.getImageUrl().isBlank())) {
                    continue;
                }
                        missingCount++;

                GeneratedPostDraft draft = new GeneratedPostDraft();
                draft.setText(post.getText());
                draft.setVisualHint(post.getVisualHint());
                GeneratedPostDraft enriched = openSourceImageService.enrichDraftWithOpenImage(draft, post.getTopic(), usedImageUrls);
                boolean updated = false;
                if (enriched.getImageUrl() != null && !enriched.getImageUrl().isBlank()) {
                    String originalImageUrl = enriched.getImageUrl();
                    try {
                        String storedImageUrl = mediaStorageService.storeRemoteQueueImage(originalImageUrl, post.getId());
                        post.setImageOriginalUrl(originalImageUrl);
                        post.setImageUrl(storedImageUrl);
                        post.setImageSourcePage(enriched.getImageSourcePage());
                        post.setImageAttribution(enriched.getImageAttribution());
                        post.setImageLicense(enriched.getImageLicense());
                        usedImageUrls.add(originalImageUrl);
                        usedImageUrls.add(storedImageUrl);
                        updated = true;
                    } catch (Exception ex) {
                        log.warn("Could not store queue image for post {}: {}", post.getId(), ex.getMessage());
                        usedImageUrls.add(originalImageUrl);
                        skippedStorageErrorCount++;
                    }
                } else {
                    skippedWithoutMatchCount++;
                }

                if (!updated && aiGeneratedCount < aiFillLimit) {
                    try {
                        OpenAiService.GeneratedImage image = openAiService.generateQueueImage(
                                post.getText(),
                                post.getTopic(),
                                firstNonBlank(post.getVisualHint(), enriched.getVisualHint())
                        );
                        String storedImageUrl = mediaStorageService.storeQueueImageBytes(image.bytes(), image.extension(), post.getId());
                        post.setImageOriginalUrl("");
                        post.setImageUrl(storedImageUrl);
                        post.setImageSourcePage("OpenAI image generation");
                        post.setImageAttribution("AI-generated image from post text");
                        post.setImageLicense("AI-generated");
                        usedImageUrls.add(storedImageUrl);
                        aiGeneratedCount++;
                        updated = true;
                    } catch (Exception ex) {
                        log.warn("Could not generate AI queue image for post {}: {}", post.getId(), ex.getMessage());
                        aiErrorCount++;
                    }
                }

                if (updated) {
                    updatedCount++;
                }
            }

            if (updatedCount > 0) {
                queueService.writeQueuedPosts(posts, queuePathForProfile(accountId, platform));
            }
            if (missingCount == 0) {
                return "No ready posts are missing photos.";
            }
            if (updatedCount == 0) {
                return "Found " + missingCount + " ready post(s) without photos, but no suitable new photos were found.";
            }

            String message = "Filled missing photos for " + updatedCount + " queued post(s).";
            if (aiGeneratedCount > 0) {
                message += " " + aiGeneratedCount + " were AI-generated from the post text.";
            }
            int remainingCount = missingCount - updatedCount;
            if (remainingCount > 0) {
                message += " " + remainingCount + " still need photos";
                if (skippedWithoutMatchCount > 0 || skippedStorageErrorCount > 0 || aiErrorCount > 0) {
                    message += " (" + skippedWithoutMatchCount + " had no suitable match, "
                            + skippedStorageErrorCount + " could not be stored, "
                            + aiErrorCount + " AI generation attempt(s) failed)";
                }
                message += ".";
            }
            return message;
        });
    }

    public ActionResult markQueuedPostPublishedManually(String id, String platform, String accountId) {
        return executeAction("mark-queued-post-published", () -> {
            String normalizedPlatform = normalizeQueuePlatform(platform);
            if (!List.of("x", "threads").contains(normalizedPlatform)) {
                throw new IllegalStateException("Unsupported platform: " + platform);
            }

            queueService.markQueuedPostPublished(
                    queuePathForProfile(accountId, normalizedPlatform),
                    id,
                    normalizedPlatform,
                    Map.of("manual", true, "source", "web-composer")
            );
            return "Queued post " + id + " marked as published for " + normalizedPlatform + ".";
        });
    }

    public QueuedPost createQueuedPost(QueuePostUpsertRequest request) throws Exception {
        if (request.text() == null || request.text().isBlank()) {
            throw new IllegalStateException("Post text is required.");
        }

        List<TargetProfile> targetProfiles = resolveTargetProfiles(
                request.targetProfiles(),
                request.accountIds(),
                request.platforms()
        );
        QueuedPost firstCreated = null;
        for (TargetProfile targetProfile : targetProfiles) {
            AppProperties.Account account = accountConfigService.requireAccount(targetProfile.accountId());
            QueuedPost post = queueService.createQueuedPost(
                    request.topic(),
                    request.text(),
                    request.visualHint(),
                    request.imageUrl(),
                    request.imageSourcePage(),
                    request.imageAttribution(),
                    request.imageLicense(),
                    request.language(),
                    List.of(targetProfile.platform()),
                    request.status(),
                    account.id(),
                    firstNonBlank(account.label(), account.id())
            );
            QueuedPost created = queueService.appendQueuedPost(appPathService.queuePath(account.id(), targetProfile.platform()), post);
            if (firstCreated == null) {
                firstCreated = created;
            }
        }
        return firstCreated;
    }

    public String publishQueuedForAccountOnce(String accountId, String platform, int postsPerRun, int minimumReady) throws Exception {
        return accountConfigService.withAccount(
                requireAccountForJobTargets(accountId).id(),
                () -> {
                    String normalizedPlatform = normalizeQueuePlatform(platform);
                    if (postsPerRun < 1) {
                        return "No posts were published. postsPerRun must be at least 1.";
                    }
                    Path queuePath = queuePathForProfile(accountId, normalizedPlatform);
                    long readyCount = queueService.countReadyPosts(queuePath, normalizedPlatform);
                    if (readyCount < minimumReady) {
                        return "Only " + readyCount + " ready " + normalizedPlatform + " post(s) found. Skipping publication.";
                    }

                    int publishedCount = 0;
                    for (int index = 0; index < postsPerRun; index++) {
                        QueuedPost post = queueService.selectQueuedPost(
                                queuePath,
                                normalizedPlatform,
                                1,
                                "No ready queued " + normalizedPlatform + " posts found."
                        );
                        Map<String, Object> result = switch (normalizedPlatform) {
                            case "threads" -> threadsService.publishToThreads(post.getText(), post.getImageUrl());
                            case "x" -> publishXWithConfiguredMode(post.getText(), post.getImageUrl());
                            default -> throw new IllegalStateException("Unsupported platform: " + normalizedPlatform);
                        };
                        queueService.markQueuedPostPublished(queuePath, post.getId(), normalizedPlatform, result);
                        publishedCount++;
                    }
                    return "Published " + publishedCount + " queued post(s) for account " + resolveAccountLabel(accountId) + " on " + normalizedPlatform + ".";
                }
        );
    }

    public ActionResult createPostsFromUploadedPhotos(
            MultipartFile[] photos,
            String prompt,
            String topic,
            String language,
            List<String> platforms,
            List<String> accountIds,
            List<String> targetProfileIds,
            boolean publishNow
    ) {
        return executeAction("photo-batch", () -> {
            if (photos == null || photos.length == 0) {
                throw new IllegalStateException("Upload at least one photo.");
            }

            String resolvedTopic = firstNonBlank(topic, "Uploaded photo batch");
            String resolvedLanguage = firstNonBlank(language, appProperties.defaults().language());
            List<TargetProfile> targetProfiles = resolveTargetProfiles(targetProfileIds, accountIds, platforms);
            int queuedCount = 0;
            int publishedCount = 0;

            for (MultipartFile photo : photos) {
                if (photo == null || photo.isEmpty()) {
                    continue;
                }
                String contentType = firstNonBlank(photo.getContentType(), "");
                if (!contentType.isBlank() && !contentType.toLowerCase(Locale.ROOT).startsWith("image/")) {
                    throw new IllegalStateException("Only image uploads are supported: " + photo.getOriginalFilename());
                }

                byte[] bytes = photo.getBytes();
                GeneratedPostDraft draft = openAiService.generateDraftForImage(
                        bytes,
                        contentType,
                        photo.getOriginalFilename(),
                        firstNonBlank(prompt, ""),
                        resolvedTopic,
                        resolvedLanguage
                );

                for (TargetProfile targetProfile : targetProfiles) {
                    AppProperties.Account account = accountConfigService.requireAccount(targetProfile.accountId());
                    String platform = targetProfile.platform();
                    QueuedPost post = queueService.createQueuedPost(
                            resolvedTopic,
                            draft.getText(),
                            draft.getVisualHint(),
                            "",
                            "Uploaded photo: " + firstNonBlank(photo.getOriginalFilename(), "untitled"),
                            "User uploaded photo",
                            "User provided",
                            resolvedLanguage,
                            List.of(platform),
                            "ready",
                            account.id(),
                            firstNonBlank(account.label(), account.id())
                    );
                    String imageUrl = mediaStorageService.storeUploadedQueueImage(bytes, photo.getOriginalFilename(), post.getId());
                    post.setImageUrl(imageUrl);
                    post.setImageOriginalUrl("");
                    Path accountQueuePath = appPathService.queuePath(account.id(), platform);
                    queueService.appendQueuedPost(accountQueuePath, post);
                    queuedCount++;

                    if (publishNow) {
                        publishedCount += accountConfigService.withAccount(account.id(), () -> {
                            if ("x".equals(platform)) {
                                Map<String, Object> result = publishXWithConfiguredMode(post.getText(), post.getImageUrl());
                                queueService.markQueuedPostPublished(accountQueuePath, post.getId(), "x", result);
                                return 1;
                            }
                            if ("threads".equals(platform)) {
                                Map<String, Object> result = threadsService.publishToThreads(post.getText(), post.getImageUrl());
                                queueService.markQueuedPostPublished(accountQueuePath, post.getId(), "threads", result);
                                return 1;
                            }
                            return 0;
                        });
                    }
                }
            }

            if (queuedCount == 0) {
                throw new IllegalStateException("No valid photos were uploaded.");
            }

            String message = "Created " + queuedCount + " queued post(s) from uploaded photo(s).";
            if (publishNow) {
                message += " Published " + publishedCount + " platform post(s) using selected account(s).";
            }
            return message;
        });
    }

    public GeneratePromptResponse generateFromCustomPrompt(GeneratePromptRequest request) throws Exception {
        int requestedCount = resolveCustomPromptCount(request);
        List<TargetProfile> targetProfiles = resolveTargetProfiles(
                request.targetProfiles(),
                request.accountIds(),
                request.platforms()
        );
        Set<String> publishedSignatures = collectPublishedPostSignatures(targetProfiles);
        List<String> posts = generateUniquePostsFromPrompt(request, requestedCount, publishedSignatures);
        if (posts.isEmpty()) {
            throw new IllegalStateException("OpenAI returned posts already used for selected account(s) and platforms. Try changing the prompt.");
        }

        if (request.saveToQueue()) {
            String topic = firstNonBlank(request.topic(), "Custom prompt");
            String language = firstNonBlank(request.language(), appProperties.defaults().language());
            List<GeneratedPostDraft> drafts = posts.stream().map(text -> {
                GeneratedPostDraft draft = new GeneratedPostDraft();
                draft.setText(text);
                draft.setVisualHint("Personal photo idea: notebook, headphones, evening window, or a quiet street.");
                return draft;
            }).toList();
            drafts = enrichDraftsWithUniqueImages(drafts, topic, readUsedImageUrls());

            for (TargetProfile targetProfile : targetProfiles) {
                AppProperties.Account account = accountConfigService.requireAccount(targetProfile.accountId());
                queueService.saveQueuedPosts(
                        drafts,
                        topic,
                        language,
                        List.of(targetProfile.platform()),
                        appPathService.queuePath(account.id(), targetProfile.platform()),
                        account.id(),
                        firstNonBlank(account.label(), account.id())
                );
            }
        }

        int generatedCount = posts.size();
        int skippedCount = Math.max(0, requestedCount - generatedCount);
        String message = "Generated " + generatedCount + " post(s)";
        if (skippedCount > 0) {
            message += ", skipped " + skippedCount + " duplicate(s) already published";
        }
        if (request.saveToQueue()) {
            message += " and saved them to selected account queue(s).";
        } else {
            message += ".";
        }

        return new GeneratePromptResponse(
                posts,
                request.saveToQueue(),
                message
        );
    }

    public ActionResult markQueuedPostPublishedManually(String id, String platform) {
        return executeAction("mark-queued-post-published", () -> {
            String normalizedPlatform = platform == null ? "" : platform.trim().toLowerCase(Locale.ROOT);
            if (!List.of("x", "threads").contains(normalizedPlatform)) {
                throw new IllegalStateException("Unsupported platform: " + platform);
            }

            queueService.markQueuedPostPublished(
                    activeQueuePath(normalizedPlatform),
                    id,
                    normalizedPlatform,
                    Map.of("manual", true, "source", "web-composer")
            );
            return "Queued post " + id + " marked as published for " + normalizedPlatform + ".";
        });
    }

    private String buildCustomPrompt(GeneratePromptRequest request) {
        int count = request.count() == null || request.count() < 1 ? 3 : request.count();
        return buildCustomPrompt(request, count);
    }

    private String buildCustomPrompt(GeneratePromptRequest request, int count) {
        String language = firstNonBlank(request.language(), appProperties.defaults().language());
        String topic = firstNonBlank(request.topic(), "Custom prompt");
        String customPrompt = firstNonBlank(request.prompt(), "");

        StringBuilder builder = new StringBuilder();
        builder.append("Create ").append(count).append(" social media post options.").append(System.lineSeparator());
        builder.append("Topic: ").append(topic).append(System.lineSeparator());
        builder.append("Language: ").append(language).append(System.lineSeparator());
        builder.append("Each post must be suitable for both X and Threads.").append(System.lineSeparator());
        builder.append("Keep every post under 260 characters.").append(System.lineSeparator());
        builder.append("Avoid forcing the phrase Behind The Smile unless explicitly requested.").append(System.lineSeparator());
        builder.append("Do not include direct medical advice beyond encouraging professional care and avoiding self-medication.").append(System.lineSeparator());
        if (!customPrompt.isBlank()) {
            builder.append("Additional prompt instructions:").append(System.lineSeparator());
            builder.append(customPrompt).append(System.lineSeparator());
        }
        builder.append("Return JSON as: {\"posts\":[\"...\"]}");
        return builder.toString();
    }

    private List<String> generateUniquePostsFromPrompt(
            GeneratePromptRequest request,
            int requestedCount,
            Set<String> publishedSignatures
    ) throws Exception {
        Set<String> seenSignatures = new HashSet<>();
        List<String> generatedPosts = new ArrayList<>();
        int maxAttempts = Math.max(requestedCount * 3, 6);
        int attempts = 0;

        while (generatedPosts.size() < requestedCount && attempts < maxAttempts) {
            int batchCount = Math.min(requestedCount - generatedPosts.size() + 1, 8);
            String prompt = buildCustomPrompt(request, batchCount);
            List<String> generatedBatch = openAiService.generatePostsFromPrompt(prompt, true);

            for (String post : generatedBatch) {
                String signature = normalizePostSignature(post);
                if (signature.isBlank() || seenSignatures.contains(signature) || publishedSignatures.contains(signature)) {
                    continue;
                }
                generatedPosts.add(post);
                seenSignatures.add(signature);
                if (generatedPosts.size() >= requestedCount) {
                    break;
                }
            }
            attempts++;
            if (generatedBatch.isEmpty()) {
                break;
            }
        }

        return generatedPosts;
    }

    private Set<String> collectPublishedPostSignatures(List<TargetProfile> targetProfiles) throws Exception {
        Set<String> signatures = new HashSet<>();
        for (TargetProfile targetProfile : targetProfiles) {
            List<QueuedPost> posts = queueService.readQueuedPosts(appPathService.queuePath(
                    targetProfile.accountId(),
                    targetProfile.platform()
            ));
            for (QueuedPost post : posts) {
                if (!isPostPublishedForPlatform(post, targetProfile.platform())) {
                    continue;
                }
                String signature = normalizePostSignature(post.getText());
                if (!signature.isBlank()) {
                    signatures.add(signature);
                }
            }
        }
        return signatures;
    }

    private boolean isPostPublishedForPlatform(QueuedPost post, String platform) {
        if ("posted".equals(post.getStatus())) {
            return true;
        }
        Map<String, QueuedPost.PublishedInfo> published = post.getPublished();
        return published != null && published.get(platform) != null;
    }

    private String normalizePostSignature(String value) {
        if (value == null || value.isBlank()) {
            return "";
        }
        return value.toLowerCase(Locale.ROOT)
                .replaceAll("\\p{Z}+", " ")
                .replaceAll("[\\p{Punct}\\p{S}]+", " ")
                .replaceAll("\\s+", " ")
                .trim();
    }

    private int resolveCustomPromptCount(GeneratePromptRequest request) {
        return request.count() == null || request.count() < 1 ? 3 : request.count();
    }

    private ActionResult executeAction(String command, ServiceAction action) {
        try {
            return new ActionResult(true, command, action.run());
        } catch (Exception ex) {
            return new ActionResult(false, command, ex.getMessage());
        }
    }

    private Path activeQueuePath() {
        return activeQueuePath("x");
    }

    private Path activeQueuePath(String platform) {
        AppProperties.Account activeAccount = accountConfigService.activeAccount();
        String normalizedPlatform = normalizeQueuePlatform(platform);
        Path activeQueuePath = appPathService.queuePath(activeAccount.id(), normalizedPlatform);
        migrateLegacyQueueIfNeeded(activeQueuePath, activeAccount, normalizedPlatform);
        return activeQueuePath;
    }

    private Path queuePathForProfile(String accountId, String platform) {
        AppProperties.Account targetAccount = accountId == null || accountId.isBlank()
                ? accountConfigService.activeAccount()
                : accountConfigService.requireAccount(accountId);
        String normalizedPlatform = normalizeQueuePlatform(platform);
        Path queuePath = appPathService.queuePath(targetAccount.id(), normalizedPlatform);
        if ("x".equals(normalizedPlatform)) {
            migrateLegacyQueueIfNeeded(queuePath, targetAccount, normalizedPlatform);
        }
        return queuePath;
    }

    private String normalizeQueuePlatform(String platform) {
        String normalized = platform == null ? "" : platform.trim().toLowerCase(Locale.ROOT);
        return "threads".equals(normalized) ? "threads" : "x";
    }

    private List<String> normalizePlatformList(List<String> platforms) {
        if (platforms == null || platforms.isEmpty()) {
            return List.of("x", "threads");
        }
        List<String> normalized = platforms.stream()
                .map(this::normalizeQueuePlatform)
                .distinct()
                .toList();
        return normalized.isEmpty() ? List.of("x", "threads") : normalized;
    }

    private List<TargetProfile> resolveTargetProfiles(
            List<String> requestedTargetProfiles,
            List<String> requestedAccountIds,
            List<String> requestedPlatforms
    ) {
        if (requestedTargetProfiles != null && !requestedTargetProfiles.isEmpty()) {
            LinkedHashSet<TargetProfile> profiles = new LinkedHashSet<>();
            for (String requestedTargetProfile : requestedTargetProfiles) {
                TargetProfile targetProfile = parseTargetProfile(requestedTargetProfile);
                accountConfigService.requireAccount(targetProfile.accountId());
                profiles.add(targetProfile);
            }
            if (!profiles.isEmpty()) {
                return new ArrayList<>(profiles);
            }
        }

        List<String> targetAccountIds = accountConfigService.resolveTargetAccountIds(requestedAccountIds);
        List<String> targetPlatforms = normalizePlatformList(requestedPlatforms);
        List<TargetProfile> profiles = new ArrayList<>();
        for (String accountId : targetAccountIds) {
            for (String platform : targetPlatforms) {
                profiles.add(new TargetProfile(accountId, platform));
            }
        }
        return profiles;
    }

    private TargetProfile parseTargetProfile(String value) {
        if (value == null || value.isBlank()) {
            throw new IllegalStateException("Target profile is required.");
        }
        String trimmed = value.trim();
        int separator = trimmed.lastIndexOf(':');
        if (separator <= 0 || separator >= trimmed.length() - 1) {
            throw new IllegalStateException("Target profile must use accountId:platform format.");
        }
        String accountId = trimmed.substring(0, separator).trim();
        String platform = normalizeQueuePlatform(trimmed.substring(separator + 1));
        if (accountId.isBlank()) {
            throw new IllegalStateException("Target profile account id is required.");
        }
        return new TargetProfile(accountId, platform);
    }

    private record TargetProfile(String accountId, String platform) {
    }

    private void migrateLegacyQueueIfNeeded(Path activeQueuePath, AppProperties.Account activeAccount, String platform) {
        try {
            String firstAccountId = appProperties.accounts().isEmpty() ? "default" : appProperties.accounts().getFirst().id();
            Path legacyQueuePath = appPathService.queuePath();
            if (!activeAccount.id().equals(firstAccountId)
                    || !"x".equals(platform)
                    || activeQueuePath.equals(legacyQueuePath)
                    || Files.exists(activeQueuePath)
                    || !Files.exists(legacyQueuePath)) {
                return;
            }

            List<QueuedPost> legacyPosts = queueService.readQueuedPosts(legacyQueuePath);
            if (legacyPosts.isEmpty()) {
                return;
            }

            String accountLabel = firstNonBlank(activeAccount.label(), activeAccount.id());
            for (QueuedPost post : legacyPosts) {
                post.setAccountId(activeAccount.id());
                post.setAccountLabel(accountLabel);
            }
            queueService.writeQueuedPosts(legacyPosts, activeQueuePath);
            log.info("Migrated {} legacy queued post(s) to account queue {}", legacyPosts.size(), activeQueuePath);
        } catch (Exception ex) {
            log.warn("Could not migrate legacy queue to account queue: {}", ex.getMessage());
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
        return readUsedImageUrls(null, null);
    }

    private Set<String> readUsedImageUrls(String accountId, String platform) throws Exception {
        Set<String> usedImageUrls = new HashSet<>();
        List<QueuedPost> posts = new ArrayList<>();
        if (accountId == null || accountId.isBlank()) {
            accountId = accountConfigService.activeAccount().id();
        } else {
            accountConfigService.requireAccount(accountId);
        }

        if (platform == null || platform.isBlank()) {
            posts.addAll(queueService.readQueuedPosts(queuePathForProfile(accountId, "x")));
            posts.addAll(queueService.readQueuedPosts(queuePathForProfile(accountId, "threads")));
        } else {
            posts.addAll(queueService.readQueuedPosts(queuePathForProfile(accountId, platform)));
        }

        for (QueuedPost post : posts) {
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

    private long countReadyForPlatform(List<QueuedPost> posts, String platform) {
        return posts.stream()
                .filter(post -> "ready".equals(post.getStatus()))
                .filter(post -> {
                    List<String> platforms = post.getPlatforms() == null ? List.of() : post.getPlatforms();
                    boolean published = post.getPublished() != null && post.getPublished().get(platform) != null;
                    return platforms.contains(platform) && !published;
                })
                .count();
    }

    private long countStatus(List<QueuedPost> posts, String status) {
        return posts.stream()
                .filter(post -> status.equals(post.getStatus()))
                .count();
    }

    private boolean isConfiguredLabel(String label) {
        String normalized = label == null ? "" : label.trim().toLowerCase(Locale.ROOT);
        return !normalized.isBlank() && !normalized.contains("not configured");
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
        AppProperties.Account account = accountConfigService.activeAccount();
        AppProperties.Threads threads = account.threads();
        if (threads.accountLabel() != null && !threads.accountLabel().isBlank()) {
            return threads.accountLabel().trim();
        }
        String fetchedLabel = threadsService.fetchCurrentAccountLabel();
        if (fetchedLabel != null && !fetchedLabel.isBlank()) {
            return fetchedLabel;
        }
        if (threads.userId() != null && !threads.userId().isBlank()) {
            return firstNonBlank(account.label(), account.id());
        }
        if (threads.accessToken() != null && !threads.accessToken().isBlank()) {
            return firstNonBlank(account.label(), "Configured Threads account");
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
