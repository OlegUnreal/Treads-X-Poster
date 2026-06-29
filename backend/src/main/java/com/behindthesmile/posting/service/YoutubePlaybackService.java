package com.behindthesmile.posting.service;

import com.behindthesmile.posting.api.YoutubePlaybackRequest;
import com.behindthesmile.posting.config.AppProperties;
import org.openqa.selenium.JavascriptExecutor;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.chrome.ChromeDriver;
import org.openqa.selenium.chrome.ChromeDriverService;
import org.openqa.selenium.chrome.ChromeOptions;
import org.openqa.selenium.edge.EdgeDriver;
import org.openqa.selenium.edge.EdgeOptions;
import org.openqa.selenium.support.ui.WebDriverWait;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.io.File;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

@Service
public class YoutubePlaybackService {
    private static final Logger log = LoggerFactory.getLogger(YoutubePlaybackService.class);

    private final AccountConfigService accountConfigService;
    private final ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor();
    private WebDriver driver;
    private ScheduledFuture<?> stopTask;
    private String currentUrl = "";
    private int currentPercent = 0;

    public YoutubePlaybackService(AccountConfigService accountConfigService) {
        this.accountConfigService = accountConfigService;
    }

    public synchronized Map<String, Object> play(YoutubePlaybackRequest request) throws Exception {
        String url = normalizeYoutubeUrl(request == null ? null : request.url());
        int percent = clampPercent(request == null ? null : request.percent());
        cancelStopTask();
        if (driver == null) {
            driver = createDriver();
        }

        currentUrl = url;
        currentPercent = percent;
        driver.get(url);
        WebDriverWait wait = new WebDriverWait(driver, Duration.ofSeconds(30));
        wait.until(webDriver -> ((JavascriptExecutor) webDriver)
                .executeScript("return Boolean(document.querySelector('video'));")
                .equals(Boolean.TRUE));

        double durationSeconds = readDurationSeconds();
        startVideo(percent);
        if (percent <= 0) {
            pauseVideo();
        } else if (durationSeconds > 0 && percent < 100) {
            long delayMs = Math.max(1000L, Math.round(durationSeconds * percent * 10.0));
            stopTask = scheduler.schedule(this::safePause, delayMs, TimeUnit.MILLISECONDS);
        }

        return Map.of(
                "status", "playing",
                "url", currentUrl,
                "percent", currentPercent,
                "durationSeconds", durationSeconds,
                "browser", accountConfigService.activeAccount().x().browser(),
                "accountId", accountConfigService.activeAccount().id()
        );
    }

    public synchronized Map<String, Object> stop() {
        cancelStopTask();
        safePause();
        return Map.of(
                "status", "stopped",
                "url", currentUrl,
                "percent", currentPercent
        );
    }

    public synchronized Map<String, Object> status() {
        return Map.of(
                "status", driver == null ? "idle" : "open",
                "url", currentUrl,
                "percent", currentPercent
        );
    }

    private WebDriver createDriver() throws Exception {
        AppProperties.X x = accountConfigService.activeAccount().x();
        String browser = x.browser() == null ? "chrome" : x.browser().trim().toLowerCase(Locale.ROOT);
        Path profilePath = Path.of(x.browserProfileDir()).toAbsolutePath().normalize();
        Files.createDirectories(profilePath);

        if ("edge".equals(browser)) {
            EdgeOptions options = new EdgeOptions();
            options.addArguments("--user-data-dir=" + profilePath);
            options.addArguments("--profile-directory=Default");
            options.addArguments("--start-maximized");
            addServerBrowserArguments(options);
            if (x.browserHeadless()) {
                options.addArguments("--headless=new");
            }
            return new EdgeDriver(options);
        }

        ChromeOptions options = new ChromeOptions();
        options.addArguments("--user-data-dir=" + profilePath);
        options.addArguments("--profile-directory=Default");
        options.addArguments("--start-maximized");
        addServerBrowserArguments(options);
        if (x.browserHeadless()) {
            options.addArguments("--headless=new");
        }
        return new ChromeDriver(chromeDriverService(), options);
    }

    private ChromeDriverService chromeDriverService() {
        return new ChromeDriverService.Builder()
                .withLogFile(new File("/tmp/behind-the-smile-youtube-chromedriver.log"))
                .withVerbose(true)
                .build();
    }

    private void addServerBrowserArguments(ChromeOptions options) {
        options.addArguments("--no-sandbox");
        options.addArguments("--disable-dev-shm-usage");
        options.addArguments("--disable-gpu");
        options.addArguments("--no-first-run");
        options.addArguments("--no-default-browser-check");
        options.addArguments("--window-size=1365,900");
        options.addArguments("--remote-allow-origins=*");
    }

    private void addServerBrowserArguments(EdgeOptions options) {
        options.addArguments("--no-sandbox");
        options.addArguments("--disable-dev-shm-usage");
        options.addArguments("--disable-gpu");
        options.addArguments("--no-first-run");
        options.addArguments("--no-default-browser-check");
        options.addArguments("--window-size=1365,900");
        options.addArguments("--remote-allow-origins=*");
    }

    private double readDurationSeconds() {
        Object result = ((JavascriptExecutor) driver).executeScript("""
                const video = document.querySelector('video');
                return video && Number.isFinite(video.duration) ? video.duration : 0;
                """);
        if (result instanceof Number number) {
            return number.doubleValue();
        }
        return 0;
    }

    private void startVideo(int percent) {
        ((JavascriptExecutor) driver).executeScript("""
                const video = document.querySelector('video');
                if (!video) return;
                video.currentTime = 0;
                if (arguments[0] > 0) {
                  const play = video.play();
                  if (play && typeof play.catch === 'function') play.catch(() => {});
                }
                """, percent);
    }

    private void pauseVideo() {
        if (driver == null) {
            return;
        }
        ((JavascriptExecutor) driver).executeScript("""
                const video = document.querySelector('video');
                if (video) video.pause();
                """);
    }

    private synchronized void safePause() {
        try {
            pauseVideo();
        } catch (Exception ex) {
            log.warn("Could not pause YouTube playback: {}", ex.getMessage());
        }
    }

    private void cancelStopTask() {
        if (stopTask != null) {
            stopTask.cancel(false);
            stopTask = null;
        }
    }

    private String normalizeYoutubeUrl(String rawUrl) {
        if (rawUrl == null || rawUrl.isBlank()) {
            throw new IllegalStateException("YouTube URL is required.");
        }
        URI uri = URI.create(rawUrl.trim());
        String host = uri.getHost() == null ? "" : uri.getHost().toLowerCase(Locale.ROOT);
        if (!host.equals("youtube.com")
                && !host.endsWith(".youtube.com")
                && !host.equals("youtu.be")
                && !host.endsWith(".youtu.be")) {
            throw new IllegalStateException("Only YouTube URLs are supported.");
        }
        String separator = rawUrl.contains("?") ? "&" : "?";
        return rawUrl + separator + "autoplay=1";
    }

    private int clampPercent(Integer percent) {
        if (percent == null) {
            return 100;
        }
        return Math.max(0, Math.min(100, percent));
    }
}
