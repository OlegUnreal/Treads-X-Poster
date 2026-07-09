package com.behindthesmile.posting.api;

import com.behindthesmile.posting.model.QueuedPost;
import com.behindthesmile.posting.service.AppPathService;
import com.behindthesmile.posting.service.ChromeProfileLauncherService;
import com.behindthesmile.posting.service.SocialPostingService;
import com.behindthesmile.posting.service.YoutubePlaybackService;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api")
@CrossOrigin(origins = {
        "http://localhost:4200",
        "http://127.0.0.1:4200",
        "http://167.233.93.6:4301"
})
public class DashboardController {
    private final SocialPostingService socialPostingService;
    private final com.behindthesmile.posting.service.PostingJobService postingJobService;
    private final AppPathService appPathService;
    private final YoutubePlaybackService youtubePlaybackService;
    private final ChromeProfileLauncherService chromeProfileLauncherService;

    public DashboardController(
            SocialPostingService socialPostingService,
            com.behindthesmile.posting.service.PostingJobService postingJobService,
            AppPathService appPathService,
            YoutubePlaybackService youtubePlaybackService,
            ChromeProfileLauncherService chromeProfileLauncherService
    ) {
        this.socialPostingService = socialPostingService;
        this.postingJobService = postingJobService;
        this.appPathService = appPathService;
        this.youtubePlaybackService = youtubePlaybackService;
        this.chromeProfileLauncherService = chromeProfileLauncherService;
    }

    @GetMapping("/health")
    public Map<String, Object> health() {
        Map<String, Object> health = new java.util.LinkedHashMap<>();
        health.put("status", "ok");
        health.put("storage", appPathService.healthDetails());
        return health;
    }

    @GetMapping("/summary")
    public DashboardSummary summary() throws Exception {
        DashboardSummary summary = socialPostingService.getDashboardSummary();
        return new DashboardSummary(
                summary.queueReady(),
                summary.threadsReady(),
                summary.xReady(),
                summary.postingStatus(),
                summary.lastDailyMessage(),
                summary.lastThreadsMessage(),
                socialPostingService.getPublisherAccountSummary(),
                postingJobService.status()
        );
    }

    @GetMapping("/accounts")
    public AccountSelectionResponse accounts() {
        return socialPostingService.getAccounts();
    }

    @GetMapping("/accounts/config")
    public List<AccountConfigResponse> accountConfigs() {
        return socialPostingService.getAccountConfigs();
    }

    @PostMapping("/accounts/config")
    public AccountConfigResponse createAccountConfig(@RequestBody AccountConfigRequest request) throws Exception {
        return socialPostingService.saveAccountConfig(request);
    }

    @PutMapping("/accounts/config/{id}")
    public AccountConfigResponse updateAccountConfig(
            @PathVariable String id,
            @RequestBody AccountConfigRequest request
    ) throws Exception {
        AccountConfigRequest normalized = new AccountConfigRequest(
                id,
                request.label(),
                request.prompt(),
                request.language(),
                request.defaultPostCount(),
                request.xPrompt(),
                request.xLanguage(),
                request.xDefaultPostCount(),
                request.xAccountLabel(),
                request.xAccessToken(),
                request.xClientId(),
                request.xClientSecret(),
                request.xRedirectUri(),
                request.xScopes(),
                request.xApiKey(),
                request.xApiSecret(),
                request.xAccessTokenSecret(),
                request.xRefreshToken(),
                request.xPublishMode(),
                request.xBrowser(),
                request.xBrowserProfileDir(),
                request.xBrowserHeadless(),
                request.threadsPrompt(),
                request.threadsLanguage(),
                request.threadsDefaultPostCount(),
                request.threadsAccountLabel(),
                request.threadsAccessToken(),
                request.threadsUserId(),
                request.threadsAppId(),
                request.threadsAppSecret(),
                request.threadsRedirectUri()
        );
        return socialPostingService.saveAccountConfig(normalized);
    }

    @DeleteMapping("/accounts/config/{id}")
    public ActionResult deleteAccountConfig(@PathVariable String id) throws Exception {
        socialPostingService.deleteAccountConfig(id);
        return new ActionResult(true, "delete-account", "Account removed from UI-managed settings.");
    }

