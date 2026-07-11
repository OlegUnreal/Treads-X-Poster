package com.behindthesmile.posting.service;

import com.behindthesmile.posting.api.AccountConfigRequest;
import com.behindthesmile.posting.api.AccountConfigResponse;
import com.behindthesmile.posting.api.AccountSelectionResponse;
import com.behindthesmile.posting.api.PublisherAccountOption;
import com.behindthesmile.posting.config.AppProperties;
import com.behindthesmile.posting.persistence.AccountSetting;
import com.behindthesmile.posting.persistence.AccountSettingRepository;
import com.behindthesmile.posting.persistence.AppSetting;
import com.behindthesmile.posting.persistence.AppSettingRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import jakarta.annotation.PostConstruct;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.stream.Collectors;

@Service
public class AccountConfigService {
    private static final String LEGACY_DEFAULT_ACCOUNT_ID = "default";
    private static final String ACTIVE_ACCOUNT_SETTING_KEY = "active_account_id";

    private final AppProperties appProperties;
    private final AppPathService appPathService;
    private final ObjectMapper objectMapper;
    private final AccountSettingRepository accountSettingRepository;
    private final AppSettingRepository appSettingRepository;
    private final ThreadLocal<String> activeAccountOverride = new ThreadLocal<>();

    public AccountConfigService(
            AppProperties appProperties,
            AppPathService appPathService,
            ObjectMapper objectMapper,
            AccountSettingRepository accountSettingRepository,
            AppSettingRepository appSettingRepository
    ) {
        this.appProperties = appProperties;
        this.appPathService = appPathService;
        this.objectMapper = objectMapper.copy().enable(SerializationFeature.INDENT_OUTPUT);
        this.accountSettingRepository = accountSettingRepository;
        this.appSettingRepository = appSettingRepository;
    }

    @PostConstruct
    private void migrateLegacyFileStorage() {
        readLegacyStoredAccounts().forEach((id, account) -> {
            if (!accountSettingRepository.existsById(id)) {
                accountSettingRepository.save(toAccountSetting(account));
            }
        });
        try {
            Files.deleteIfExists(appPathService.accountSettingsPath());
        } catch (IOException ignored) {
        }

        String legacyActive = readActiveAccountFile();
        if (legacyActive != null) {
            AppSetting setting = appSettingRepository.findById(ACTIVE_ACCOUNT_SETTING_KEY).orElseGet(() -> new AppSetting(ACTIVE_ACCOUNT_SETTING_KEY, null));
            if (legacyActive.equals(setting.getValue())) {
                return;
            }
            setting.setValue(legacyActive);
            appSettingRepository.save(setting);
            try {
                Files.deleteIfExists(appPathService.activeAccountPath());
            } catch (IOException ignored) {
            }
        }
    }

    public AppProperties.Account activeAccount() {
        String activeId = activeAccountOverride.get() == null ? readActiveAccountId() : activeAccountOverride.get();
        List<AppProperties.Account> accounts = accounts();
        if (activeId != null && !activeId.isBlank()) {
            return accounts.stream()
                    .filter(account -> !isLegacyDefaultAccount(account.id(), account.label()))
                    .filter(account -> account.id().equals(activeId))
                    .findFirst()
                    .orElseGet(() -> fallbackToFirstAccount(accounts, activeId));
        }

        if (!accounts.isEmpty()) {
            return accounts.getFirst();
        }
        throw new IllegalStateException("No accounts configured. Add one via /api/accounts/config or SOCIAL_ACCOUNTS env variable.");
    }

    public AccountSelectionResponse selection() {
        List<AppProperties.Account> accounts = accounts();
        return new AccountSelectionResponse(
                accounts.isEmpty() ? "" : activeAccount().id(),
                accounts.stream().map(this::toOption).toList()
        );
    }

    public AccountSelectionResponse switchActiveAccount(String accountId) throws IOException {
        if (accountId == null || accountId.isBlank()) {
            throw new IllegalStateException("Account id is required.");
        }
        AppProperties.Account account = accounts().stream()
                .filter(candidate -> candidate.id().equals(accountId))
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("Unknown account: " + accountId));

