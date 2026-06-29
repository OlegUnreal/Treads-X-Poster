package com.behindthesmile.posting.service;

import com.behindthesmile.posting.api.AccountSelectionResponse;
import com.behindthesmile.posting.api.PublisherAccountOption;
import com.behindthesmile.posting.config.AppProperties;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.concurrent.Callable;

@Service
public class AccountConfigService {
    private final AppProperties appProperties;
    private final AppPathService appPathService;
    private final ThreadLocal<String> activeAccountOverride = new ThreadLocal<>();

    public AccountConfigService(AppProperties appProperties, AppPathService appPathService) {
        this.appProperties = appProperties;
        this.appPathService = appPathService;
    }

    public AppProperties.Account activeAccount() {
        String activeId = activeAccountOverride.get() == null ? readActiveAccountId() : activeAccountOverride.get();
        return appProperties.accounts().stream()
                .filter(account -> account.id().equals(activeId))
                .findFirst()
                .orElseGet(() -> appProperties.accounts().isEmpty()
                        ? new AppProperties.Account("default", "Default account", appProperties.x(), appProperties.threads())
                        : appProperties.accounts().getFirst());
    }

    public AccountSelectionResponse selection() {
        return new AccountSelectionResponse(
                activeAccount().id(),
                appProperties.accounts().stream().map(this::toOption).toList()
        );
    }

    public AccountSelectionResponse switchActiveAccount(String accountId) throws IOException {
        if (accountId == null || accountId.isBlank()) {
            throw new IllegalStateException("Account id is required.");
        }

        AppProperties.Account account = appProperties.accounts().stream()
                .filter(candidate -> candidate.id().equals(accountId))
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("Unknown account: " + accountId));

        Files.createDirectories(appPathService.activeAccountPath().getParent());
        Files.writeString(appPathService.activeAccountPath(), account.id(), StandardCharsets.UTF_8);
        return selection();
    }

    public <T> T withAccount(String accountId, Callable<T> action) throws Exception {
        AppProperties.Account account = requireAccount(accountId);
        String previous = activeAccountOverride.get();
        activeAccountOverride.set(account.id());
        try {
            return action.call();
        } finally {
            if (previous == null) {
                activeAccountOverride.remove();
            } else {
                activeAccountOverride.set(previous);
            }
        }
    }

    public AppProperties.Account requireAccount(String accountId) {
        if (accountId == null || accountId.isBlank()) {
            throw new IllegalStateException("Account id is required.");
        }
        return appProperties.accounts().stream()
                .filter(candidate -> candidate.id().equals(accountId))
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("Unknown account: " + accountId));
    }

    public List<String> resolveTargetAccountIds(List<String> requestedAccountIds) {
        if (requestedAccountIds == null || requestedAccountIds.isEmpty()) {
            return List.of(activeAccount().id());
        }

        LinkedHashSet<String> normalized = new LinkedHashSet<>();
        for (String accountId : requestedAccountIds) {
            if (accountId == null || accountId.isBlank()) {
                continue;
            }
            String trimmed = accountId.trim();
            requireAccount(trimmed);
            normalized.add(trimmed);
        }

        if (normalized.isEmpty()) {
            return List.of(activeAccount().id());
        }
        return new ArrayList<>(normalized);
    }

    public PublisherAccountOption activeAccountOption() {
        return toOption(activeAccount());
    }

    public List<PublisherAccountOption> accountOptions() {
        return appProperties.accounts().stream().map(this::toOption).toList();
    }

    private PublisherAccountOption toOption(AppProperties.Account account) {
        return new PublisherAccountOption(
                account.id(),
                firstNonBlank(account.label(), account.id()),
                resolveXAccountLabel(account.x()),
                resolveXModeLabel(account.x()),
                resolveThreadsAccountLabel(account.threads())
        );
    }

    private String readActiveAccountId() {
        try {
            if (Files.exists(appPathService.activeAccountPath())) {
                return Files.readString(appPathService.activeAccountPath(), StandardCharsets.UTF_8).trim();
            }
        } catch (IOException ignored) {
            return null;
        }
        return appProperties.accounts().isEmpty() ? "default" : appProperties.accounts().getFirst().id();
    }

    private String resolveXAccountLabel(AppProperties.X x) {
        if (x.accountLabel() != null && !x.accountLabel().isBlank()) {
            return x.accountLabel().trim();
        }
        if ("selenium".equalsIgnoreCase(firstNonBlank(x.publishMode(), "api"))) {
            return "X account from Selenium profile";
        }
        if (x.accessToken() != null || x.apiKey() != null) {
            return "Configured X account";
        }
        return "X account is not configured";
    }

    private String resolveXModeLabel(AppProperties.X x) {
        String publishMode = firstNonBlank(x.publishMode(), "api").trim().toLowerCase();
        return switch (publishMode) {
            case "selenium" -> "Posting through Selenium browser";
            case "auto" -> "API first, Selenium fallback";
            default -> "Posting through X API";
        };
    }

    private String resolveThreadsAccountLabel(AppProperties.Threads threads) {
        if (threads.accountLabel() != null && !threads.accountLabel().isBlank()) {
            return threads.accountLabel().trim();
        }
        if (threads.userId() != null && !threads.userId().isBlank()) {
            return "Threads user ID ending in " + trailing(threads.userId(), 4);
        }
        if (threads.accessToken() != null && !threads.accessToken().isBlank()) {
            return "Configured Threads account";
        }
        return "Threads account is not configured";
    }

    private String trailing(String value, int size) {
        if (value == null || value.isBlank()) {
            return "unknown";
        }
        return value.length() <= size ? value : value.substring(value.length() - size);
    }

    private String firstNonBlank(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value;
    }
}
