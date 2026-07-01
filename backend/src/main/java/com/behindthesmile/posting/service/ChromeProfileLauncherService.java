package com.behindthesmile.posting.service;

import com.behindthesmile.posting.api.ChromeProfilesLaunchRequest;
import com.behindthesmile.posting.api.ChromeProfileLoginStatusRequest;
import com.behindthesmile.posting.api.ChromeProfilesUrlCheckRequest;
import org.springframework.stereotype.Service;

import java.io.BufferedReader;
import java.io.StringReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
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
    private static final Pattern ENV_LINE = Pattern.compile("^([A-Za-z_][A-Za-z0-9_]*)=(.*)$");

    private Instant lastStartedAt;

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
        int requestedCount = request == null ? 1 : request.profileCount() == null ? 1 : request.profileCount();
        int availableProfiles = allProfiles.stream()
                .filter(profile -> !profile.getOrDefault("proxy", "").isBlank() || !profile.getOrDefault("upstreamProxy", "").isBlank())
                .toList()
                .size();
        int maxProfiles = selectedProfiles.isEmpty()
                ? Math.max(1, availableProfiles == 0 ? requestedCount : availableProfiles)
                : selectedProfiles.size();
        int profileCount = clampProfileCount(request == null ? null : request.profileCount(), maxProfiles);
        String launchUrl = normalizeLaunchUrl(request == null ? null : request.url());

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
        if (minDelay > 0 || maxDelay > 0) {
            command.add("-DelayFrom");
            command.add(String.valueOf(minDelay));
            command.add("-DelayTo");
            command.add(String.valueOf(maxDelay));
        }
        if (!selectedProfiles.isEmpty()) {
            command.add("-Profiles");
            command.addAll(selectedProfiles);
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
        status.put("directory", profilesDir.toString());
        status.put("script", startScript.toString());
        status.put("startProfileScript", startProfileScript.toString());
        status.put("envFile", envFile.toString());
        status.put("directoryExists", Files.isDirectory(profilesDir));
        status.put("scriptExists", Files.isRegularFile(startScript));
        status.put("startProfileScriptExists", Files.isRegularFile(startProfileScript));
        status.put("envFileExists", Files.isRegularFile(envFile));
        status.put("profiles", readProfiles());
        status.put("logExists", Files.isRegularFile(logFile()));
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
            profile.put("label", env.getOrDefault("PROFILE_LABEL_" + profileName, profileName));
            String loginStatus = env.getOrDefault("LOGIN_STATUS_" + profileName, "not_logged_in");
            profile.put("loginStatus", loginStatus);
            profile.put("loggedIn", String.valueOf("logged_in".equalsIgnoreCase(loginStatus)));
            profile.put("proxy", maskProxy(env.getOrDefault("PROXY_" + profileName, "")));
            profile.put("upstreamProxy", maskProxy(env.getOrDefault("UPSTREAM_PROXY_" + profileName, "")));
            profiles.add(profile);
        }
        return profiles;
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
}