    @PostMapping("/accounts/config/threads/lookup")
    public ThreadsProfileLookupResponse lookupThreadsProfile(@RequestBody AccountConfigRequest request) throws Exception {
        return socialPostingService.lookupThreadsProfile(request);
    }

    @GetMapping("/accounts/workspace")
    public AccountWorkspaceSummary accountWorkspace() throws Exception {
        return socialPostingService.getAccountWorkspaceSummary();
    }

    @PutMapping("/accounts/active")
    public AccountSelectionResponse switchActiveAccount(@RequestBody ActiveAccountRequest request) throws Exception {
        return socialPostingService.switchActiveAccount(request.accountId());
    }

    @GetMapping("/queue")
    public List<QueuedPost> queue(
            @RequestParam(value = "platform", required = false) String platform,
            @RequestParam(value = "accountId", required = false) String accountId
    ) throws Exception {
        return socialPostingService.getQueue(platform, accountId);
    }

    @PostMapping("/queue")
    public QueuedPost createQueuePost(@RequestBody QueuePostUpsertRequest request) throws Exception {
        return socialPostingService.createQueuedPost(request);
    }

    @PutMapping("/queue/{id}")
    public QueuedPost updateQueuePost(
            @PathVariable String id,
            @RequestParam(value = "platform", required = false) String platform,
            @RequestParam(value = "accountId", required = false) String accountId,
            @RequestBody QueuePostUpsertRequest request
    ) throws Exception {
        return socialPostingService.updateQueuedPost(id, platform, accountId, request);
    }

    @DeleteMapping("/queue/{id}")
    public ActionResult deleteQueuePost(
            @PathVariable String id,
            @RequestParam(value = "platform", required = false) String platform,
            @RequestParam(value = "accountId", required = false) String accountId
    ) {
        return socialPostingService.deleteQueuedPost(id, platform, accountId);
    }

    @PostMapping("/queue/{id}/move/{direction}")
    public ActionResult moveQueuePost(
            @PathVariable String id,
            @PathVariable String direction,
            @RequestParam(value = "platform", required = false) String platform,
            @RequestParam(value = "accountId", required = false) String accountId
    ) {
        return socialPostingService.moveQueuedPost(id, direction, platform, accountId);
    }

    @PostMapping("/queue/{id}/publish")
    public ActionResult publishQueuePost(
            @PathVariable String id,
            @RequestParam(value = "platform", required = false) String platform,
            @RequestParam(value = "accountId", required = false) String accountId
    ) {
        return socialPostingService.publishQueuedPost(id, platform, accountId);
    }

    @PostMapping("/queue/clean-duplicate-images")
    public ActionResult cleanDuplicateQueueImages(
            @RequestParam(value = "platform", required = false) String platform,
            @RequestParam(value = "accountId", required = false) String accountId
    ) {
        return socialPostingService.clearDuplicateQueueImages(platform, accountId);
    }

    @PostMapping("/queue/fill-missing-photos")
    public ActionResult fillMissingQueuePhotos(
            @RequestParam(value = "platform", required = false) String platform,
            @RequestParam(value = "accountId", required = false) String accountId
    ) {
        return socialPostingService.fillMissingQueuePhotos(platform, accountId);
    }

    @PostMapping("/queue/{id}/mark-published/{platform}")
    public ActionResult markQueuePostPublished(
            @PathVariable String id,
            @PathVariable String platform,
            @RequestParam(value = "accountId", required = false) String accountId
    ) {
        return socialPostingService.markQueuedPostPublishedManually(id, platform, accountId);
    }

    @PostMapping("/generate")
    public GeneratePromptResponse generate(@RequestBody GeneratePromptRequest request) throws Exception {
        return socialPostingService.generateFromCustomPrompt(request);
    }

    @PostMapping("/photo-batch")
    public ActionResult createPhotoBatch(
            @RequestParam("photos") MultipartFile[] photos,
            @RequestParam(value = "prompt", required = false) String prompt,
            @RequestParam(value = "topic", required = false) String topic,
            @RequestParam(value = "language", required = false) String language,
            @RequestParam(value = "platforms", required = false) String platforms,
            @RequestParam(value = "accountIds", required = false) String accountIds,
            @RequestParam(value = "targetProfiles", required = false) String targetProfiles,
            @RequestParam(value = "publishNow", required = false, defaultValue = "false") boolean publishNow
    ) {
        return socialPostingService.createPostsFromUploadedPhotos(
                photos,
                prompt,
                topic,
                language,
                socialPostingService.parsePlatforms(platforms),
                socialPostingService.parseAccountIds(accountIds),
                socialPostingService.parseTargetProfiles(targetProfiles),
                publishNow
        );
    }