        writeActiveAccountId(account.id());
        return selection();
    }

    public List<AppProperties.Account> accounts() {
        LinkedHashMap<String, AppProperties.Account> merged = new LinkedHashMap<>();
        for (AppProperties.Account account : appProperties.accounts()) {
            if (isLegacyDefaultAccount(account.id(), account.label())) {
                continue;
            }
            merged.put(account.id(), account);
        }
        for (AccountSetting setting : accountSettingRepository.findAll()) {
            if (isLegacyDefaultAccount(setting.getId(), setting.getLabel())) {
                continue;
            }
            merged.put(setting.getId(), toAccount(setting));
        }
        return new ArrayList<>(merged.values());
    }

    public List<AccountConfigResponse> accountConfigs() {
        Map<String, AppProperties.Account> envById = appProperties.accounts().stream()
                .filter(account -> !isLegacyDefaultAccount(account.id(), account.label()))
                .collect(Collectors.toMap(AppProperties.Account::id, account -> account, (a, b) -> b, LinkedHashMap::new));

        Map<String, AccountSetting> uiById = accountSettingRepository.findAll().stream()
                .filter(account -> !isLegacyDefaultAccount(account.getId(), account.getLabel()))
                .collect(Collectors.toMap(AccountSetting::getId, account -> account, (a, b) -> b, LinkedHashMap::new));

        List<AccountConfigResponse> responses = new ArrayList<>();
        Set<String> handled = new LinkedHashSet<>();
        for (Map.Entry<String, AppProperties.Account> entry : envById.entrySet()) {
            String accountId = entry.getKey();
            AppProperties.Account account = entry.getValue();
            AccountSetting ui = uiById.remove(accountId);
            if (ui == null) {
                responses.add(toResponse(
                        account,
                        "env",
                        defaultPrompt(account),
                        appProperties.defaults().language(),
                        appProperties.defaults().count()
                ));
            } else {
                responses.add(toResponse(ui));
                handled.add(accountId);
            }
        }
        for (AccountSetting setting : uiById.values()) {
            responses.add(toResponse(setting));
            handled.add(setting.getId());
        }
        return responses.stream()
                .filter(config -> !isLegacyDefaultAccount(config.id(), ""))
                .toList();
    }

    public AccountConfigResponse upsertAccount(AccountConfigRequest request) throws IOException {
        StoredAccount account = normalizeRequest(request);
        AccountSetting entity = toAccountSetting(account);
        AccountSetting saved = accountSettingRepository.save(entity);
        if (saved.getId().equals(readActiveAccountId()) && activeAccountOverride.get() == null) {
            writeActiveAccountId(saved.getId());
        }
        return toResponse(saved);
    }

    public void deleteUiAccount(String accountId) throws IOException {
        if (isLegacyDefaultAccount(accountId, null)) {
            throw new IllegalStateException("Only UI-managed accounts can be deleted here.");
        }
        if (accountId == null || accountId.isBlank()) {
            throw new IllegalStateException("Account id is required.");
        }
        if (!accountSettingRepository.existsById(accountId)) {
            throw new IllegalStateException("Only UI-managed accounts can be deleted here.");
        }
        accountSettingRepository.deleteById(accountId);
        String activeId = readActiveAccountId();
        if (accountId.equals(activeId)) {
            appSettingRepository.findById(ACTIVE_ACCOUNT_SETTING_KEY)
                    .ifPresent(setting -> {
                        setting.setValue("");
                        appSettingRepository.save(setting);
                    });
        }
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
        return accounts().stream()
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
        return accounts().stream().map(this::toOption).toList();
    }

    public String accountPrompt(String accountId) {
        return accountConfigs().stream()
                .filter(account -> account.id().equals(accountId))
                .map(AccountConfigResponse::prompt)
                .findFirst()
                .orElse("");
    }

    private PublisherAccountOption toOption(AppProperties.Account account) {
        return new PublisherAccountOption(
                account.id(),
                firstNonBlank(account.label(), account.id()),
                resolveXAccountLabel(account.x()),
                resolveXModeLabel(account.x()),
                resolveThreadsAccountLabel(account)
        );
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
        String publishMode = firstNonBlank(x.publishMode(), "api").trim().toLowerCase(Locale.ROOT);
        return switch (publishMode) {
            case "selenium" -> "Posting through Selenium browser";
            case "auto" -> "API first, Selenium fallback";
            default -> "Posting through X API";
        };
    }

    private String resolveThreadsAccountLabel(AppProperties.Account account) {
        AppProperties.Threads threads = account.threads();
        if (threads.accountLabel() != null && !threads.accountLabel().isBlank()) {
            return threads.accountLabel().trim();
        }
        if (threads.userId() != null && !threads.userId().isBlank()) {
            return firstNonBlank(account.label(), account.id());
        }
        if (threads.accessToken() != null && !threads.accessToken().isBlank()) {
            return firstNonBlank(account.label(), "Configured Threads account");
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

    private Integer normalizedCount(Integer value, Integer fallback) {
        Integer resolvedFallback = fallback == null || fallback < 1 ? appProperties.defaults().count() : fallback;
        return value == null || value < 1 ? resolvedFallback : value;
    }

    private AppProperties.Account fallbackToFirstAccount(List<AppProperties.Account> accounts, String activeId) {
        return accounts.stream()
                .filter(account -> !isLegacyDefaultAccount(account.id(), account.label()))
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("Configured account not found: " + activeId));
    }

    private AppProperties.Account toAccount(AccountSetting setting) {
        return new AppProperties.Account(
                setting.getId(),
                setting.getLabel(),
                toX(setting),
                toThreads(setting)
        );
    }

    private AppProperties.X toX(AccountSetting setting) {
        return new AppProperties.X(
                setting.getXAccountLabel(),
                setting.getXAccessToken(),
                setting.getXClientId(),
                setting.getXClientSecret(),
                firstNonBlank(setting.getXRedirectUri(), "http://127.0.0.1:3000/callback"),
                firstNonBlank(setting.getXScopes(), "tweet.read tweet.write users.read"),
                setting.getXApiKey(),
                setting.getXApiSecret(),
                setting.getXAccessTokenSecret(),
                setting.getXRefreshToken(),
                firstNonBlank(setting.getXPublishMode(), "selenium"),
                firstNonBlank(setting.getXBrowser(), "chrome"),
                firstNonBlank(setting.getXBrowserProfileDir(), appPathService.dataDir().resolve("selenium").resolve("chrome-profile").toString()),
                setting.isXBrowserHeadless()
        );
    }

    private AppProperties.Threads toThreads(AccountSetting setting) {
        return new AppProperties.Threads(
                setting.getThreadsAccountLabel(),
                setting.getThreadsAccessToken(),
                setting.getThreadsUserId(),
                setting.getThreadsAppId(),
                setting.getThreadsAppSecret(),
                firstNonBlank(setting.getThreadsRedirectUri(), "http://127.0.0.1:3001/callback")
        );
    }

    private AppProperties.Account toAccount(String id, String label, String prompt, String language, Integer count, StoredX x, StoredThreads threads) {
        return new AppProperties.Account(
                id,
                label,
                toX(x),
                toThreads(threads)
        );
    }

    private AppProperties.X toX(StoredX x) {
        StoredX value = x == null ? new StoredX(null, null, null, null, null, null, null, null, null, null, "selenium", "chrome", null, false, null, null, null) : x;
        return new AppProperties.X(
                value.accountLabel(),
                value.accessToken(),
                value.clientId(),
                value.clientSecret(),
                firstNonBlank(value.redirectUri(), "http://127.0.0.1:3000/callback"),
                firstNonBlank(value.scopes(), "tweet.read tweet.write users.read"),
                value.apiKey(),
                value.apiSecret(),
                value.accessTokenSecret(),
                value.refreshToken(),
                firstNonBlank(value.publishMode(), "selenium"),
                firstNonBlank(value.browser(), "chrome"),
                firstNonBlank(value.browserProfileDir(), appPathService.dataDir().resolve("selenium").resolve("chrome-profile").toString()),
                value.browserHeadless()
        );
    }

    private AppProperties.Threads toThreads(StoredThreads threads) {
        StoredThreads value = threads == null ? new StoredThreads(null, null, null, null, null, null, null, null, null) : threads;
        return new AppProperties.Threads(
                value.accountLabel(),
                value.accessToken(),
                value.userId(),
                value.appId(),
                value.appSecret(),
                firstNonBlank(value.redirectUri(), "http://127.0.0.1:3001/callback")
        );
    }

    private AccountConfigResponse toResponse(AppProperties.Account account, String source, String prompt, String language, Integer count) {
        return new AccountConfigResponse(
                account.id(),
                account.label(),
                source,
                prompt,
                language,
                count,
                prompt,
                language,
                count,
                account.x().accountLabel(),
                account.x().accessToken(),
                account.x().clientId(),
                account.x().clientSecret(),
                account.x().redirectUri(),
                account.x().scopes(),
                account.x().apiKey(),
                account.x().apiSecret(),
                account.x().accessTokenSecret(),
                account.x().refreshToken(),
                account.x().publishMode(),
                account.x().browser(),
                account.x().browserProfileDir(),
                account.x().browserHeadless(),
                prompt,
                language,
                count,
                account.threads().accountLabel(),
                account.threads().accessToken(),
                account.threads().userId(),
                account.threads().appId(),
                account.threads().appSecret(),
                account.threads().redirectUri()
        );
    }

    private AccountConfigResponse toResponse(AccountSetting account) {
        String prompt = firstNonBlank(account.getPrompt(), defaultPrompt(account.getLabel()));
        String language = firstNonBlank(account.getLanguage(), appProperties.defaults().language());
        Integer defaultCount = normalizedCount(account.getDefaultPostCount(), appProperties.defaults().count());

        AppProperties.Account converted = toAccount(account);
        return new AccountConfigResponse(
                converted.id(),
                converted.label(),
                "ui",
                prompt,
                language,
                defaultCount,
                firstNonBlank(account.getXPrompt(), prompt),
                firstNonBlank(account.getXLanguage(), language),
                normalizedCount(account.getXDefaultPostCount(), defaultCount),
                converted.x().accountLabel(),
                converted.x().accessToken(),
                converted.x().clientId(),
                converted.x().clientSecret(),
                converted.x().redirectUri(),
                converted.x().scopes(),
                converted.x().apiKey(),
                converted.x().apiSecret(),
                converted.x().accessTokenSecret(),
                converted.x().refreshToken(),
                converted.x().publishMode(),
                converted.x().browser(),
                converted.x().browserProfileDir(),
                converted.x().browserHeadless(),
                firstNonBlank(account.getThreadsPrompt(), prompt),
                firstNonBlank(account.getThreadsLanguage(), language),
                normalizedCount(account.getThreadsDefaultPostCount(), defaultCount),
                converted.threads().accountLabel(),
                converted.threads().accessToken(),
                converted.threads().userId(),
                converted.threads().appId(),
                converted.threads().appSecret(),
                converted.threads().redirectUri()
        );
    }

    private String defaultPrompt(AppProperties.Account account) {
        return defaultPrompt(firstNonBlank(account.label(), account.id()));
    }

    private String defaultPrompt(String label) {
        return String.join("\n",
                "Write posts for " + firstNonBlank(label, "this account") + ".",
                "Keep the voice personal and specific.",
                "Avoid generic marketing language.",
                "Write like a real person who knows the account and its audience.",
                "Use Ukrainian unless the account prompt says otherwise."
        );
    }

    private StoredAccount normalizeRequest(AccountConfigRequest request) {
        if (request == null) {
            throw new IllegalStateException("Account settings are required.");
        }
        String id = normalizeAccountId(request.id(), request.label());
        String label = firstNonBlank(request.label(), id).trim();
        String publishMode = firstNonBlank(request.xPublishMode(), "selenium").trim().toLowerCase(Locale.ROOT);
        String browserProfileDir = firstNonBlank(
                request.xBrowserProfileDir(),
                appPathService.dataDir().resolve("selenium").resolve(id + "-chrome-profile").toString()
        );

        String accountPrompt = firstNonBlank(request.prompt(), defaultPrompt(label));
        String accountLanguage = firstNonBlank(request.language(), appProperties.defaults().language());
        int accountCount = request.defaultPostCount() == null || request.defaultPostCount() < 1
                ? appProperties.defaults().count()
                : request.defaultPostCount();

        return new StoredAccount(
                id,
                label,
                accountPrompt,
                accountLanguage,
                accountCount,
                new StoredX(
                        request.xAccountLabel(),
                        request.xAccessToken(),
                        request.xClientId(),
                        request.xClientSecret(),
                        firstNonBlank(request.xRedirectUri(), "http://127.0.0.1:3000/callback"),
                        firstNonBlank(request.xScopes(), "tweet.read tweet.write users.read"),
                        request.xApiKey(),
                        request.xApiSecret(),
                        request.xAccessTokenSecret(),
                        request.xRefreshToken(),
                        publishMode,
                        firstNonBlank(request.xBrowser(), "chrome"),
                        browserProfileDir,
                        Boolean.TRUE.equals(request.xBrowserHeadless()),
                        firstNonBlank(request.xPrompt(), firstNonBlank(request.prompt(), accountPrompt)),
                        firstNonBlank(request.xLanguage(), accountLanguage),
                        normalizedCount(request.xDefaultPostCount(), accountCount)
                ),
                new StoredThreads(
                        request.threadsAccountLabel(),
                        request.threadsAccessToken(),
                        request.threadsUserId(),
                        request.threadsAppId(),
                        request.threadsAppSecret(),
                        firstNonBlank(request.threadsRedirectUri(), "http://127.0.0.1:3001/callback"),
                        firstNonBlank(request.threadsPrompt(), firstNonBlank(request.prompt(), accountPrompt)),
                        firstNonBlank(request.threadsLanguage(), accountLanguage),
                        normalizedCount(request.threadsDefaultPostCount(), accountCount)
                )
        );
    }

    private String normalizeAccountId(String requestedId, String label) {
        String source = firstNonBlank(requestedId, label);
        if (source == null || source.isBlank()) {
            throw new IllegalStateException("Account name is required.");
        }
        String normalized = source.trim().toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9_-]+", "-");
        normalized = normalized.replaceAll("^-+|-+$", "");
        if (LEGACY_DEFAULT_ACCOUNT_ID.equals(normalized)) {
            throw new IllegalStateException("Account id \"default\" is reserved. Choose another account id.");
        }
        if (normalized.isBlank()) {
            throw new IllegalStateException("Account id must contain letters or numbers.");
        }
        return normalized;
    }

    private boolean isLegacyDefaultAccount(String accountId, String label) {
        String id = firstNonBlank(accountId, "").trim().toLowerCase(Locale.ROOT);
        String accountLabel = firstNonBlank(label, "").trim().toLowerCase(Locale.ROOT);
        return LEGACY_DEFAULT_ACCOUNT_ID.equals(id) || LEGACY_DEFAULT_ACCOUNT_ID.equals(accountLabel);
    }

    private String readActiveAccountId() {
        return appSettingRepository.findByKey(ACTIVE_ACCOUNT_SETTING_KEY)
                .map(AppSetting::getValue)
                .filter(value -> !isLegacyDefaultAccount(value, value))
                .orElse("");
    }

    private void writeActiveAccountId(String accountId) {
        AppSetting setting = appSettingRepository.findById(ACTIVE_ACCOUNT_SETTING_KEY)
                .orElseGet(() -> new AppSetting(ACTIVE_ACCOUNT_SETTING_KEY, ""));
        setting.setValue(firstNonBlank(accountId, ""));
        appSettingRepository.save(setting);
    }

    private String readActiveAccountFile() {
        try {
            Path path = appPathService.activeAccountPath();
            if (Files.exists(path)) {
                return Files.readString(path).trim();
            }
        } catch (IOException ignored) {
            return null;
        }
        return null;
    }

    private Map<String, StoredAccount> readLegacyStoredAccounts() {
        Path path = appPathService.accountSettingsPath();
        if (!Files.exists(path)) {
            return Map.of();
        }
        try {
            StoredAccountsFile file = objectMapper.readValue(Files.readString(path), StoredAccountsFile.class);
            List<StoredAccount> rawAccounts = file.accounts() == null ? List.of() : file.accounts();
            return rawAccounts.stream()
                    .filter(account -> !isLegacyDefaultAccount(account.id(), account.label()))
                    .collect(Collectors.toMap(
                            StoredAccount::id,
                            account -> account,
                            (a, b) -> b,
                            LinkedHashMap::new
                    ));
        } catch (Exception ex) {
            throw new IllegalStateException("Could not read account settings from legacy file: " + ex.getMessage(), ex);
        }
    }

    private AccountSetting toAccountSetting(StoredAccount account) {
        AccountSetting setting = new AccountSetting();
        setting.setId(account.id());
        setting.setLabel(account.label());
        setting.setPrompt(account.prompt());
        setting.setLanguage(account.language());
        setting.setDefaultPostCount(account.defaultPostCount());

        StoredX x = account.x();
        setting.setXPrompt(x == null ? null : x.prompt());
        setting.setXLanguage(x == null ? null : x.language());
        setting.setXDefaultPostCount(x == null ? null : x.defaultPostCount());
        setting.setXAccountLabel(x == null ? null : x.accountLabel());
        setting.setXAccessToken(x == null ? null : x.accessToken());
        setting.setXClientId(x == null ? null : x.clientId());
        setting.setXClientSecret(x == null ? null : x.clientSecret());
        setting.setXRedirectUri(x == null ? null : x.redirectUri());
        setting.setXScopes(x == null ? null : x.scopes());
        setting.setXApiKey(x == null ? null : x.apiKey());
        setting.setXApiSecret(x == null ? null : x.apiSecret());
        setting.setXAccessTokenSecret(x == null ? null : x.accessTokenSecret());
        setting.setXRefreshToken(x == null ? null : x.refreshToken());
        setting.setXPublishMode(x == null ? null : x.publishMode());
        setting.setXBrowser(x == null ? null : x.browser());
        setting.setXBrowserProfileDir(x == null ? null : x.browserProfileDir());
        setting.setXBrowserHeadless(x == null ? false : x.browserHeadless());
        setting.setThreadsPrompt(x == null ? null : account.threads().prompt());

        StoredThreads threads = account.threads();
        setting.setThreadsPrompt(threads == null ? setting.getThreadsPrompt() : threads.prompt());
        setting.setThreadsLanguage(threads == null ? null : threads.language());
        setting.setThreadsDefaultPostCount(threads == null ? null : threads.defaultPostCount());
        setting.setThreadsAccountLabel(threads == null ? null : threads.accountLabel());
        setting.setThreadsAccessToken(threads == null ? null : threads.accessToken());
        setting.setThreadsUserId(threads == null ? null : threads.userId());
        setting.setThreadsAppId(threads == null ? null : threads.appId());
        setting.setThreadsAppSecret(threads == null ? null : threads.appSecret());
        setting.setThreadsRedirectUri(threads == null ? null : threads.redirectUri());
        return setting;
    }

    private record StoredAccountsFile(List<StoredAccount> accounts) {}

    private record StoredAccount(
            String id,
            String label,
            String prompt,
            String language,
            Integer defaultPostCount,
            StoredX x,
            StoredThreads threads
    ) {}

    private record StoredX(
            String accountLabel,
            String accessToken,
            String clientId,
            String clientSecret,
            String redirectUri,
            String scopes,
            String apiKey,
            String apiSecret,
            String accessTokenSecret,
            String refreshToken,
            String publishMode,
            String browser,
            String browserProfileDir,
            boolean browserHeadless,
            String prompt,
            String language,
            Integer defaultPostCount
    ) {}

    private record StoredThreads(
            String accountLabel,
            String accessToken,
            String userId,
            String appId,
            String appSecret,
            String redirectUri,
            String prompt,
            String language,
            Integer defaultPostCount
    ) {}
}
