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

    @PutMapping("/accounts/active")
    public AccountSelectionResponse switchActiveAccount(@RequestBody ActiveAccountRequest request) throws Exception {
        return socialPostingService.switchActiveAccount(request.accountId());
    }

    @GetMapping("/queue")
    public List<QueuedPost> queue(@RequestParam(value = "platform", required = false) String platform) throws Exception {
        return socialPostingService.getQueue(platform);
    }

    @PostMapping("/queue")
    public QueuedPost createQueuePost(@RequestBody QueuePostUpsertRequest request) throws Exception {
        return socialPostingService.createQueuedPost(request);
    }

    @PutMapping("/queue/{id}")
    public QueuedPost updateQueuePost(
            @PathVariable String id,
            @RequestParam(value = "platform", required = false) String platform,
            @RequestBody QueuePostUpsertRequest request
    ) throws Exception {
        return socialPostingService.updateQueuedPost(id, platform, request);
    }

    @DeleteMapping("/queue/{id}")
    public ActionResult deleteQueuePost(@PathVariable String id, @RequestParam(value = "platform", required = false) String platform) {
        return socialPostingService.deleteQueuedPost(id, platform);
    }

    @PostMapping("/queue/{id}/move/{direction}")
    public ActionResult moveQueuePost(
            @PathVariable String id,
            @PathVariable String direction,
            @RequestParam(value = "platform", required = false) String platform
    ) {
        return socialPostingService.moveQueuedPost(id, direction, platform);
    }

    @PostMapping("/queue/clean-duplicate-images")
    public ActionResult cleanDuplicateQueueImages(@RequestParam(value = "platform", required = false) String platform) {
        return socialPostingService.clearDuplicateQueueImages(platform);
    }

    @PostMapping("/queue/fill-missing-photos")
    public ActionResult fillMissingQueuePhotos(@RequestParam(value = "platform", required = false) String platform) {
        return socialPostingService.fillMissingQueuePhotos(platform);
    }

    @PostMapping("/queue/{id}/mark-published/{platform}")
    public ActionResult markQueuePostPublished(@PathVariable String id, @PathVariable String platform) {
        return socialPostingService.markQueuedPostPublishedManually(id, platform);
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
            @RequestParam(value = "tone", required = false) String tone,
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
                tone,
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

    @PostMapping("/actions/chrome-profiles/check-url")
    public Map<String, Object> checkChromeProfilesUrl(@RequestBody(required = false) ChromeProfilesUrlCheckRequest request) throws Exception {
        return chromeProfileLauncherService.checkUrl(request);
    }

    @PostMapping("/actions/attach-open-images")
    public ActionResult attachOpenImages() {
        return socialPostingService.attachImagesToReadyQueue();
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