    @PostMapping("/actions/daily")
    public ActionResult runDaily() {
        return socialPostingService.runDailyNow();
    }

    @PostMapping("/actions/auto-create")
    public ActionResult runAutoCreate() {
        return socialPostingService.runAutoCreateNow();
    }

    @PostMapping("/actions/publish-thread")
    public ActionResult runPublishThread() {
        return socialPostingService.runPublishThreadNow();
    }

    @PostMapping("/actions/publish-x")
    public ActionResult runPublishX() {
        return socialPostingService.runPublishXNow();
    }

    @PostMapping("/actions/publish-x-browser")
    public ActionResult runPublishXBrowser(@RequestBody(required = false) BrowserXPublishRequest request) {
        return socialPostingService.runPublishXViaBrowser(request);
    }

    @PostMapping("/actions/open-x-login-browser")
    public ActionResult openXLoginBrowser() {
        return socialPostingService.openXLoginBrowser();
    }

    @PostMapping("/actions/youtube/play")
    public Map<String, Object> playYoutube(@RequestBody YoutubePlaybackRequest request) throws Exception {
        return youtubePlaybackService.play(request);
    }

    @PostMapping("/actions/youtube/stop")
    public Map<String, Object> stopYoutube() {
        return youtubePlaybackService.stop();
    }

    @GetMapping("/actions/youtube/status")
    public Map<String, Object> youtubeStatus() {
        return youtubePlaybackService.status();
    }

    @GetMapping(value = "/actions/youtube/screenshot", produces = MediaType.IMAGE_PNG_VALUE)
    public ResponseEntity<byte[]> youtubeScreenshot() {
        return ResponseEntity.ok()
                .contentType(MediaType.IMAGE_PNG)
                .body(youtubePlaybackService.screenshot());
    }

    @PostMapping("/actions/chrome-profiles/start-all")
    public Map<String, Object> startAllChromeProfiles(@RequestBody(required = false) ChromeProfilesLaunchRequest request) throws Exception {
        return chromeProfileLauncherService.startAll(request);
    }

    @GetMapping("/actions/chrome-profiles/status")
    public Map<String, Object> chromeProfileStatus() throws Exception {
        return chromeProfileLauncherService.status();
    }

    @GetMapping(value = "/actions/chrome-profiles/profiles-env", produces = MediaType.TEXT_PLAIN_VALUE)
    public ResponseEntity<String> chromeProfilesEnv(
            @RequestHeader(value = "X-Profiles-Env-Token", required = false) String token
    ) throws Exception {
        if (!profilesEnvTokenAllowed(token, "PROFILES_ENV_DOWNLOAD_TOKEN")) {
            return ResponseEntity.status(403).body("Forbidden");
        }
        return ResponseEntity.ok()
                .contentType(MediaType.TEXT_PLAIN)
                .body(chromeProfileLauncherService.profilesEnvContent());
    }

    @PutMapping(value = "/actions/chrome-profiles/profiles-env", consumes = MediaType.TEXT_PLAIN_VALUE)
    public ResponseEntity<Map<String, Object>> updateChromeProfilesEnv(
            @RequestHeader(value = "X-Profiles-Env-Token", required = false) String token,
            @RequestBody String content
    ) throws Exception {
        if (!profilesEnvTokenAllowed(token, "PROFILES_ENV_UPLOAD_TOKEN")) {
            return ResponseEntity.status(403).body(Map.of("error", "Forbidden"));
        }
        return ResponseEntity.ok(chromeProfileLauncherService.updateProfilesEnvContent(content));
    }

    private boolean profilesEnvTokenAllowed(String token, String envName) {
        String expectedToken = System.getenv(envName);
        if (expectedToken == null || expectedToken.isBlank()) {
            expectedToken = System.getenv("PROFILES_ENV_SYNC_TOKEN");
        }
        return expectedToken == null || expectedToken.isBlank() || expectedToken.equals(token);
    }

    @PostMapping("/actions/chrome-profiles/check-url")
    public Map<String, Object> checkChromeProfilesUrl(@RequestBody(required = false) ChromeProfilesUrlCheckRequest request) throws Exception {
        return chromeProfileLauncherService.checkUrl(request);
    }

