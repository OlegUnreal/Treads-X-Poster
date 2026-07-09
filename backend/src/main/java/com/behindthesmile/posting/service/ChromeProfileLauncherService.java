package com.behindthesmile.posting.service;

import com.behindthesmile.posting.api.ChromeProfilesLaunchRequest;
import com.behindthesmile.posting.api.ChromeProfilesBulkActionRequest;
import com.behindthesmile.posting.api.ChromeProfileActionRequest;
import com.behindthesmile.posting.api.ChromeProfileLoginStatusRequest;
import com.behindthesmile.posting.api.ChromeProfileProxyCapabilityRequest;
import com.behindthesmile.posting.api.ChromeProfilesUrlCheckRequest;
import org.springframework.stereotype.Service;

import java.io.BufferedReader;
import java.io.StringReader;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.HashMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.CompletionService;
import java.util.concurrent.ExecutorCompletionService;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
public class ChromeProfileLauncherService {
    private static final Pattern ENV_LINE = Pattern.compile("^([A-Za-z_][A-Za-z0-9_]*)=(.*)$");
    private static final Pattern EMAIL = Pattern.compile("[A-Z0-9._%+-]+@[A-Z0-9.-]+\\.[A-Z]{2,}", Pattern.CASE_INSENSITIVE);
    private static final Pattern FULL_NAME = Pattern.compile("\"(?:full_name|given_name|display_name)\"\\s*:\\s*\"([^\"]+)\"", Pattern.CASE_INSENSITIVE);
    private static final String PROXY_CAPABILITIES_FILE = "proxy-capabilities.tsv";
    private static final Duration DEVTOOLS_STATUS_TIMEOUT = Duration.ofMillis(700);
    private final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(DEVTOOLS_STATUS_TIMEOUT)
            .build();
    private final Object urlCheckLock = new Object();

    private Instant lastStartedAt;
    private Map<String, Object> latestUrlCheckStatus = Map.of(
            "url", "",
            "checking", false,
            "okCount", 0,
            "completedCount", 0,
            "totalCount", 0,
            "results", List.of()
    );
    private ExecutorService activeUrlCheckExecutor;

    public Map<String, Object> startAll(ChromeProfilesLaunchRequest request) throws Exception {
        if (isWindows()) {
            return startWindowsProfiles(request);
        }

        Path profilesDir = profilesDir();
        Path startScript = startScript();
        if (!Files.isDirectory(profilesDir)) {
            throw new IllegalStateException("Chrome proxy profiles directory is missing: " + profilesDir);
        }
        if (!Files.isRegularFile(startScript)) {
            throw new IllegalStateException("Chrome proxy launcher script is missing: " + startScript);
        }

        int minDelay = clampDelay(request == null ? null : request.minDelaySeconds(), 0, 3600);
        int maxDelay = clampDelay(request == null ? null : request.maxDelaySeconds(), minDelay, 3600);
        List<Map<String, String>> allProfiles = readProfiles();
        List<String> selectedProfiles = selectedProfiles(request == null ? null : request.profileNames(), allProfiles);
        selectedProfiles = filterProfilesForCapabilities(selectedProfiles, allProfiles, requireYoutube(request), requirePornhub(request));
        ensureCapabilityFilterMatch(selectedProfiles, requireYoutube(request), requirePornhub(request));
        int maxProfiles = selectedProfiles.isEmpty() ? allProfiles.stream()
                .filter(profile -> !profile.getOrDefault("proxy", "").isBlank() || !profile.getOrDefault("upstreamProxy", "").isBlank())
                .toList()
                .size() : selectedProfiles.size();
        int profileCount = clampProfileCount(request == null ? null : request.profileCount(), maxProfiles);
        String launchUrl = launchUrlForRequest(request);

        ProcessBuilder processBuilder = new ProcessBuilder(
                "bash",
                "-lc",
                "chmod +x ./start-all.sh && nohup ./start-all.sh > ./start-all.log 2>&1 & echo started"
        )
                .directory(profilesDir.toFile())
                .redirectErrorStream(true);
        processBuilder.environment().put("STAGGER_MIN_SECONDS", String.valueOf(minDelay));
        processBuilder.environment().put("STAGGER_MAX_SECONDS", String.valueOf(maxDelay));
        processBuilder.environment().put("PROFILE_LIMIT", String.valueOf(profileCount));
        if (!selectedProfiles.isEmpty()) {
            processBuilder.environment().put("LAUNCH_PROFILE_NAMES", String.join(" ", selectedProfiles));
        }
        if (!launchUrl.isBlank()) {
            processBuilder.environment().put("LAUNCH_URL", launchUrl);
        }
        String referer = cleanReferer(request == null ? null : request.referer());
        if (!referer.isBlank()) {
            processBuilder.environment().put("LAUNCH_REFERER", referer);
        }
        processBuilder.environment().put("VIDEO_QUALITY", cleanVideoQuality(request == null ? null : request.videoQuality()));

        Process process = processBuilder.start();
        return waitForStart(process, minDelay, maxDelay, profileCount, launchUrl, selectedProfiles);
    }

    private Map<String, Object> startWindowsProfiles(ChromeProfilesLaunchRequest request) throws Exception {
        Path profilesDir = profilesDir();
        Path startScript = startScript();
        if (!Files.isRegularFile(startScript)) {
            throw new IllegalStateException("Windows Chrome profile launcher script is missing: " + startScript);
        }

        Files.createDirectories(profilesDir);
        int minDelay = clampDelay(request == null ? null : request.minDelaySeconds(), 0, 3600);
        int maxDelay = clampDelay(request == null ? null : request.maxDelaySeconds(), minDelay, 3600);
        List<Map<String, String>> allProfiles = readProfiles();
        List<String> selectedProfiles = selectedProfiles(request == null ? null : request.profileNames(), allProfiles);
        selectedProfiles = filterProfilesForCapabilities(selectedProfiles, allProfiles, requireYoutube(request), requirePornhub(request));
        ensureCapabilityFilterMatch(selectedProfiles, requireYoutube(request), requirePornhub(request));
        int requestedCount = request == null ? 1 : request.profileCount() == null ? 1 : request.profileCount();
        int availableProfiles = allProfiles.stream()
                .filter(profile -> !profile.getOrDefault("proxy", "").isBlank() || !profile.getOrDefault("upstreamProxy", "").isBlank())
                .toList()
                .size();
        int maxProfiles = selectedProfiles.isEmpty()
                ? Math.max(1, availableProfiles == 0 ? requestedCount : availableProfiles)
                : selectedProfiles.size();
        int profileCount = clampProfileCount(request == null ? null : request.profileCount(), maxProfiles);
        String launchUrl = launchUrlForRequest(request);

        List<String> command = new ArrayList<>();
        command.add("powershell.exe");
        command.add("-NoProfile");
        command.add("-ExecutionPolicy");
        command.add("Bypass");
        command.add("-File");
        command.add(startScript.toString());
        command.add("-Count");
        command.add(String.valueOf(profileCount));
        command.add("-SkipWebShareSync");
        if (!launchUrl.isBlank()) {
            command.add("-Url");
            command.add(launchUrl);
        }
        String referer = cleanReferer(request == null ? null : request.referer());
        if (!referer.isBlank()) {
            command.add("-Referer");
            command.add(referer);
        }
        command.add("-VideoQuality");
        command.add(cleanVideoQuality(request == null ? null : request.videoQuality()));
        if (Boolean.TRUE.equals(request == null ? null : request.loginMode())) {
            command.add("-Mode");
            command.add("login");
        }
        if (minDelay > 0 || maxDelay > 0) {
            command.add("-DelayFrom");
            command.add(String.valueOf(minDelay));
            command.add("-DelayTo");
            command.add(String.valueOf(maxDelay));
        }
        if (!selectedProfiles.isEmpty()) {
            command.add("-Profiles");
            command.add(String.join(",", selectedProfiles));
        }

        Path logFile = logFile();
        ProcessBuilder processBuilder = new ProcessBuilder(command)
                .directory(repoRoot().toFile())
                .redirectOutput(logFile.toFile())
                .redirectErrorStream(true);
        processBuilder.start();

        lastStartedAt = Instant.now();
        Map<String, Object> result = status();
        result.put("message", "started");
        result.put("minDelaySeconds", minDelay);
        result.put("maxDelaySeconds", maxDelay);
        result.put("profileCount", profileCount);
        result.put("url", launchUrl);
        result.put("profileNames", selectedProfiles);
        return result;
    }

