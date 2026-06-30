package com.behindthesmile.posting.service;

import com.behindthesmile.posting.api.ChromeProfilesLaunchRequest;
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

        ProcessBuilder processBuilder = new ProcessBuilder(
                "bash",
                "-lc",
                "chmod +x ./start-all.sh && nohup ./start-all.sh > ./start-all.log 2>&1 & echo started"
        )
                .directory(PROFILES_DIR.toFile())
                .redirectErrorStream(true);
        processBuilder.environment().put("STAGGER_MIN_SECONDS", String.valueOf(minDelay));
        processBuilder.environment().put("STAGGER_MAX_SECONDS", String.valueOf(maxDelay));

        Process process = processBuilder.start();
        return waitForStart(process, minDelay, maxDelay);
    }

    private Map<String, Object> waitForStart(Process process, int minDelay, int maxDelay) throws Exception {
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

    private int clampDelay(Integer value, int min, int max) {
        if (value == null) {
            return min;
        }
        return Math.max(min, Math.min(max, value));
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
