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
import java.util.stream.Stream;

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

        AppProperties.Runtime runtime = new AppProperties.Runtime(
                values.getOrDefault("DATA_DIR", "../generated"),
                values.getOrDefault("QUEUE_FILE", "queue.jsonl"),
                values.getOrDefault("DRAFTS_FILE", "posts.jsonl"),
                values.getOrDefault("X_LINKS_FILE", "x-ready.html"),
                values.getOrDefault("CONTENT_PLAN_FILE", "config/content-plan.json"),
                values.getOrDefault("ACTIVE_ACCOUNT_FILE", "active-account.txt"),
                values.getOrDefault("MEDIA_DIR", "../generated/media"),
                values.getOrDefault("PUBLIC_BASE_URL", "http://167.233.93.6:4301")
        );

        AppProperties.X defaultX = buildX(values, "", runtime);
        AppProperties.Threads defaultThreads = buildThreads(values, "");

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
                runtime,
                buildAccounts(values, runtime, defaultX, defaultThreads),
                defaultX,
                defaultThreads
        );
    }

    private static List<AppProperties.Account> buildAccounts(
            Map<String, String> values,
            AppProperties.Runtime runtime,
            AppProperties.X defaultX,
            AppProperties.Threads defaultThreads
    ) {
        String accountIds = values.get("SOCIAL_ACCOUNTS");
        if (accountIds == null || accountIds.isBlank()) {
            return List.of(new AppProperties.Account(
                    "default",
                    firstNonBlank(values.get("ACCOUNT_LABEL"), "Default account"),
                    defaultX,
                    defaultThreads
            ));
        }

        return Stream.of(accountIds.split(","))
                .map(String::trim)
                .filter(id -> !id.isBlank())
                .map(id -> {
                    String prefix = "ACCOUNT_" + normalizeAccountKey(id) + "_";
                    AppProperties.X x = buildX(values, prefix, runtime);
                    AppProperties.Threads threads = buildThreads(values, prefix);
                    return new AppProperties.Account(
                            id,
                            firstNonBlank(values.get(prefix + "LABEL"), id),
                            x,
                            threads
                    );
                })
                .toList();
    }

    private static AppProperties.X buildX(Map<String, String> values, String prefix, AppProperties.Runtime runtime) {
        String profileSuffix = prefix.isBlank()
                ? "chrome-profile"
                : normalizeAccountKey(prefix.substring("ACCOUNT_".length(), prefix.length() - 1)).toLowerCase() + "-chrome-profile";
        return new AppProperties.X(
                firstNonBlank(
                        values.get(prefix + "X_ACCOUNT_LABEL"),
                        firstNonBlank(values.get(prefix + "TWITTER_ACCOUNT_LABEL"), values.get(prefix + "LABEL"))
                ),
                values.get(prefix + "X_ACCESS_TOKEN"),
                values.get(prefix + "X_CLIENT_ID"),
                values.get(prefix + "X_CLIENT_SECRET"),
                values.getOrDefault(prefix + "X_REDIRECT_URI", "http://127.0.0.1:3000/callback"),
                values.getOrDefault(prefix + "X_SCOPES", "tweet.read tweet.write users.read"),
                values.get(prefix + "X_API_KEY"),
                values.get(prefix + "X_API_SECRET"),
                values.get(prefix + "X_ACCESS_TOKEN_SECRET"),
                values.get(prefix + "X_REFRESH_TOKEN"),
                values.getOrDefault(prefix + "X_PUBLISH_MODE", prefix.isBlank() ? values.getOrDefault("X_PUBLISH_MODE", "api") : "selenium"),
                values.getOrDefault(prefix + "X_BROWSER", values.getOrDefault("X_BROWSER", "chrome")),
                values.getOrDefault(
                        prefix + "X_BROWSER_PROFILE_DIR",
                        Path.of(runtime.dataDir(), "selenium", profileSuffix).toString()
                ),
                Boolean.parseBoolean(values.getOrDefault(prefix + "X_BROWSER_HEADLESS", values.getOrDefault("X_BROWSER_HEADLESS", "false")))
        );
    }

    private static AppProperties.Threads buildThreads(Map<String, String> values, String prefix) {
        return new AppProperties.Threads(
                firstNonBlank(values.get(prefix + "THREADS_ACCOUNT_LABEL"), values.get(prefix + "LABEL")),
                values.get(prefix + "THREADS_ACCESS_TOKEN"),
                values.get(prefix + "THREADS_USER_ID"),
                firstNonBlank(values.get(prefix + "THREADS_APP_ID"), values.get(prefix + "META_APP_ID")),
                firstNonBlank(values.get(prefix + "THREADS_APP_SECRET"), values.get(prefix + "META_APP_SECRET")),
                values.getOrDefault(prefix + "THREADS_REDIRECT_URI", "http://127.0.0.1:3001/callback")
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

    private static String normalizeAccountKey(String value) {
        return value == null ? "" : value.trim().toUpperCase().replaceAll("[^A-Z0-9]", "_");
    }
}