    private Map<String, Object> waitForStart(
            Process process,
            int minDelay,
            int maxDelay,
            int profileCount,
            String launchUrl,
            List<String> selectedProfiles
    ) throws Exception {
        boolean finished = process.waitFor(10, TimeUnit.SECONDS);
        String output = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8).trim();
        if (!finished) {
            process.destroyForcibly();
            throw new IllegalStateException("Chrome proxy launcher did not return after 10 seconds.");
        }
        if (process.exitValue() != 0) {
            throw new IllegalStateException("Chrome proxy launcher failed: " + output);
        }

        lastStartedAt = Instant.now();
        Map<String, Object> result = status();
        result.put("message", output.isBlank() ? "started" : output);
        result.put("minDelaySeconds", minDelay);
        result.put("maxDelaySeconds", maxDelay);
        result.put("profileCount", profileCount);
        result.put("url", launchUrl);
        result.put("profileNames", selectedProfiles);
        return result;
    }

    public Map<String, Object> status() throws Exception {
        Map<String, Object> status = new LinkedHashMap<>();
        Path profilesDir = profilesDir();
        Path startScript = startScript();
        Path startProfileScript = startProfileScript();
        Path envFile = envFile();
        List<Map<String, String>> profiles = readProfiles();
        long configuredProfiles = profiles.stream()
                .filter(profile -> !profile.getOrDefault("proxy", "").isBlank() || !profile.getOrDefault("upstreamProxy", "").isBlank())
                .count();
        long loggedInProfiles = profiles.stream()
                .filter(profile -> "true".equalsIgnoreCase(profile.getOrDefault("loggedIn", "false")))
                .count();
        long runningProfiles = profiles.stream()
                .filter(profile -> "true".equalsIgnoreCase(profile.getOrDefault("running", "false")))
                .count();
        status.put("directory", profilesDir.toString());
        status.put("script", startScript.toString());
        status.put("startProfileScript", startProfileScript.toString());
        status.put("envFile", envFile.toString());
        status.put("directoryExists", Files.isDirectory(profilesDir));
        status.put("scriptExists", Files.isRegularFile(startScript));
        status.put("startProfileScriptExists", Files.isRegularFile(startProfileScript));
        status.put("envFileExists", Files.isRegularFile(envFile));
        status.put("profiles", profiles);
        status.put("chromeFound", chromeFound());
        status.put("configuredProfileCount", configuredProfiles);
        status.put("loggedInProfileCount", loggedInProfiles);
        status.put("runningProfileCount", runningProfiles);
        status.put("logExists", Files.isRegularFile(logFile()));
        status.put("lastStartedAt", lastStartedAt == null ? "" : lastStartedAt.toString());
        status.put("logTail", readLogTail());
        return status;
    }

    public Map<String, Object> runtimeStatus() {
        Map<String, Object> status = new LinkedHashMap<>();
        Path profilesDir = profilesDir();
        Path startScript = startScript();
        Path startProfileScript = startProfileScript();
        Path envFile = envFile();
        status.put("directory", profilesDir.toString());
        status.put("script", startScript.toString());
        status.put("startProfileScript", startProfileScript.toString());
        status.put("envFile", envFile.toString());
        status.put("directoryExists", Files.isDirectory(profilesDir));
        status.put("scriptExists", Files.isRegularFile(startScript));
        status.put("startProfileScriptExists", Files.isRegularFile(startProfileScript));
        status.put("envFileExists", Files.isRegularFile(envFile));
        status.put("chromeFound", chromeFound());
        status.put("lastStartedAt", lastStartedAt == null ? "" : lastStartedAt.toString());
        return status;
    }

    public Map<String, Object> checkUrl(ChromeProfilesUrlCheckRequest request) throws Exception {
        String url = normalizeLaunchUrl(request == null ? null : request.url());
        if (url.isBlank()) {
            throw new IllegalArgumentException("URL is required.");
        }

        Map<String, String> env = readEnvFile();
        List<Map<String, String>> profiles = readProfiles();
        List<Map<String, Object>> results = checkUrlForProfiles(env, profiles, url);
        int okCount = 0;
        for (Map<String, Object> result : results) {
            if (Boolean.TRUE.equals(result.get("ok"))) {
                okCount++;
            }
        }

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("url", url);
        response.put("okCount", okCount);
        response.put("totalCount", results.size());
        response.put("results", results);
        return response;
    }

    public Map<String, Object> startUrlCheck(ChromeProfilesUrlCheckRequest request) throws Exception {
        String url = normalizeLaunchUrl(request == null ? null : request.url());
        if (url.isBlank()) {
            throw new IllegalArgumentException("URL is required.");
        }
        Map<String, String> env = readEnvFile();
        List<Map<String, String>> profiles = readProfiles();
        List<Map<String, Object>> initialResults = new ArrayList<>();
        for (Map<String, String> profile : profiles) {
            String profileName = profile.getOrDefault("name", "");
            Map<String, Object> pending = new LinkedHashMap<>();
            pending.put("name", profileName);
            pending.put("proxy", profile.getOrDefault("upstreamProxy", profile.getOrDefault("proxy", "")));
            pending.put("ok", false);
            pending.put("status", "Pending");
            pending.put("statusCode", 0);
            pending.put("location", "");
            pending.put("redirectMarker", "");
            pending.put("reason", "Pending");
            initialResults.add(pending);
        }

        ExecutorService executor = Executors.newFixedThreadPool(checkConcurrency());
        synchronized (urlCheckLock) {
            if (activeUrlCheckExecutor != null) {
                activeUrlCheckExecutor.shutdownNow();
            }
            activeUrlCheckExecutor = executor;
            latestUrlCheckStatus = urlCheckStatus(url, true, 0, 0, profiles.size(), initialResults);
        }

        Thread worker = new Thread(() -> runUrlCheckJob(executor, env, profiles, url), "chrome-profile-url-check");
        worker.setDaemon(true);
        worker.start();
        return currentUrlCheckStatus();
    }

    public Map<String, Object> currentUrlCheckStatus() {
        synchronized (urlCheckLock) {
            return copyUrlCheckStatus(latestUrlCheckStatus);
        }
    }

    public Map<String, Object> bulkAction(ChromeProfilesBulkActionRequest request) throws Exception {
        if (request == null) {
            throw new IllegalArgumentException("Bulk action request is required.");
        }
        String action = request == null || request.action() == null ? "" : request.action().trim().toLowerCase();
        if (!List.of("open", "restart", "close").contains(action)) {
            throw new IllegalArgumentException("Unsupported bulk action: " + action);
        }

        List<Map<String, String>> currentProfiles = readProfiles();
        List<String> selectedProfiles = selectedProfiles(request.profileNames(), currentProfiles);
        selectedProfiles = filterProfilesForCapabilities(selectedProfiles, currentProfiles, requireYoutube(request), requirePornhub(request));
        ensureCapabilityFilterMatch(selectedProfiles, requireYoutube(request), requirePornhub(request));
        if (selectedProfiles.isEmpty()) {
            throw new IllegalArgumentException("Choose at least one profile.");
        }

        List<Map<String, Object>> profileResults = new ArrayList<>();
        List<String> profilesToOpen = new ArrayList<>();
        Map<String, Map<String, String>> currentByName = new HashMap<>();
        for (Map<String, String> profile : currentProfiles) {
            currentByName.put(profile.getOrDefault("name", ""), profile);
        }

        if ("close".equals(action)) {
            for (String profileName : selectedProfiles) {
                int closed = closeProfileProcesses(profileName);
                profileResults.add(profileActionResult(profileName, closed > 0 ? "closed" : "not_running", closed > 0 ? "Closed" : "Was not running"));
            }
            Map<String, Object> result = status();
            result.put("message", "Closed checked profiles.");
            result.put("profileResults", profileResults);
            return result;
        }

        if ("restart".equals(action)) {
            for (String profileName : selectedProfiles) {
                int closed = closeProfileProcesses(profileName);
                profilesToOpen.add(profileName);
                profileResults.add(profileActionResult(profileName, "restart_queued", closed > 0 ? "Closed and queued" : "Queued"));
            }
        } else {
            for (String profileName : selectedProfiles) {
                Map<String, String> profile = currentByName.getOrDefault(profileName, Map.of());
                if ("true".equalsIgnoreCase(profile.getOrDefault("running", "false"))) {
                    profileResults.add(profileActionResult(profileName, "already_running", "Already running"));
                } else {
                    profilesToOpen.add(profileName);
                    profileResults.add(profileActionResult(profileName, "open_queued", "Queued"));
                }
            }
        }

        Map<String, Object> result;
        if (profilesToOpen.isEmpty()) {
            result = status();
            result.put("message", "No profiles needed opening.");
        } else {
            ChromeProfilesLaunchRequest launchRequest = new ChromeProfilesLaunchRequest(
                    request.minDelaySeconds(),
                    request.maxDelaySeconds(),
                    profilesToOpen.size(),
                    request.url(),
                    profilesToOpen,
                    false,
                    cleanReferer(request.referer()),
                    cleanVideoQuality(request.videoQuality()),
                    request.requireYoutube(),
                    request.requirePornhub()
            );
            result = startAll(launchRequest);
            result.put("message", ("restart".equals(action) ? "Restarted " : "Opened ") + profilesToOpen.size() + " checked profile(s).");
        }
        result.put("profileResults", profileResults);
        return result;
    }

    private Map<String, Object> profileActionResult(String profileName, String status, String message) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("name", profileName);
        result.put("status", status);
        result.put("message", message);
        return result;
    }

    private List<Map<String, Object>> checkUrlForProfiles(Map<String, String> env, List<Map<String, String>> profiles, String url) throws Exception {
        int concurrency = checkConcurrency();
        ExecutorService executor = Executors.newFixedThreadPool(concurrency);
        try {
            List<Future<Map<String, Object>>> futures = new ArrayList<>();
            for (Map<String, String> profile : profiles) {
                futures.add(executor.submit(() -> {
                    String profileName = profile.getOrDefault("name", "");
                    String upstreamProxy = env.getOrDefault("UPSTREAM_PROXY_" + profileName, "");
                    String proxy = upstreamProxy.isBlank() ? env.getOrDefault("PROXY_" + profileName, "") : upstreamProxy;
                    return checkUrlWithProxy(profileName, proxy, url);
                }));
            }
            List<Map<String, Object>> results = new ArrayList<>();
            for (Future<Map<String, Object>> future : futures) {
                results.add(future.get());
            }
            return results;
        } finally {
            executor.shutdownNow();
        }
    }

    private void runUrlCheckJob(ExecutorService executor, Map<String, String> env, List<Map<String, String>> profiles, String url) {
        int completedCount = 0;
        int okCount = 0;
        try {
            CompletionService<Map<String, Object>> completion = new ExecutorCompletionService<>(executor);
            for (Map<String, String> profile : profiles) {
                completion.submit(() -> {
                    String profileName = profile.getOrDefault("name", "");
                    String upstreamProxy = env.getOrDefault("UPSTREAM_PROXY_" + profileName, "");
                    String proxy = upstreamProxy.isBlank() ? env.getOrDefault("PROXY_" + profileName, "") : upstreamProxy;
                    return checkUrlWithProxy(profileName, proxy, url);
                });
            }

            for (int index = 0; index < profiles.size(); index++) {
                Map<String, Object> result = completion.take().get();
                completedCount++;
                if (Boolean.TRUE.equals(result.get("ok"))) {
                    okCount++;
                }
                updateUrlCheckProgress(executor, url, true, okCount, completedCount, profiles.size(), result);
            }
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
        } catch (Exception exception) {
            Map<String, Object> failed = new LinkedHashMap<>();
            failed.put("name", "");
            failed.put("proxy", "");
            failed.put("ok", false);
            failed.put("status", "Error");
            failed.put("statusCode", 0);
            failed.put("location", "");
            failed.put("redirectMarker", "");
            failed.put("reason", exception.getMessage() == null ? "Check failed" : exception.getMessage());
            updateUrlCheckProgress(executor, url, true, okCount, completedCount, profiles.size(), failed);
        } finally {
            synchronized (urlCheckLock) {
                if (activeUrlCheckExecutor != executor) {
                    executor.shutdownNow();
                    return;
                }
                Map<String, Object> current = copyUrlCheckStatus(latestUrlCheckStatus);
                latestUrlCheckStatus = urlCheckStatus(
                        url,
                        false,
                        parseInt(String.valueOf(current.getOrDefault("okCount", okCount)), okCount),
                        parseInt(String.valueOf(current.getOrDefault("completedCount", completedCount)), completedCount),
                        profiles.size(),
                        currentResults(current)
                );
                if (activeUrlCheckExecutor == executor) {
                    activeUrlCheckExecutor = null;
                }
            }
            executor.shutdownNow();
        }
    }

    private void updateUrlCheckProgress(ExecutorService executor, String url, boolean checking, int okCount, int completedCount, int totalCount, Map<String, Object> result) {
        synchronized (urlCheckLock) {
            if (activeUrlCheckExecutor != executor) {
                return;
            }
            Map<String, Object> current = copyUrlCheckStatus(latestUrlCheckStatus);
            List<Map<String, Object>> results = currentResults(current);
            String resultName = String.valueOf(result.getOrDefault("name", ""));
            if (!resultName.isBlank()) {
                boolean replaced = false;
                for (int index = 0; index < results.size(); index++) {
                    if (resultName.equals(String.valueOf(results.get(index).getOrDefault("name", "")))) {
                        results.set(index, new LinkedHashMap<>(result));
                        replaced = true;
                        break;
                    }
                }
                if (!replaced) {
                    results.add(new LinkedHashMap<>(result));
                }
            }
            latestUrlCheckStatus = urlCheckStatus(url, checking, okCount, completedCount, totalCount, results);
        }
    }

    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> currentResults(Map<String, Object> status) {
        Object rawResults = status.get("results");
        if (!(rawResults instanceof List<?> rawList)) {
            return new ArrayList<>();
        }
        List<Map<String, Object>> results = new ArrayList<>();
        for (Object item : rawList) {
            if (item instanceof Map<?, ?> rawMap) {
                Map<String, Object> result = new LinkedHashMap<>();
                for (Map.Entry<?, ?> entry : rawMap.entrySet()) {
                    result.put(String.valueOf(entry.getKey()), entry.getValue());
                }
                results.add(result);
            }
        }
        return results;
    }

    private Map<String, Object> urlCheckStatus(String url, boolean checking, int okCount, int completedCount, int totalCount, List<Map<String, Object>> results) {
        Map<String, Object> status = new LinkedHashMap<>();
        status.put("url", url);
        status.put("checking", checking);
        status.put("okCount", okCount);
        status.put("completedCount", completedCount);
        status.put("totalCount", totalCount);
        status.put("results", new ArrayList<>(results));
        return status;
    }

    private Map<String, Object> copyUrlCheckStatus(Map<String, Object> status) {
        return urlCheckStatus(
                String.valueOf(status.getOrDefault("url", "")),
                Boolean.TRUE.equals(status.get("checking")),
                parseInt(String.valueOf(status.getOrDefault("okCount", "0")), 0),
                parseInt(String.valueOf(status.getOrDefault("completedCount", "0")), 0),
                parseInt(String.valueOf(status.getOrDefault("totalCount", "0")), 0),
                currentResults(status)
        );
    }

    private int checkConcurrency() {
        String value = System.getenv("CHECK_CONCURRENCY");
        int parsed = parseInt(value == null ? "" : value, 5);
        return Math.max(1, Math.min(20, parsed));
    }

    private int checkTimeoutSeconds() {
        String value = System.getenv("CHECK_TIMEOUT_SECONDS");
        int parsed = parseInt(value == null ? "" : value, 15);
        return Math.max(3, Math.min(60, parsed));
    }

    public String profilesEnvContent() throws Exception {
        Path envFile = envFile();
        if (!Files.isRegularFile(envFile)) {
            throw new IllegalStateException("profiles.env is missing: " + envFile);
        }
        return Files.readString(envFile, StandardCharsets.UTF_8);
    }

    public Map<String, Object> updateProfilesEnvContent(String content) throws Exception {
        if (content == null || !content.contains("PROFILE_NAMES=")) {
            throw new IllegalArgumentException("profiles.env must contain PROFILE_NAMES.");
        }
        if (!content.contains("UPSTREAM_PROXY_") && !content.contains("PROXY_")) {
            throw new IllegalArgumentException("profiles.env must contain proxy settings.");
        }

        Path envFile = envFile();
        writeEnvFile(envFile, content);

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("message", "profiles.env updated");
        result.put("envFile", envFile.toString());
        result.put("profiles", readProfiles());
        return result;
    }

    public Map<String, Object> updateLoginStatus(String profileName, ChromeProfileLoginStatusRequest request) throws Exception {
        String cleanProfileName = profileName == null ? "" : profileName.trim();
        if (cleanProfileName.isBlank() || !cleanProfileName.matches("[A-Za-z0-9_-]+")) {
            throw new IllegalArgumentException("Invalid profile name.");
        }
        boolean loggedIn = request != null && Boolean.TRUE.equals(request.loggedIn());
        Map<String, String> env = readEnvFile();
        List<String> profileNames = List.of(env.getOrDefault("PROFILE_NAMES", "").trim().split("\\s+"));
        if (!profileNames.contains(cleanProfileName)) {
            throw new IllegalArgumentException("Unknown profile: " + cleanProfileName);
        }

        Path envFile = envFile();
        String content = Files.isRegularFile(envFile) ? Files.readString(envFile, StandardCharsets.UTF_8) : "";
        String updated = upsertEnvValue(content, "LOGIN_STATUS_" + cleanProfileName, loggedIn ? "logged_in" : "not_logged_in");
        writeEnvFile(envFile, updated);

        Map<String, Object> result = status();
        result.put("message", cleanProfileName + " marked " + (loggedIn ? "logged in" : "not logged in"));
        return result;
    }

    public Map<String, Object> updateProxyCapability(String profileName, ChromeProfileProxyCapabilityRequest request) throws Exception {
        String cleanProfileName = requireKnownProfile(profileName);
        Map<String, String> env = readEnvFile();
        String proxyKey = proxyKeyForProfile(env, cleanProfileName);
        if (proxyKey.isBlank()) {
            throw new IllegalArgumentException("Profile has no proxy host: " + cleanProfileName);
        }

        Map<String, ProxyCapability> capabilities = readProxyCapabilities();
        ProxyCapability current = capabilities.getOrDefault(proxyKey, new ProxyCapability(false, false));
        boolean youtube = request == null || request.youtube() == null ? current.youtube() : Boolean.TRUE.equals(request.youtube());
        boolean pornhub = request == null || request.pornhub() == null ? current.pornhub() : Boolean.TRUE.equals(request.pornhub());
        capabilities.put(proxyKey, new ProxyCapability(youtube, pornhub));
        writeProxyCapabilities(capabilities);

        Map<String, Object> result = status();
        result.put("message", cleanProfileName + " proxy marked " + proxyCapabilityLabel(youtube, pornhub));
        return result;
    }

    public Map<String, Object> focusProfile(String profileName) throws Exception {
        String cleanProfileName = requireKnownProfile(profileName);
        boolean focused = focusProfileWindow(cleanProfileName);
        Map<String, Object> result = status();
        result.put("message", focused ? "Focused " + cleanProfileName : cleanProfileName + " is not running.");
        return result;
    }

    public Map<String, Object> closeProfile(String profileName) throws Exception {
        String cleanProfileName = requireKnownProfile(profileName);
        int closed = closeProfileProcesses(cleanProfileName);
        Map<String, Object> result = status();
        result.put("message", closed > 0 ? "Closed " + cleanProfileName : cleanProfileName + " was not running.");
        return result;
    }

    public Map<String, Object> restartProfile(String profileName, ChromeProfileActionRequest request) throws Exception {
        String cleanProfileName = requireKnownProfile(profileName);
        closeProfileProcesses(cleanProfileName);
        ChromeProfilesLaunchRequest launchRequest = new ChromeProfilesLaunchRequest(
                0,
                0,
                1,
                request == null ? null : request.url(),
                List.of(cleanProfileName),
                false,
                cleanReferer(request == null ? null : request.referer()),
                cleanVideoQuality(request == null ? null : request.videoQuality()),
                false,
                false
        );
        Map<String, Object> result = startAll(launchRequest);
        result.put("message", "Restarted " + cleanProfileName);
        return result;
    }

    public Map<String, Object> openLoginProfile(String profileName) throws Exception {
        String cleanProfileName = requireKnownProfile(profileName);
        ChromeProfilesLaunchRequest launchRequest = new ChromeProfilesLaunchRequest(
                0,
                0,
                1,
                null,
                List.of(cleanProfileName),
                true,
                "",
                "auto",
                false,
                false
        );
        Map<String, Object> result = startAll(launchRequest);
        result.put("message", "Opened login mode for " + cleanProfileName);
        return result;
    }

    private String requireKnownProfile(String profileName) throws Exception {
        String cleanProfileName = profileName == null ? "" : profileName.trim();
        if (cleanProfileName.isBlank() || !cleanProfileName.matches("[A-Za-z0-9_-]+")) {
            throw new IllegalArgumentException("Invalid profile name.");
        }
        List<String> profileNames = List.of(readEnvFile().getOrDefault("PROFILE_NAMES", "").trim().split("\\s+"));
        if (!profileNames.contains(cleanProfileName)) {
            throw new IllegalArgumentException("Unknown profile: " + cleanProfileName);
        }
        return cleanProfileName;
    }

    private int clampProfileCount(Integer value, int maxProfiles) {
        if (maxProfiles <= 0) {
            return 0;
        }
        if (value == null) {
            return maxProfiles;
        }
        return Math.max(1, Math.min(maxProfiles, value));
    }

    private List<String> selectedProfiles(List<String> requestedProfiles, List<Map<String, String>> allProfiles) {
        if (requestedProfiles == null || requestedProfiles.isEmpty()) {
            return List.of();
        }
        List<String> allowed = allProfiles.stream()
                .map(profile -> profile.getOrDefault("name", ""))
                .filter(name -> !name.isBlank())
                .toList();
        List<String> selected = new ArrayList<>();
        for (String requestedProfile : requestedProfiles) {
            if (requestedProfile == null) {
                continue;
            }
            String profileName = requestedProfile.trim();
            if (allowed.contains(profileName) && !selected.contains(profileName)) {
                selected.add(profileName);
            }
        }
        return selected;
    }

    private List<String> filterProfilesForCapabilities(
            List<String> selectedProfiles,
            List<Map<String, String>> allProfiles,
            boolean requireYoutube,
            boolean requirePornhub
    ) {
        if (!requireYoutube && !requirePornhub) {
            return selectedProfiles;
        }
        List<String> selected = selectedProfiles == null ? List.of() : selectedProfiles;
        boolean useAllProfiles = selected.isEmpty();
        List<String> filtered = new ArrayList<>();
        for (Map<String, String> profile : allProfiles) {
            String name = profile.getOrDefault("name", "");
            if (name.isBlank() || (!useAllProfiles && !selected.contains(name))) {
                continue;
            }
            boolean youtube = Boolean.parseBoolean(profile.getOrDefault("supportsYoutube", "false"));
            boolean pornhub = Boolean.parseBoolean(profile.getOrDefault("supportsPornhub", "false"));
            if ((!requireYoutube || youtube) && (!requirePornhub || pornhub)) {
                filtered.add(name);
            }
        }
        return filtered;
    }

    private void ensureCapabilityFilterMatch(List<String> selectedProfiles, boolean requireYoutube, boolean requirePornhub) {
        if ((requireYoutube || requirePornhub) && selectedProfiles.isEmpty()) {
            String filter = requireYoutube && requirePornhub ? "YT and PH" : requireYoutube ? "YT" : "PH";
            throw new IllegalArgumentException("No profiles match proxy filter: " + filter);
        }
    }

    private boolean requireYoutube(ChromeProfilesLaunchRequest request) {
        return Boolean.TRUE.equals(request == null ? null : request.requireYoutube());
    }

    private boolean requirePornhub(ChromeProfilesLaunchRequest request) {
        return Boolean.TRUE.equals(request == null ? null : request.requirePornhub());
    }

    private boolean requireYoutube(ChromeProfilesBulkActionRequest request) {
        return Boolean.TRUE.equals(request == null ? null : request.requireYoutube());
    }

    private boolean requirePornhub(ChromeProfilesBulkActionRequest request) {
        return Boolean.TRUE.equals(request == null ? null : request.requirePornhub());
    }

    private String launchUrlForRequest(ChromeProfilesLaunchRequest request) {
        if (Boolean.TRUE.equals(request == null ? null : request.loginMode())) {
            return "https://accounts.google.com/";
        }
        return normalizeLaunchUrl(request == null ? null : request.url());
    }

    private String normalizeLaunchUrl(String url) {
        if (url == null) {
            return "";
        }
        String trimmed = url.trim();
        if (trimmed.isBlank()) {
            return "";
        }
        if (trimmed.startsWith("http://") || trimmed.startsWith("https://")) {
            return normalizeYoutubeUrl(trimmed);
        }
        throw new IllegalArgumentException("Launch URL must start with http:// or https://");
    }

    private String cleanReferer(String referer) {
        if (referer == null) {
            return "";
        }
        String trimmed = referer.trim();
        if (trimmed.isBlank()) {
            return "";
        }
        if (trimmed.startsWith("http://") || trimmed.startsWith("https://")) {
            return trimmed;
        }
        throw new IllegalArgumentException("Referer must start with http:// or https://");
    }

    private String cleanVideoQuality(String quality) {
        if (quality == null || quality.isBlank()) {
            return "auto";
        }
        String normalized = quality.trim().toLowerCase();
        return switch (normalized) {
            case "auto", "tiny", "small", "medium", "large", "hd720", "hd1080", "hd1440", "highres" -> normalized;
            default -> throw new IllegalArgumentException("Unsupported video quality: " + quality);
        };
    }

    private String normalizeYoutubeUrl(String url) {
        try {
            URI uri = URI.create(url);
            String host = uri.getHost() == null ? "" : uri.getHost().toLowerCase();
            if ("youtu.be".equals(host) || host.endsWith(".youtu.be")) {
                String path = uri.getPath() == null ? "" : uri.getPath();
                String videoId = path.replaceFirst("^/", "").split("/")[0];
                if (!videoId.isBlank()) {
                    return "https://www.youtube.com/watch?v=" + videoId;
                }
            }
            if ("youtube.com".equals(host) || host.endsWith(".youtube.com")) {
                Map<String, String> query = parseQuery(uri.getRawQuery());
                String videoId = query.getOrDefault("v", "");
                if (!videoId.isBlank()) {
                    StringBuilder normalized = new StringBuilder("https://www.youtube.com/watch?v=").append(videoId);
                    String list = query.getOrDefault("list", "");
                    if (!list.isBlank()) {
                        normalized.append("&list=").append(list);
                    }
                    return normalized.toString();
                }
            }
        } catch (Exception ignored) {
            return url;
        }
        return url;
    }

    private Map<String, String> parseQuery(String rawQuery) {
        Map<String, String> values = new LinkedHashMap<>();
        if (rawQuery == null || rawQuery.isBlank()) {
            return values;
        }
        for (String part : rawQuery.split("&")) {
            int separator = part.indexOf('=');
            String key = separator < 0 ? part : part.substring(0, separator);
            String value = separator < 0 ? "" : part.substring(separator + 1);
            values.put(key, value);
        }
        return values;
    }

    private int clampDelay(Integer value, int min, int max) {
        if (value == null) {
            return min;
        }
        return Math.max(min, Math.min(max, value));
    }

    private Map<String, Object> checkUrlWithProxy(String profileName, String proxy, String url) throws Exception {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("name", profileName);
        result.put("proxy", maskProxy(proxy));

        if (proxy == null || proxy.isBlank()) {
            result.put("ok", false);
            result.put("status", "");
            result.put("location", "");
            result.put("reason", "Proxy not set");
            return result;
        }

        int timeoutSeconds = checkTimeoutSeconds();
        ProcessBuilder builder = new ProcessBuilder(
                "curl",
                "-sS",
                "-I",
                "--max-time",
                String.valueOf(timeoutSeconds),
                "-x",
                proxy,
                url
        ).redirectErrorStream(true);
        Process process = builder.start();
        boolean finished = process.waitFor(timeoutSeconds + 5L, TimeUnit.SECONDS);
        String output = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
        if (!finished) {
            process.destroyForcibly();
            result.put("ok", false);
            result.put("status", "");
            result.put("location", "");
            result.put("reason", "Timeout");
            return result;
        }

        String status = "";
        String location = "";
        String redirectMarker = "";
        for (String rawLine : output.split("\\R")) {
            String line = rawLine.trim();
            String lower = line.toLowerCase();
            if (line.startsWith("HTTP/")) {
                status = line;
            } else if (lower.startsWith("location:")) {
                location = line.substring("location:".length()).trim();
            } else if (lower.startsWith("ph-redirect:")) {
                redirectMarker = line.substring("ph-redirect:".length()).trim();
            }
        }

        int statusCode = parseStatusCode(status);
        boolean redirected = statusCode >= 300 && statusCode < 400;
        boolean homepageRedirect = redirected && (location.equals("/") || location.equalsIgnoreCase("https://www.pornhub.com/"));
        boolean ok = statusCode >= 200 && statusCode < 300;

        result.put("ok", ok);
        result.put("status", status);
        result.put("statusCode", statusCode);
        result.put("location", location);
        result.put("redirectMarker", redirectMarker);
        result.put("reason", ok ? "OK" : reasonFor(statusCode, location, redirectMarker, homepageRedirect, output));
        return result;
    }

    private int parseStatusCode(String status) {
        if (status == null || status.isBlank()) {
            return 0;
        }
        String[] parts = status.split("\\s+");
        if (parts.length < 2) {
            return 0;
        }
        try {
            return Integer.parseInt(parts[1]);
        } catch (NumberFormatException ex) {
            return 0;
        }
    }

    private String reasonFor(int statusCode, String location, String redirectMarker, boolean homepageRedirect, String output) {
        if (homepageRedirect) {
            return redirectMarker.isBlank() ? "Redirected to homepage" : "Redirected to homepage, marker " + redirectMarker;
        }
        if (statusCode >= 300 && statusCode < 400) {
            return location.isBlank() ? "Redirect" : "Redirect to " + location;
        }
        if (statusCode > 0) {
            return "HTTP " + statusCode;
        }
        String trimmed = output == null ? "" : output.trim();
        return trimmed.isBlank() ? "No response" : trimmed.substring(0, Math.min(160, trimmed.length()));
    }

    private boolean chromeFound() {
        if (isWindows()) {
            return Files.isRegularFile(Path.of("C:\\Program Files\\Google\\Chrome\\Application\\chrome.exe"));
        }
        try {
            Process process = new ProcessBuilder("bash", "-lc", "command -v google-chrome chromium chromium-browser").start();
            return process.waitFor(3, TimeUnit.SECONDS) && process.exitValue() == 0;
        } catch (Exception ignored) {
            return false;
        }
    }

    private Map<String, String> readProfileState(String profileName) {
        Path stateFile = profilesDir().resolve("state").resolve(profileName + ".env");
        if (!Files.isRegularFile(stateFile)) {
            return Map.of();
        }
        try {
            Map<String, String> state = new LinkedHashMap<>();
            for (String rawLine : Files.readAllLines(stateFile, StandardCharsets.UTF_8)) {
                Matcher matcher = ENV_LINE.matcher(rawLine.trim());
                if (matcher.matches()) {
                    state.put(matcher.group(1), unquote(matcher.group(2).trim()));
                }
            }
            return state;
        } catch (Exception ignored) {
            return Map.of();
        }
    }

    private Map<String, List<String>> runningProfileProcesses(List<String> profileNames) {
        Map<String, List<String>> running = new HashMap<>();
        try {
            if (isWindows()) {
                String output = runPowerShell("""
$ErrorActionPreference = 'SilentlyContinue'
Get-CimInstance Win32_Process -Filter "name = 'chrome.exe' or name = 'chrome_proxy.exe'" |
  Where-Object { $_.CommandLine -match '--user-data-dir=' } |
  ForEach-Object { "$($_.ProcessId)`t$($_.CommandLine)" }
""");
                collectRunningProfiles(running, profileNames, output);
            } else {
                Process process = new ProcessBuilder("bash", "-lc", "ps -eo pid=,args= | grep -E 'chrome|chromium' | grep -- '--user-data-dir=' || true")
                        .redirectErrorStream(true)
                        .start();
                process.waitFor(5, TimeUnit.SECONDS);
                collectRunningProfiles(running, profileNames, new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8));
            }
        } catch (Exception ignored) {
            // Keep going with the JVM process list fallback below.
        }
        collectRunningProfilesFromProcessHandles(running, profileNames);
        return running;
    }

    private void collectRunningProfiles(Map<String, List<String>> running, List<String> profileNames, String output) {
        for (String rawLine : output.split("\\R")) {
            String line = rawLine.trim();
            if (line.isBlank()) {
                continue;
            }
            String[] parts = line.split("\\s+", 2);
            if (parts.length < 2) {
                continue;
            }
            collectRunningProfile(running, profileNames, parts[0].trim(), parts[1]);
        }
    }

    private void collectRunningProfilesFromProcessHandles(Map<String, List<String>> running, List<String> profileNames) {
        ProcessHandle.allProcesses().forEach(process -> {
            ProcessHandle.Info info = process.info();
            String commandLine = info.commandLine().orElse("");
            if (commandLine.isBlank() || !commandLine.toLowerCase().contains("--user-data-dir=")) {
                return;
            }
            collectRunningProfile(running, profileNames, String.valueOf(process.pid()), commandLine);
        });
    }

    private void collectRunningProfile(Map<String, List<String>> running, List<String> profileNames, String pid, String commandLine) {
        for (String profileName : profileNames) {
            if (profileName.isBlank() || !commandReferencesProfile(commandLine, profileDir(profileName))) {
                continue;
            }
            List<String> pids = running.computeIfAbsent(profileName, ignored -> new ArrayList<>());
            if (!pids.contains(pid)) {
                pids.add(pid);
            }
        }
    }

    private boolean commandReferencesProfile(String commandLine, Path profileDir) {
        String normalizedCommand = commandLine.replace("\\", "/").replace("\"", "").toLowerCase();
        String normalizedProfileDir = profileDir.toString().replace("\\", "/").replace("\"", "").toLowerCase();
        int index = normalizedCommand.indexOf(normalizedProfileDir);
        while (index >= 0) {
            int after = index + normalizedProfileDir.length();
            if (after >= normalizedCommand.length()) {
                return true;
            }
            char next = normalizedCommand.charAt(after);
            if (Character.isWhitespace(next) || next == '/' || next == '\'' || next == ';') {
                return true;
            }
            index = normalizedCommand.indexOf(normalizedProfileDir, after);
        }
        return false;
    }

    private boolean focusProfileWindow(String profileName) throws Exception {
        if (!isWindows()) {
            return false;
        }
        String profileDir = escapePowerShellSingleQuoted(profileDir(profileName).toString());
        String output = runPowerShell("""
$ErrorActionPreference = 'SilentlyContinue'
$profileDir = '__PROFILE_DIR__'
$procs = @(Get-CimInstance Win32_Process -Filter "name = 'chrome.exe'" | Where-Object { $_.CommandLine -like "*$profileDir*" })
if ($procs.Count -eq 0) { 'not-running'; exit 0 }
Add-Type @"
using System;
using System.Runtime.InteropServices;
public class WinApi {
  [DllImport("user32.dll")] public static extern bool SetForegroundWindow(IntPtr hWnd);
  [DllImport("user32.dll")] public static extern bool ShowWindowAsync(IntPtr hWnd, int nCmdShow);
}
"@
foreach ($item in $procs) {
  $process = Get-Process -Id $item.ProcessId -ErrorAction SilentlyContinue
  if ($process -and $process.MainWindowHandle -ne 0) {
    [WinApi]::ShowWindowAsync($process.MainWindowHandle, 9) | Out-Null
    [WinApi]::SetForegroundWindow($process.MainWindowHandle) | Out-Null
    'focused'
    exit 0
  }
}
'not-focused'
""".replace("__PROFILE_DIR__", profileDir));
        return output.contains("focused");
    }

    private int closeProfileProcesses(String profileName) throws Exception {
        if (isWindows()) {
            String profileDir = escapePowerShellSingleQuoted(profileDir(profileName).toString());
            String output = runPowerShell("""
$ErrorActionPreference = 'SilentlyContinue'
$profileDir = '__PROFILE_DIR__'
$procs = @(Get-CimInstance Win32_Process -Filter "name = 'chrome.exe'" | Where-Object { $_.CommandLine -like "*$profileDir*" })
foreach ($item in $procs) { Stop-Process -Id $item.ProcessId -Force -ErrorAction SilentlyContinue }
$procs.Count
""".replace("__PROFILE_DIR__", profileDir));
            return parseInt(output.trim(), 0);
        }
        String quoted = profileDir(profileName).toString().replace("'", "'\\''");
        Process process = new ProcessBuilder("bash", "-lc", "pkill -f -- '--user-data-dir=" + quoted + "'; true")
                .redirectErrorStream(true)
                .start();
        process.waitFor(5, TimeUnit.SECONDS);
        return 0;
    }

    private String runPowerShell(String script) throws Exception {
        Process process = new ProcessBuilder(
                "powershell.exe",
                "-NoProfile",
                "-ExecutionPolicy",
                "Bypass",
                "-Command",
                script
        )
                .redirectErrorStream(true)
                .start();
        boolean finished = process.waitFor(10, TimeUnit.SECONDS);
        String output = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
        if (!finished) {
            process.destroyForcibly();
            throw new IllegalStateException("PowerShell command timed out.");
        }
        return output;
    }

    private int parseInt(String value, int fallback) {
        try {
            return Integer.parseInt(value.trim());
        } catch (Exception ignored) {
            return fallback;
        }
    }

    private String firstNonBlank(String... values) {
        for (String value : values) {
            if (value != null && !value.isBlank()) {
                return value;
            }
        }
        return "";
    }

    private String escapePowerShellSingleQuoted(String value) {
        return value.replace("'", "''");
    }

    private List<Map<String, String>> readProfiles() throws Exception {
        Map<String, String> env = readEnvFile();
        Map<String, ProxyCapability> proxyCapabilities = readProxyCapabilities();
        String[] profileNames = env.getOrDefault("PROFILE_NAMES", "").trim().split("\\s+");
        List<String> configuredProfileNames = new ArrayList<>();
        for (String profileName : profileNames) {
            if (!profileName.isBlank()) {
                configuredProfileNames.add(profileName);
            }
        }
        Map<String, List<String>> runningProcesses = runningProfileProcesses(configuredProfileNames);
        List<Map<String, String>> profiles = new ArrayList<>();
        for (String profileName : profileNames) {
            if (profileName.isBlank()) {
                continue;
            }
            Map<String, String> state = readProfileState(profileName);
            Map<String, String> googleAccount = readGoogleAccount(profileName);
            List<String> pids = new ArrayList<>(runningProcesses.getOrDefault(profileName, List.of()));
            if (pids.isEmpty()) {
                pids.addAll(liveStatePids(state));
            }
            String debugPort = state.getOrDefault("DEBUG_PORT", "");
            if (pids.isEmpty() && devToolsPortAlive(debugPort)) {
                pids.add("debug:" + debugPort);
            }
            Map<String, String> profile = new LinkedHashMap<>();
            profile.put("name", profileName);
            profile.put("label", env.getOrDefault("PROFILE_LABEL_" + profileName, profileName));
            String googleEmail = googleAccount.getOrDefault("email", "");
            String loginStatus = googleEmail.isBlank()
                    ? env.getOrDefault("LOGIN_STATUS_" + profileName, "not_logged_in")
                    : "logged_in";
            profile.put("googleAccount", googleEmail);
            profile.put("googleAccountName", googleAccount.getOrDefault("name", ""));
            profile.put("loginStatus", loginStatus);
            profile.put("loggedIn", String.valueOf(!googleEmail.isBlank() || "logged_in".equalsIgnoreCase(loginStatus)));
            profile.put("proxy", maskProxy(env.getOrDefault("PROXY_" + profileName, "")));
            profile.put("upstreamProxy", maskProxy(env.getOrDefault("UPSTREAM_PROXY_" + profileName, "")));
            String proxyKey = proxyKeyForProfile(env, profileName);
            ProxyCapability proxyCapability = proxyCapabilities.getOrDefault(proxyKey, new ProxyCapability(false, false));
            profile.put("proxyKey", proxyKey);
            profile.put("supportsYoutube", String.valueOf(proxyCapability.youtube()));
            profile.put("supportsPornhub", String.valueOf(proxyCapability.pornhub()));
            profile.put("proxyCountry", env.getOrDefault("PROXY_COUNTRY_" + profileName, ""));
            profile.put("proxyCity", env.getOrDefault("PROXY_CITY_" + profileName, ""));
            profile.put("timezone", firstNonBlank(env.getOrDefault("TIMEZONE_" + profileName, ""), env.getOrDefault("TIMEZONE", ""), state.getOrDefault("TIMEZONE", "")));
            profile.put("language", firstNonBlank(env.getOrDefault("LANGUAGE_" + profileName, ""), env.getOrDefault("LANGUAGE", ""), state.getOrDefault("LANGUAGE", "")));
            profile.put("windowSize", firstNonBlank(env.getOrDefault("WINDOW_SIZE_" + profileName, ""), env.getOrDefault("WINDOW_SIZE", ""), state.getOrDefault("WINDOW_SIZE", "")));
            profile.put("profileDir", profileDir(profileName).toString());
            profile.put("running", String.valueOf(!pids.isEmpty()));
            profile.put("pid", String.join(",", pids));
            profile.put("debugPort", debugPort);
            profile.put("lastUrl", state.getOrDefault("LAST_URL", ""));
            profile.put("lastOpenedAt", state.getOrDefault("LAST_OPENED_AT", ""));
            profile.put("lastMode", state.getOrDefault("MODE", ""));
            profiles.add(profile);
        }
        return profiles;
    }

    private List<String> liveStatePids(Map<String, String> state) {
        String pidValue = state.getOrDefault("PID", "");
        if (pidValue.isBlank()) {
            return List.of();
        }

        List<String> livePids = new ArrayList<>();
        for (String rawPid : pidValue.split(",")) {
            String pid = rawPid.trim();
            if (pid.isBlank()) {
                continue;
            }
            try {
                long numericPid = Long.parseLong(pid);
                if (ProcessHandle.of(numericPid).map(ProcessHandle::isAlive).orElse(false)) {
                    livePids.add(pid);
                }
            } catch (NumberFormatException ignored) {
                // Ignore stale or malformed state from older launches.
            }
        }
        return livePids;
    }

    private boolean devToolsPortAlive(String portValue) {
        int port = parseInt(portValue, 0);
        if (port <= 0 || port > 65535) {
            return false;
        }
        try {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create("http://127.0.0.1:" + port + "/json/version"))
                    .timeout(DEVTOOLS_STATUS_TIMEOUT)
                    .GET()
                    .build();
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            return response.statusCode() >= 200 && response.statusCode() < 300
                    && response.body() != null
                    && response.body().toLowerCase().contains("chrome");
        } catch (Exception ignored) {
            return false;
        }
    }

    private Map<String, String> readGoogleAccount(String profileName) {
        Path profileRoot = profileDir(profileName);
        List<Path> candidates = List.of(
                profileRoot.resolve("Default").resolve("Preferences"),
                profileRoot.resolve("Local State")
        );
        for (Path candidate : candidates) {
            Map<String, String> account = readGoogleAccountFromFile(candidate);
            if (!account.getOrDefault("email", "").isBlank()) {
                return account;
            }
        }
        return Map.of();
    }

    private Map<String, String> readGoogleAccountFromFile(Path file) {
        if (!Files.isRegularFile(file)) {
            return Map.of();
        }
        try {
            String content = Files.readString(file, StandardCharsets.UTF_8);
            String email = bestGoogleEmail(content);
            if (email.isBlank()) {
                return Map.of();
            }
            Map<String, String> account = new LinkedHashMap<>();
            account.put("email", email);
            String name = bestGoogleName(content, email);
            if (!name.isBlank()) {
                account.put("name", name);
            }
            return account;
        } catch (Exception ignored) {
            return Map.of();
        }
    }

    private String bestGoogleEmail(String content) {
        Matcher matcher = EMAIL.matcher(content);
        String firstEmail = "";
        while (matcher.find()) {
            String email = matcher.group();
            if (firstEmail.isBlank()) {
                firstEmail = email;
            }
            int start = Math.max(0, matcher.start() - 160);
            int end = Math.min(content.length(), matcher.end() + 160);
            String context = content.substring(start, end).toLowerCase();
            if (context.contains("account") || context.contains("signin") || context.contains("gaia") || context.contains("google")) {
                return email;
            }
        }
        return firstEmail;
    }

    private String bestGoogleName(String content, String email) {
        int emailIndex = content.indexOf(email);
        if (emailIndex >= 0) {
            int start = Math.max(0, emailIndex - 600);
            int end = Math.min(content.length(), emailIndex + 600);
            String nearEmail = content.substring(start, end);
            String name = firstJsonName(nearEmail);
            if (!name.isBlank()) {
                return name;
            }
        }
        return firstJsonName(content);
    }

    private String firstJsonName(String content) {
        Matcher matcher = FULL_NAME.matcher(content);
        while (matcher.find()) {
            String value = unescapeJsonString(matcher.group(1)).trim();
            if (!value.isBlank() && !EMAIL.matcher(value).matches()) {
                return value;
            }
        }
        return "";
    }

    private String unescapeJsonString(String value) {
        return value
                .replace("\\\"", "\"")
                .replace("\\\\", "\\")
                .replace("\\/", "/")
                .replace("\\n", "\n")
                .replace("\\r", "\r")
                .replace("\\t", "\t");
    }

    private Map<String, String> readEnvFile() throws Exception {
        Map<String, String> values = new LinkedHashMap<>();
        Path envFile = envFile();
        if (!Files.isRegularFile(envFile)) {
            return values;
        }
        try (BufferedReader reader = new BufferedReader(new StringReader(Files.readString(envFile, StandardCharsets.UTF_8)))) {
            String line;
            while ((line = reader.readLine()) != null) {
                String trimmed = line.trim();
                if (trimmed.isBlank() || trimmed.startsWith("#")) {
                    continue;
                }
                Matcher matcher = ENV_LINE.matcher(trimmed);
                if (!matcher.matches()) {
                    continue;
                }
                values.put(matcher.group(1), unquote(matcher.group(2).trim()));
            }
        }
        return values;
    }

    private String unquote(String value) {
        if (value.length() >= 2 && ((value.startsWith("\"") && value.endsWith("\"")) || (value.startsWith("'") && value.endsWith("'")))) {
            return value.substring(1, value.length() - 1);
        }
        return value;
    }

    private String upsertEnvValue(String content, String key, String value) {
        String line = key + "=\"" + value.replace("\\", "\\\\").replace("\"", "\\\"") + "\"";
        String normalized = content == null ? "" : content.replace("\r\n", "\n").replace("\r", "\n");
        String[] lines = normalized.split("\n", -1);
        List<String> updated = new ArrayList<>();
        boolean replaced = false;
        for (String existingLine : lines) {
            if (existingLine.startsWith(key + "=")) {
                if (!replaced) {
                    updated.add(line);
                    replaced = true;
                }
            } else {
                updated.add(existingLine);
            }
        }
        if (!replaced) {
            if (!updated.isEmpty() && !updated.get(updated.size() - 1).isBlank()) {
                updated.add("");
            }
            updated.add(line);
        }
        return String.join("\n", updated);
    }

    private void writeEnvFile(Path envFile, String content) throws Exception {
        Files.createDirectories(envFile.getParent());
        Path tempFile = envFile.resolveSibling(envFile.getFileName() + ".tmp");
        Files.writeString(tempFile, content.replace("\r\n", "\n").replace("\r", "\n"), StandardCharsets.UTF_8);
        try {
            Files.move(tempFile, envFile, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } catch (AtomicMoveNotSupportedException ex) {
            Files.move(tempFile, envFile, StandardCopyOption.REPLACE_EXISTING);
        }
    }

    private Map<String, ProxyCapability> readProxyCapabilities() throws Exception {
        Map<String, ProxyCapability> capabilities = new LinkedHashMap<>();
        Path file = proxyCapabilitiesFile();
        if (!Files.isRegularFile(file)) {
            return capabilities;
        }
        for (String line : Files.readAllLines(file, StandardCharsets.UTF_8)) {
            String trimmed = line.trim();
            if (trimmed.isBlank() || trimmed.startsWith("#")) {
                continue;
            }
            String[] parts = trimmed.split("\t");
            if (parts.length < 3 || parts[0].isBlank()) {
                continue;
            }
            capabilities.put(parts[0].trim().toLowerCase(), new ProxyCapability(Boolean.parseBoolean(parts[1]), Boolean.parseBoolean(parts[2])));
        }
        return capabilities;
    }

    private void writeProxyCapabilities(Map<String, ProxyCapability> capabilities) throws Exception {
        Path file = proxyCapabilitiesFile();
        Files.createDirectories(file.getParent());
        List<String> lines = new ArrayList<>();
        lines.add("# proxy-host\tyoutube\tpornhub");
        for (Map.Entry<String, ProxyCapability> entry : capabilities.entrySet()) {
            if (entry.getKey() == null || entry.getKey().isBlank()) {
                continue;
            }
            ProxyCapability capability = entry.getValue();
            lines.add(entry.getKey().trim().toLowerCase() + "\t" + capability.youtube() + "\t" + capability.pornhub());
        }
        Path tempFile = file.resolveSibling(file.getFileName() + ".tmp");
        Files.writeString(tempFile, String.join("\n", lines) + "\n", StandardCharsets.UTF_8);
        try {
            Files.move(tempFile, file, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } catch (AtomicMoveNotSupportedException ex) {
            Files.move(tempFile, file, StandardCopyOption.REPLACE_EXISTING);
        }
    }

    private String proxyKeyForProfile(Map<String, String> env, String profileName) {
        String upstreamProxy = env.getOrDefault("UPSTREAM_PROXY_" + profileName, "");
        String proxy = upstreamProxy.isBlank() ? env.getOrDefault("PROXY_" + profileName, "") : upstreamProxy;
        return proxyHostKey(proxy);
    }

    private String proxyHostKey(String proxy) {
        if (proxy == null || proxy.isBlank()) {
            return "";
        }
        String value = proxy.trim();
        try {
            URI uri = URI.create(value.contains("://") ? value : "http://" + value);
            String host = uri.getHost();
            if (host != null && !host.isBlank()) {
                return host.toLowerCase();
            }
        } catch (Exception ignored) {
            // Fall back to simple host extraction below.
        }
        String withoutScheme = value.replaceFirst("^[A-Za-z][A-Za-z0-9+.-]*://", "");
        int at = withoutScheme.lastIndexOf('@');
        if (at >= 0) {
            withoutScheme = withoutScheme.substring(at + 1);
        }
        int slash = withoutScheme.indexOf('/');
        if (slash >= 0) {
            withoutScheme = withoutScheme.substring(0, slash);
        }
        int colon = withoutScheme.indexOf(':');
        if (colon >= 0) {
            withoutScheme = withoutScheme.substring(0, colon);
        }
        return withoutScheme.trim().toLowerCase();
    }

    private String proxyCapabilityLabel(boolean youtube, boolean pornhub) {
        if (youtube && pornhub) {
            return "YT + PH";
        }
        if (youtube) {
            return "YT only";
        }
        if (pornhub) {
            return "PH only";
        }
        return "blocked";
    }

    private String maskProxy(String proxy) {
        if (proxy == null || proxy.isBlank()) {
            return "";
        }
        return proxy.replaceAll("(https?://)[^:@/]+:[^@/]+@", "$1***:***@");
    }

    private String readLogTail() throws Exception {
        Path logFile = logFile();
        if (!Files.isRegularFile(logFile)) {
            return "";
        }
        byte[] bytes = Files.readAllBytes(logFile);
        int start = Math.max(0, bytes.length - 4000);
        return new String(bytes, start, bytes.length - start, StandardCharsets.UTF_8);
    }

    private boolean isWindows() {
        return System.getProperty("os.name", "").toLowerCase().contains("win");
    }

    private Path profilesDir() {
        if (isWindows()) {
            return Path.of(System.getProperty("user.home"), "chrome-proxy-profiles").toAbsolutePath().normalize();
        }
        return Path.of("/root/chrome-proxy-profiles");
    }

    private Path startScript() {
        if (isWindows()) {
            return repoRoot().resolve("scripts").resolve("start-local-chrome-profiles.ps1").normalize();
        }
        return profilesDir().resolve("start-all.sh");
    }

    private Path startProfileScript() {
        if (isWindows()) {
            return repoRoot().resolve("scripts").resolve("start-profiles.ps1").normalize();
        }
        return profilesDir().resolve("start-profile.sh");
    }

    private Path envFile() {
        return profilesDir().resolve("profiles.env");
    }

    private Path proxyCapabilitiesFile() {
        return profilesDir().resolve(PROXY_CAPABILITIES_FILE);
    }

    private Path profileDir(String profileName) {
        return profilesDir().resolve("data").resolve(profileName).toAbsolutePath().normalize();
    }

    private Path logFile() {
        return profilesDir().resolve(isWindows() ? "start-local.log" : "start-all.log");
    }

    private Path repoRoot() {
        String configured = System.getenv("APP_REPO_DIR");
        if (configured != null && !configured.isBlank()) {
            return Path.of(configured).toAbsolutePath().normalize();
        }
        Path current = Path.of("").toAbsolutePath().normalize();
        if (current.getFileName() != null && "backend".equalsIgnoreCase(current.getFileName().toString())) {
            return current.getParent();
        }
        return current;
    }

    private record ProxyCapability(boolean youtube, boolean pornhub) {
    }
}
