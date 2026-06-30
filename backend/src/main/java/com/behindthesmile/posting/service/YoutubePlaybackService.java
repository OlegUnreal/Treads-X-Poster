package com.behindthesmile.posting.service;

import com.behindthesmile.posting.api.YoutubePlaybackRequest;
import com.behindthesmile.posting.config.AppProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

@Service
public class YoutubePlaybackService {
    private static final Logger log = LoggerFactory.getLogger(YoutubePlaybackService.class);
    private static final Path DESKTOP_LOG = Path.of("/tmp/behind-the-smile-youtube-desktop.log");

    private final AccountConfigService accountConfigService;
    private final ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor();
    private Process browserProcess;
    private ScheduledFuture<?> stopTask;
    private String currentUrl = "";
    private int currentPercent = 0;
    private String lastError = "";
    private Instant lastStartedAt;

    public YoutubePlaybackService(AccountConfigService accountConfigService) {
        this.accountConfigService = accountConfigService;
    }

    public synchronized Map<String, Object> play(YoutubePlaybackRequest request) {
        try {
            String url = normalizeYoutubeUrl(request == null ? null : request.url());
            int percent = clampPercent(request == null ? null : request.percent());
            cancelStopTask();

            currentUrl = url;
            currentPercent = percent;
            lastError = "";
            lastStartedAt = Instant.now();

            AppProperties.X x = accountConfigService.activeAccount().x();
            Path profilePath = Path.of(x.browserProfileDir()).toAbsolutePath().normalize();
            Files.createDirectories(profilePath);

            Process process = startDesktopBrowser(url, profilePath);
            if (!process.waitFor(10, TimeUnit.SECONDS)) {
                process.destroyForcibly();
                throw new IllegalStateException("Desktop browser launcher did not return after 10 seconds.");
            }
            String output = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8).trim();
            if (process.exitValue() != 0) {
                throw new IllegalStateException(output.isBlank() ? "Desktop browser launcher failed." : output);
            }
            browserProcess = process;

            if (percent <= 0) {
                stopTask = scheduler.schedule(this::safePauseDesktop, 5, TimeUnit.SECONDS);
            }

            Map<String, Object> status = status();
            status.put("status", "playing");
            status.put("message", output.isBlank() ? "Desktop Chrome launched." : output);
            return status;
        } catch (Exception ex) {
            lastError = readableError(ex);
            log.warn("Could not start desktop YouTube playback: {}", lastError);
            Map<String, Object> status = status();
            status.put("status", "error");
            status.put("lastError", lastError);
            return status;
        }
    }

    public synchronized Map<String, Object> stop() {
        cancelStopTask();
        safePauseDesktop();
        Map<String, Object> status = status();
        status.put("status", "stopped");
        return status;
    }

    public synchronized Map<String, Object> status() {
        Map<String, Object> status = new LinkedHashMap<>();
        status.put("status", currentUrl.isBlank() ? "idle" : "open");
        status.put("automationMode", "desktop");
        status.put("url", currentUrl);
        status.put("pageUrl", currentUrl);
        status.put("percent", currentPercent);
        status.put("lastError", lastError);
        status.put("lastStartedAt", lastStartedAt == null ? "" : lastStartedAt.toString());
        status.put("browser", accountConfigService.activeAccount().x().browser());
        status.put("accountId", accountConfigService.activeAccount().id());
        status.put("videoPresent", null);
        status.put("paused", null);
        status.put("currentTime", null);
        status.put("durationSeconds", null);
        status.put("logTail", readLogTail());
        return status;
    }

    public synchronized byte[] screenshot() {
        try {
            return captureDesktopScreenshot();
        } catch (Exception ex) {
            lastError = readableError(ex);
            throw new IllegalStateException(lastError, ex);
        }
    }

    private Process startDesktopBrowser(String url, Path profilePath) throws Exception {
        String command = """
                set -euo pipefail
                BROWSER="$(command -v google-chrome || command -v chromium || command -v chromium-browser || true)"
                if [ -z "$BROWSER" ]; then
                  echo "Missing Chrome/Chromium on the server." >&2
                  exit 127
                fi
                nohup "$BROWSER" \
                  --user-data-dir=%s \
                  --profile-directory=Default \
                  --start-maximized \
                  --new-window \
                  --no-sandbox \
                  --disable-dev-shm-usage \
                  --no-first-run \
                  --no-default-browser-check \
                  --window-size=1365,900 \
                  --autoplay-policy=no-user-gesture-required \
                  %s >%s 2>&1 &
                echo "Desktop Chrome launched."
                """.formatted(shellQuote(profilePath.toString()), shellQuote(url), shellQuote(DESKTOP_LOG.toString()));

        ProcessBuilder builder = new ProcessBuilder("bash", "-lc", command)
                .redirectErrorStream(true);
        builder.environment().putIfAbsent("DISPLAY", ":1");
        builder.environment().putIfAbsent("XAUTHORITY", "/root/.Xauthority");
        return builder.start();
    }

    private byte[] captureDesktopScreenshot() throws Exception {
        Path screenshotPath = Files.createTempFile("behind-the-smile-desktop-", ".png");
        try {
            Files.deleteIfExists(screenshotPath);
            String command = """
                    set -euo pipefail
                    OUT=%s
                    capture_ok() {
                      [ -s "$OUT" ]
                    }
                    if command -v gnome-screenshot >/dev/null 2>&1; then
                      gnome-screenshot -f "$OUT" || true
                      capture_ok && exit 0
                    fi
                    if command -v scrot >/dev/null 2>&1; then
                      scrot "$OUT" || true
                      capture_ok && exit 0
                    fi
                    if command -v import >/dev/null 2>&1; then
                      import -window root "$OUT" || true
                      capture_ok && exit 0
                    fi
                    echo "Desktop screenshot failed or produced an empty image." >&2
                    exit 1
                    """.formatted(shellQuote(screenshotPath.toString()));
            ProcessBuilder builder = new ProcessBuilder("bash", "-lc", command)
                    .redirectErrorStream(true);
            builder.environment().putIfAbsent("DISPLAY", ":1");
            builder.environment().putIfAbsent("XAUTHORITY", "/root/.Xauthority");
            Process process = builder.start();
            process.getOutputStream().close();
            if (!process.waitFor(15, TimeUnit.SECONDS)) {
                process.destroyForcibly();
                throw new IllegalStateException("Desktop screenshot did not finish after 15 seconds.");
            }
            String output = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8).trim();
            if (process.exitValue() != 0) {
                throw new IllegalStateException(output.isBlank() ? "Desktop screenshot failed." : output);
            }
            if (Files.size(screenshotPath) <= 0) {
                throw new IllegalStateException("Desktop screenshot produced an empty image.");
            }
            return Files.readAllBytes(screenshotPath);
        } finally {
            Files.deleteIfExists(screenshotPath);
        }
    }

    private void safePauseDesktop() {
        try {
            String command = """
                    if command -v xdotool >/dev/null 2>&1; then
                      xdotool search --onlyvisible --class 'google-chrome|chromium|Chromium' windowactivate --sync key k >/dev/null 2>&1 || true
                    fi
                    """;
            ProcessBuilder builder = new ProcessBuilder("bash", "-lc", command)
                    .redirectErrorStream(true);
            builder.environment().putIfAbsent("DISPLAY", ":1");
            builder.environment().putIfAbsent("XAUTHORITY", "/root/.Xauthority");
            Process process = builder.start();
            process.waitFor(5, TimeUnit.SECONDS);
        } catch (Exception ex) {
            lastError = readableError(ex);
            log.warn("Could not pause desktop playback: {}", lastError);
        }
    }

    private String readLogTail() {
        try {
            if (!Files.isRegularFile(DESKTOP_LOG)) {
                return "";
            }
            byte[] bytes = Files.readAllBytes(DESKTOP_LOG);
            int start = Math.max(0, bytes.length - 4000);
            return new String(bytes, start, bytes.length - start, StandardCharsets.UTF_8);
        } catch (Exception ex) {
            return readableError(ex);
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

    private void cancelStopTask() {
        if (stopTask != null) {
            stopTask.cancel(false);
            stopTask = null;
        }
    }

    private String readableError(Exception ex) {
        String message = ex.getMessage();
        if (message == null || message.isBlank()) {
            message = ex.getClass().getSimpleName();
        }
        return message.length() > 600 ? message.substring(0, 600) : message;
    }

    private String shellQuote(String value) {
        return "'" + value.replace("'", "'\"'\"'") + "'";
    }
}
