package com.behindthesmile.posting.service;

import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;

@Service
public class ChromeProfileLauncherService {
    private static final Path PROFILES_DIR = Path.of("/root/chrome-proxy-profiles");
    private static final Path START_SCRIPT = PROFILES_DIR.resolve("start-all.sh");
    private static final Path LOG_FILE = PROFILES_DIR.resolve("start-all.log");

    private Instant lastStartedAt;

    public Map<String, Object> startAll() throws Exception {
        if (!Files.isDirectory(PROFILES_DIR)) {
            throw new IllegalStateException("Chrome proxy profiles directory is missing: " + PROFILES_DIR);
        }
        if (!Files.isRegularFile(START_SCRIPT)) {
            throw new IllegalStateException("Chrome proxy launcher script is missing: " + START_SCRIPT);
        }

        Process process = new ProcessBuilder(
                "bash",
                "-lc",
                "chmod +x ./start-all.sh && nohup ./start-all.sh > ./start-all.log 2>&1 & echo started"
        )
                .directory(PROFILES_DIR.toFile())
                .redirectErrorStream(true)
                .start();
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
        return result;
    }

    public Map<String, Object> status() throws Exception {
        Map<String, Object> status = new LinkedHashMap<>();
        status.put("directory", PROFILES_DIR.toString());
        status.put("script", START_SCRIPT.toString());
        status.put("directoryExists", Files.isDirectory(PROFILES_DIR));
        status.put("scriptExists", Files.isRegularFile(START_SCRIPT));
        status.put("logExists", Files.isRegularFile(LOG_FILE));
        status.put("lastStartedAt", lastStartedAt == null ? "" : lastStartedAt.toString());
        status.put("logTail", readLogTail());
        return status;
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
