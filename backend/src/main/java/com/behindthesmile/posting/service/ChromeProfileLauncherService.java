package com.behindthesmile.posting.service;

import com.behindthesmile.posting.api.ChromeProfilesLaunchRequest;
import com.behindthesmile.posting.api.ChromeProfilesUrlCheckRequest;
import org.springframework.stereotype.Service;

import java.io.BufferedReader;
import java.io.StringReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
public class ChromeProfileLauncherService {
    private static final Path PROFILES_DIR = Path.of("/root/chrome-proxy-profiles");
    private static final Path START_SCRIPT = PROFILES_DIR.resolve("start-all.sh");
    private static final Path START_PROFILE_SCRIPT = PROFILES_DIR.resolve("start-profile.sh");
    private static final Path ENV_FILE = PROFILES_DIR.resolve("profiles.env");
    private static final Path LOG_FILE = PROFILES_DIR.resolve("start-all.log");
    private static final Pattern ENV_LINE = Pattern.compile("^([A-Za-z_][A-Za-z0-9_]*)=(.*)$");

    private Instant lastStartedAt;

    public Map<String, Object> startAll(ChromeProfilesLaunchRequest request) throws Exception {
        if (!Files.isDirectory(PROFILES_DIR)) {
            throw new IllegalStateException("Chrome proxy profiles directory is missing: " + PROFILES_DIR);
        }
        if (!Files.isRegularFile(START_SCRIPT)) {
            throw new IllegalStateException("Chrome proxy launcher script is missing: " + START_SCRIPT);
        }

        int minDelay = clampDelay(request == null ? null : request.minDelaySeconds(), 0, 3600);
        int maxDelay = clampDelay(request == null ? null : request.maxDelaySeconds(), minDelay, 3600);
        List<Map<String, String>> allProfiles = readProfiles();
        List<String> selectedProfiles = selectedProfiles(request == null ? null : request.profileNames(), allProfiles);
        int maxProfiles = selectedProfiles.isEmpty() ? allProfiles.stream()
                .filter(profile -> !profile.getOrDefault("proxy", "").isBlank() || !profile.getOrDefault("upstreamProxy", "").isBlank())
                .toList()
                .size() : selectedProfiles.size();
        int profileCount = clampProfileCount(request == null ? null : request.profileCount(), maxProfiles);
        String launchUrl = normalizeLaunchUrl(request == null ? null : request.url());

        ProcessBuilder processBuilder = new ProcessBuilder(
                "bash",
                "-lc",
                "chmod +x ./start-all.sh && nohup ./start-all.sh > ./start-all.log 2>&1 & echo started"
        )
                .directory(PROFILES_DIR.toFile())
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

        Process process = processBuilder.start();
        return waitForStart(process, minDelay, maxDelay, profileCount, launchUrl, selectedProfiles);
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
        status.put("directory", PROFILES_DIR.toString());
        status.put("script", START_SCRIPT.toString());
        status.put("startProfileScript", START_PROFILE_SCRIPT.toString());
        status.put("envFile", ENV_FILE.toString());
        status.put("directoryExists", Files.isDirectory(PROFILES_DIR));
        status.put("scriptExists", Files.isRegularFile(START_SCRIPT));
        status.put("startProfileScriptExists", Files.isRegularFile(START_PROFILE_SCRIPT));
        status.put("envFileExists", Files.isRegularFile(ENV_FILE));
        status.put("profiles", readProfiles());
        status.put("logExists", Files.isRegularFile(LOG_FILE));
        status.put("lastStartedAt", lastStartedAt == null ? "" : lastStartedAt.toString());
        status.put("logTail", readLogTail());
        return status;
    }

    public Map<String, Object> checkUrl(ChromeProfilesUrlCheckRequest request) throws Exception {
        String url = normalizeLaunchUrl(request == null ? null : request.url());
        if (url.isBlank()) {
            throw new IllegalArgumentException("URL is required.");
        }

        Map<String, String> env = readEnvFile();
        List<Map<String, String>> profiles = readProfiles();
        List<Map<String, Object>> results = new ArrayList<>();
        int okCount = 0;

        for (Map<String, String> profile : profiles) {
            String profileName = profile.getOrDefault("name", "");
            String upstreamProxy = env.getOrDefault("UPSTREAM_PROXY_" + profileName, "");
            String proxy = upstreamProxy.isBlank() ? env.getOrDefault("PROXY_" + profileName, "") : upstreamProxy;
            Map<String, Object> result = checkUrlWithProxy(profileName, proxy, url);
            if (Boolean.TRUE.equals(result.get("ok"))) {
                okCount++;
            }
            results.add(result);
        }

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("url", url);
        response.put("okCount", okCount);
        response.put("totalCount", results.size());
        response.put("results", results);
        return response;
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

    private String normalizeLaunchUrl(String url) {
        if (url == null) {
            return "";
        }
        String trimmed = url.trim();
        if (trimmed.isBlank()) {
            return "";
        }
        if (trimmed.startsWith("http://") || trimmed.startsWith("https://")) {
            return trimmed;
        }
        throw new IllegalArgumentException("Launch URL must start with http:// or https://");
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

        ProcessBuilder builder = new ProcessBuilder(
                "curl",
                "-sS",
                "-I",
                "--max-time",
                "15",
                "-x",
                proxy,
                url
        ).redirectErrorStream(true);
        Process process = builder.start();
        boolean finished = process.waitFor(20, TimeUnit.SECONDS);
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

    private List<Map<String, String>> readProfiles() throws Exception {
        Map<String, String> env = readEnvFile();
        String[] profileNames = env.getOrDefault("PROFILE_NAMES", "").trim().split("\\s+");
        List<Map<String, String>> profiles = new ArrayList<>();
        for (String profileName : profileNames) {
            if (profileName.isBlank()) {
                continue;
            }
            Map<String, String> profile = new LinkedHashMap<>();
            profile.put("name", profileName);
            profile.put("proxy", maskProxy(env.getOrDefault("PROXY_" + profileName, "")));
            profile.put("upstreamProxy", maskProxy(env.getOrDefault("UPSTREAM_PROXY_" + profileName, "")));
            profiles.add(profile);
        }
        return profiles;
    }

    private Map<String, String> readEnvFile() throws Exception {
        Map<String, String> values = new LinkedHashMap<>();
        if (!Files.isRegularFile(ENV_FILE)) {
            return values;
        }
        try (BufferedReader reader = new BufferedReader(new StringReader(Files.readString(ENV_FILE, StandardCharsets.UTF_8)))) {
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

    private String maskProxy(String proxy) {
        if (proxy == null || proxy.isBlank()) {
            return "";
        }
        return proxy.replaceAll("(https?://)[^:@/]+:[^@/]+@", "$1***:***@");
    }

    private String readLogTail() throws Exception {
        if (!Files.isRegularFile(LOG_FILE)) {
            return "";
        }
        byte[] bytes = Files.readAllBytes(LOG_FILE);
        int start = Math.max(0, bytes.length - 4000);
        return new String(bytes, start, bytes.length - start, StandardCharsets.UTF_8);
    }
}
