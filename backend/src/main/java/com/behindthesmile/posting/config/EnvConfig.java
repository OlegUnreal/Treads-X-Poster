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
import java.util.Locale;
import java.util.stream.Stream;

@Configuration
public class EnvConfig {
    @Bean
    public AppProperties appProperties() throws IOException {
        Map<String, String> values = new HashMap<>(System.getenv());
        Path envPath = findEnvPath();

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
                if (!key.isEmpty() && key.charAt(0) == '\uFEFF') {
                    key = key.substring(1);
                }
                String value = trimmed.substring(separator + 1).trim();
                if ((value.startsWith("\"") && value.endsWith("\"")) || (value.startsWith("'") && value.endsWith("'"))) {
                    value = value.substring(1, value.length() - 1);
                }
                value = trimBOM(value);
                putIfBlank(values, key, value);
            }
        }
        if (envPath == null) {
            System.err.println("Warning: .env file was not found in expected locations. Falling back to process environment variables.");
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
                        values.getOrDefault("OPENAI_MODEL", "gpt-4.1-mini"),
                        values.getOrDefault("OPENAI_IMAGE_MODEL", "gpt-image-1"),
                        Integer.parseInt(values.getOrDefault("OPENAI_IMAGE_FILL_LIMIT", "1"))
                ),
                new AppProperties.Defaults(
                        values.getOrDefault("POST_LANGUAGE", "uk"),
                        values.getOrDefault("POST_TOPIC", "Behind The Smile"),
                        Integer.parseInt(values.getOrDefault("POST_COUNT", "3"))
                ),
                runtime,
                buildAccounts(values, runtime, defaultX, defaultThreads),
                defaultX,
                defaultThreads
        );
    }

    private static Path findEnvPath() {
        Path workDir = Path.of(System.getProperty("user.dir")).toAbsolutePath().normalize();
        String[] candidates = {
                "backend/config/.env",
                "config/.env",
                ".env",
                "../.env",
                "../config/.env",
                "../backend/config/.env"
        };
        for (int depth = 0; depth <= 3; depth++) {
            for (String candidate : candidates) {
                Path path = workDir.resolve(candidate).normalize();
                if (Files.exists(path)) {
                    return path;
                }
            }
            workDir = workDir.getParent();
            if (workDir == null) {
                break;
            }
        }
        return null;
    }

    private static String trimBOM(String value) {
        if (value == null || value.isEmpty()) {
            return value;
        }
        if (!value.isBlank() && value.charAt(0) == '\uFEFF') {
            return value.substring(1);
        }
        return value;
    }

    private static List<AppProperties.Account> buildAccounts(
            Map<String, String> values,
            AppProperties.Runtime runtime,
            AppProperties.X defaultX,
            AppProperties.Threads defaultThreads
    ) {
        String accountIds = values.get("SOCIAL_ACCOUNTS");
        if (accountIds == null || accountIds.isBlank()) {
            boolean hasXConfig = hasAnyCredential(
                    values,
                    "X_ACCESS_TOKEN", "X_CLIENT_ID", "X_CLIENT_SECRET", "X_API_KEY",
                    "X_API_SECRET", "X_ACCESS_TOKEN_SECRET", "X_REFRESH_TOKEN", "X_ACCOUNT_LABEL"
            );
            boolean hasThreadsConfig = hasAnyCredential(
                    values,
                    "THREADS_ACCESS_TOKEN", "THREADS_USER_ID", "THREADS_ACCOUNT_LABEL",
                    "THREADS_APP_ID", "THREADS_APP_SECRET", "META_APP_ID", "META_APP_SECRET"
            );
            if (!hasXConfig && !hasThreadsConfig) {
                return List.of();
            }
            String legacyLabel = firstNonBlank(
                    values.get("ACCOUNT_LABEL"),
                    values.get("SOCIAL_ACCOUNT_LABEL"),
                    values.get("X_ACCOUNT_LABEL"),
                    values.get("THREADS_ACCOUNT_LABEL"),
                    "Publishing account"
            );
            String legacyId = firstNonBlank(values.get("ACCOUNT_ID"), safeLegacyAccountId(legacyLabel));
            return buildProfileAccounts(values, runtime, legacyId, legacyLabel, defaultX, defaultThreads, null);
        }

        return Stream.of(accountIds.split(","))
                .map(String::trim)
                .filter(id -> !id.isBlank())
                .map(id -> {
                    String prefix = "ACCOUNT_" + normalizeAccountKey(id) + "_";
                    AppProperties.X x = buildX(values, prefix, runtime);
                    AppProperties.Threads threads = buildThreads(values, prefix);
                    String accountLabel = firstNonBlank(values.get(prefix + "LABEL"), id);
                    return buildProfileAccounts(values, runtime, id, accountLabel, x, threads, id).getFirst();
                })
                .toList();
    }

    private static List<AppProperties.Account> buildProfileAccounts(
            Map<String, String> values,
            AppProperties.Runtime runtime,
            String sourceId,
            String label,
            AppProperties.X x,
            AppProperties.Threads threads,
            String accountIdSuffix
    ) {
        boolean hasExplicitX = hasAnyCredential(
                values,
                "X_ACCESS_TOKEN", "X_CLIENT_ID", "X_CLIENT_SECRET", "X_API_KEY",
                "X_API_SECRET", "X_ACCESS_TOKEN_SECRET", "X_REFRESH_TOKEN"
        );
        boolean hasExplicitThreads = hasAnyCredential(
                values,
                "THREADS_ACCESS_TOKEN", "THREADS_USER_ID", "THREADS_APP_ID", "THREADS_APP_SECRET"
        );
        AppProperties.X emptyX = new AppProperties.X(null, null, null, null, null, null, null, null, null, null, "selenium", "chrome", "", false);
        AppProperties.Threads emptyThreads = new AppProperties.Threads(null, null, null, null, null, null);
        String idSuffix = accountIdSuffix == null ? sourceId : accountIdSuffix;

        if (hasExplicitX && hasExplicitThreads) {
            String xId = buildPlatformAccountId(values, idSuffix, "x");
            String threadsId = buildPlatformAccountId(values, idSuffix, "threads");
            return List.of(
                    new AppProperties.Account(
                            xId,
                            firstNonBlank(firstNonBlank(values.get("X_ACCOUNT_LABEL"), label), sourceId) + " (X)",
                            x,
                            emptyThreads
                    ),
                    new AppProperties.Account(
                            threadsId,
                            firstNonBlank(firstNonBlank(values.get("THREADS_ACCOUNT_LABEL"), label), sourceId) + " (Threads)",
                            emptyX,
                            threads
                    )
            );
        }

        AppProperties.X platformX = hasExplicitX ? x : emptyX;
        AppProperties.Threads platformThreads = hasExplicitThreads ? threads : emptyThreads;
        return List.of(new AppProperties.Account(
                idSuffix,
                label,
                platformX,
                platformThreads
        ));
    }

    private static String buildPlatformAccountId(Map<String, String> values, String sourceId, String platform) {
        String customId = values.get(platform.toUpperCase() + "_ACCOUNT_ID");
        if (customId != null && !customId.isBlank()) {
            return sanitizeAccountId(customId);
        }
        if (sourceId != null && !sourceId.isBlank()) {
            return sanitizeAccountId(sourceId + "-" + platform);
        }
        return safeLegacyAccountId("account-" + platform);
    }

    private static String sanitizeAccountId(String value) {
        return value == null ? null : value.trim().toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9_-]+", "-");
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

    private static String firstNonBlank(String... values) {
        if (values == null) {
            return null;
        }
        for (String value : values) {
            if (value != null && !value.isBlank()) {
                return value;
            }
        }
        return null;
    }

    private static boolean hasAnyCredential(Map<String, String> values, String... keys) {
        if (keys == null || keys.length == 0) {
            return false;
        }
        for (String key : keys) {
            if (values.getOrDefault(key, "").trim().length() > 0) {
                return true;
            }
        }
        return false;
    }

    private static String normalizeAccountKey(String value) {
        return value == null ? "" : value.trim().toUpperCase().replaceAll("[^A-Z0-9]", "_");
    }

    private static String safeLegacyAccountId(String label) {
        String safe = label == null ? "" : label.trim().toLowerCase().replaceAll("[^a-z0-9_-]+", "-");
        safe = safe.replaceAll("^-+|-+$", "");
        return safe.isBlank() ? "legacy-account" : safe;
    }

    private static void putIfBlank(Map<String, String> values, String key, String value) {
        if (value == null) {
            return;
        }
        String current = values.get(key);
        if (current == null || current.isBlank()) {
            values.put(key, value);
        }
    }
}
