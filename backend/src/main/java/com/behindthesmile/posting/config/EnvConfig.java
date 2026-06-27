package com.behindthesmile.posting.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Configuration
public class EnvConfig {
    @Bean
    public AppProperties appProperties() throws IOException {
        Map<String, String> values = new HashMap<>(System.getenv());
        Path envPath = firstExistingPath(
                Path.of("config/.env"),
                Path.of(".env"),
                Path.of("../.env")
        );

        if (envPath != null && Files.exists(envPath)) {
            List<String> lines = Files.readAllLines(envPath, StandardCharsets.UTF_8);
            for (String line : lines) {
                String trimmed = line.trim();
                if (trimmed.isEmpty() || trimmed.startsWith("#")) {
                    continue;
                }

                int separator = trimmed.indexOf('=');
                if (separator < 0) {
                    continue;
                }

                String key = trimmed.substring(0, separator).trim();
                String value = trimmed.substring(separator + 1).trim();
                if ((value.startsWith("\"") && value.endsWith("\"")) || (value.startsWith("'") && value.endsWith("'"))) {
                    value = value.substring(1, value.length() - 1);
                }
                values.putIfAbsent(key, value);
            }
        }

        return new AppProperties(
                new AppProperties.OpenAi(
                        values.get("OPENAI_API_KEY"),
                        values.getOrDefault("OPENAI_MODEL", "gpt-4.1-mini")
                ),
                new AppProperties.Defaults(
                        values.getOrDefault("POST_LANGUAGE", "uk"),
                        values.getOrDefault("POST_TOPIC", "Behind The Smile"),
                        values.getOrDefault("POST_TONE", "warm, fan-friendly, concise"),
                        Integer.parseInt(values.getOrDefault("POST_COUNT", "3"))
                ),
                new AppProperties.X(
                        firstNonBlank(values.get("X_ACCOUNT_LABEL"), values.get("TWITTER_ACCOUNT_LABEL")),
                        values.get("X_ACCESS_TOKEN"),
                        values.get("X_CLIENT_ID"),
                        values.get("X_CLIENT_SECRET"),
                        values.getOrDefault("X_REDIRECT_URI", "http://127.0.0.1:3000/callback"),
                        values.getOrDefault("X_SCOPES", "tweet.read tweet.write users.read"),
                        values.get("X_API_KEY"),
                        values.get("X_API_SECRET"),
                        values.get("X_ACCESS_TOKEN_SECRET"),
                        values.get("X_REFRESH_TOKEN"),
                        values.getOrDefault("X_PUBLISH_MODE", "api"),
                        values.getOrDefault("X_BROWSER", "chrome"),
                        values.getOrDefault("X_BROWSER_PROFILE_DIR", "../generated/selenium/chrome-profile"),
                        Boolean.parseBoolean(values.getOrDefault("X_BROWSER_HEADLESS", "false"))
                ),
                new AppProperties.Threads(
                        values.get("THREADS_ACCOUNT_LABEL"),
                        values.get("THREADS_ACCESS_TOKEN"),
                        values.get("THREADS_USER_ID"),
                        firstNonBlank(values.get("THREADS_APP_ID"), values.get("META_APP_ID")),
                        firstNonBlank(values.get("THREADS_APP_SECRET"), values.get("META_APP_SECRET")),
                        values.getOrDefault("THREADS_REDIRECT_URI", "http://127.0.0.1:3001/callback")
                )
        );
    }

    private static Path firstExistingPath(Path... paths) {
        for (Path path : paths) {
            if (Files.exists(path)) {
                return path;
            }
        }
        return paths.length > 0 ? paths[0] : null;
    }

    private static String firstNonBlank(String first, String second) {
        if (first != null && !first.isBlank()) {
            return first;
        }
        return second;
    }
}