    @PostMapping("/actions/chrome-profiles/check-url/start")
    public Map<String, Object> startChromeProfilesUrlCheck(@RequestBody(required = false) ChromeProfilesUrlCheckRequest request) throws Exception {
        return chromeProfileLauncherService.startUrlCheck(request);
    }

    @GetMapping("/actions/chrome-profiles/check-url/status")
    public Map<String, Object> chromeProfilesUrlCheckStatus() {
        return chromeProfileLauncherService.currentUrlCheckStatus();
    }

    @PostMapping("/actions/chrome-profiles/bulk")
    public Map<String, Object> bulkChromeProfiles(@RequestBody(required = false) ChromeProfilesBulkActionRequest request) throws Exception {
        return chromeProfileLauncherService.bulkAction(request);
    }

    @PutMapping("/actions/chrome-profiles/{profileName}/login-status")
    public Map<String, Object> updateChromeProfileLoginStatus(
            @PathVariable String profileName,
            @RequestBody ChromeProfileLoginStatusRequest request
    ) throws Exception {
        return chromeProfileLauncherService.updateLoginStatus(profileName, request);
    }

    @PutMapping("/actions/chrome-profiles/{profileName}/proxy-capability")
    public Map<String, Object> updateChromeProfileProxyCapability(
            @PathVariable String profileName,
            @RequestBody ChromeProfileProxyCapabilityRequest request
    ) throws Exception {
        return chromeProfileLauncherService.updateProxyCapability(profileName, request);
    }

    @PostMapping("/actions/chrome-profiles/{profileName}/focus")
    public Map<String, Object> focusChromeProfile(@PathVariable String profileName) throws Exception {
        return chromeProfileLauncherService.focusProfile(profileName);
    }

    @PostMapping("/actions/chrome-profiles/{profileName}/close")
    public Map<String, Object> closeChromeProfile(@PathVariable String profileName) throws Exception {
        return chromeProfileLauncherService.closeProfile(profileName);
    }

    @PostMapping("/actions/chrome-profiles/{profileName}/restart")
    public Map<String, Object> restartChromeProfile(
            @PathVariable String profileName,
            @RequestBody(required = false) ChromeProfileActionRequest request
    ) throws Exception {
        return chromeProfileLauncherService.restartProfile(profileName, request);
    }

    @PostMapping("/actions/chrome-profiles/{profileName}/login")
    public Map<String, Object> openChromeProfileLogin(@PathVariable String profileName) throws Exception {
        return chromeProfileLauncherService.openLoginProfile(profileName);
    }

    @PostMapping("/actions/attach-open-images")
    public ActionResult attachOpenImages() {
        return socialPostingService.attachImagesToReadyQueue();
    }

    @GetMapping("/jobs")
    public List<QueuePostingJobSummary> queueJobs() {
        return postingJobService.list();
    }

    @PostMapping("/jobs")
    public QueuePostingJobSummary createQueueJob(@RequestBody QueuePostingJobRequest request) {
        return postingJobService.create(request);
    }

    @PutMapping("/jobs/{id}")
    public QueuePostingJobSummary updateQueueJob(
            @PathVariable String id,
            @RequestBody QueuePostingJobRequest request
    ) {
        return postingJobService.update(id, request);
    }

    @DeleteMapping("/jobs/{id}")
    public ActionResult deleteQueueJob(@PathVariable String id) {
        return postingJobService.delete(id);
    }

    @PostMapping("/jobs/{id}/start")
    public ActionResult startQueueJob(@PathVariable String id) {
        return postingJobService.start(id);
    }

    @PostMapping("/jobs/{id}/stop")
    public ActionResult stopQueueJob(@PathVariable String id) {
        return postingJobService.stop(id);
    }

    @GetMapping("/job")
    public PostingJobStatus jobStatus() {
        return postingJobService.status();
    }

    @PostMapping("/job/start")
    public ActionResult startJob(@RequestBody(required = false) PostingJobRequest request) {
        return postingJobService.start(request);
    }

    @PutMapping("/job")
    public ActionResult updateJob(@RequestBody(required = false) PostingJobRequest request) {
        return postingJobService.update(request);
    }

    @PostMapping("/job/stop")
    public ActionResult stopJob() {
        return postingJobService.stop();
    }
}
