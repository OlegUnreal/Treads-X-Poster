package com.behindthesmile.posting.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "social_accounts")
public class AccountSetting {
    @Id
    @Column(name = "account_id", length = 128, nullable = false)
    private String id;

    @Column(name = "label", length = 255, nullable = false)
    private String label;

    @Column(name = "prompt", length = 4000)
    private String prompt;

    @Column(name = "language", length = 32)
    private String language;

    @Column(name = "default_post_count")
    private Integer defaultPostCount;

    @Column(name = "x_prompt", length = 4000)
    private String xPrompt;
    @Column(name = "x_language", length = 32)
    private String xLanguage;
    @Column(name = "x_default_post_count")
    private Integer xDefaultPostCount;
    @Column(name = "x_account_label", length = 255)
    private String xAccountLabel;
    @Column(name = "x_access_token", length = 4000)
    private String xAccessToken;
    @Column(name = "x_client_id", length = 512)
    private String xClientId;
    @Column(name = "x_client_secret", length = 1024)
    private String xClientSecret;
    @Column(name = "x_redirect_uri", length = 255)
    private String xRedirectUri;
    @Column(name = "x_scopes", length = 255)
    private String xScopes;
    @Column(name = "x_api_key", length = 255)
    private String xApiKey;
    @Column(name = "x_api_secret", length = 1024)
    private String xApiSecret;
    @Column(name = "x_access_token_secret", length = 1024)
    private String xAccessTokenSecret;
    @Column(name = "x_refresh_token", length = 1024)
    private String xRefreshToken;
    @Column(name = "x_publish_mode", length = 64)
    private String xPublishMode;
    @Column(name = "x_browser", length = 64)
    private String xBrowser;
    @Column(name = "x_browser_profile_dir", length = 1024)
    private String xBrowserProfileDir;
    @Column(name = "x_browser_headless")
    private boolean xBrowserHeadless;

    @Column(name = "threads_prompt", length = 4000)
    private String threadsPrompt;
    @Column(name = "threads_language", length = 32)
    private String threadsLanguage;
    @Column(name = "threads_default_post_count")
    private Integer threadsDefaultPostCount;
    @Column(name = "threads_account_label", length = 255)
    private String threadsAccountLabel;
    @Column(name = "threads_access_token", length = 4000)
    private String threadsAccessToken;
    @Column(name = "threads_user_id", length = 255)
    private String threadsUserId;
    @Column(name = "threads_app_id", length = 255)
    private String threadsAppId;
    @Column(name = "threads_app_secret", length = 1024)
    private String threadsAppSecret;
    @Column(name = "threads_redirect_uri", length = 255)
    private String threadsRedirectUri;

    @Column(name = "source", length = 64)
    private String source = "ui";

    public AccountSetting() {
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getLabel() {
        return label;
    }

    public void setLabel(String label) {
        this.label = label;
    }

    public String getPrompt() {
        return prompt;
    }

    public void setPrompt(String prompt) {
        this.prompt = prompt;
    }

    public String getLanguage() {
        return language;
    }

    public void setLanguage(String language) {
        this.language = language;
    }

    public Integer getDefaultPostCount() {
        return defaultPostCount;
    }

    public void setDefaultPostCount(Integer defaultPostCount) {
        this.defaultPostCount = defaultPostCount;
    }

    public String getXPrompt() {
        return xPrompt;
    }

    public void setXPrompt(String xPrompt) {
        this.xPrompt = xPrompt;
    }

    public String getXLanguage() {
        return xLanguage;
    }

    public void setXLanguage(String xLanguage) {
        this.xLanguage = xLanguage;
    }

    public Integer getXDefaultPostCount() {
        return xDefaultPostCount;
    }

    public void setXDefaultPostCount(Integer xDefaultPostCount) {
        this.xDefaultPostCount = xDefaultPostCount;
    }

    public String getXAccountLabel() {
        return xAccountLabel;
    }

    public void setXAccountLabel(String xAccountLabel) {
        this.xAccountLabel = xAccountLabel;
    }

    public String getXAccessToken() {
        return xAccessToken;
    }

    public void setXAccessToken(String xAccessToken) {
        this.xAccessToken = xAccessToken;
    }

    public String getXClientId() {
        return xClientId;
    }

    public void setXClientId(String xClientId) {
        this.xClientId = xClientId;
    }

    public String getXClientSecret() {
        return xClientSecret;
    }

    public void setXClientSecret(String xClientSecret) {
        this.xClientSecret = xClientSecret;
    }

    public String getXRedirectUri() {
        return xRedirectUri;
    }

    public void setXRedirectUri(String xRedirectUri) {
        this.xRedirectUri = xRedirectUri;
    }

    public String getXScopes() {
        return xScopes;
    }

    public void setXScopes(String xScopes) {
        this.xScopes = xScopes;
    }

    public String getXApiKey() {
        return xApiKey;
    }

    public void setXApiKey(String xApiKey) {
        this.xApiKey = xApiKey;
    }

    public String getXApiSecret() {
        return xApiSecret;
    }

    public void setXApiSecret(String xApiSecret) {
        this.xApiSecret = xApiSecret;
    }

    public String getXAccessTokenSecret() {
        return xAccessTokenSecret;
    }

    public void setXAccessTokenSecret(String xAccessTokenSecret) {
        this.xAccessTokenSecret = xAccessTokenSecret;
    }

    public String getXRefreshToken() {
        return xRefreshToken;
    }

    public void setXRefreshToken(String xRefreshToken) {
        this.xRefreshToken = xRefreshToken;
    }

    public String getXPublishMode() {
        return xPublishMode;
    }

    public void setXPublishMode(String xPublishMode) {
        this.xPublishMode = xPublishMode;
    }

    public String getXBrowser() {
        return xBrowser;
    }

    public void setXBrowser(String xBrowser) {
        this.xBrowser = xBrowser;
    }

    public String getXBrowserProfileDir() {
        return xBrowserProfileDir;
    }

    public void setXBrowserProfileDir(String xBrowserProfileDir) {
        this.xBrowserProfileDir = xBrowserProfileDir;
    }

    public boolean isXBrowserHeadless() {
        return xBrowserHeadless;
    }

    public void setXBrowserHeadless(boolean xBrowserHeadless) {
        this.xBrowserHeadless = xBrowserHeadless;
    }

    public String getThreadsPrompt() {
        return threadsPrompt;
    }

    public void setThreadsPrompt(String threadsPrompt) {
        this.threadsPrompt = threadsPrompt;
    }

    public String getThreadsLanguage() {
        return threadsLanguage;
    }

    public void setThreadsLanguage(String threadsLanguage) {
        this.threadsLanguage = threadsLanguage;
    }

    public Integer getThreadsDefaultPostCount() {
        return threadsDefaultPostCount;
    }

    public void setThreadsDefaultPostCount(Integer threadsDefaultPostCount) {
        this.threadsDefaultPostCount = threadsDefaultPostCount;
    }

    public String getThreadsAccountLabel() {
        return threadsAccountLabel;
    }

    public void setThreadsAccountLabel(String threadsAccountLabel) {
        this.threadsAccountLabel = threadsAccountLabel;
    }

    public String getThreadsAccessToken() {
        return threadsAccessToken;
    }

    public void setThreadsAccessToken(String threadsAccessToken) {
        this.threadsAccessToken = threadsAccessToken;
    }

    public String getThreadsUserId() {
        return threadsUserId;
    }

    public void setThreadsUserId(String threadsUserId) {
        this.threadsUserId = threadsUserId;
    }

    public String getThreadsAppId() {
        return threadsAppId;
    }

    public void setThreadsAppId(String threadsAppId) {
        this.threadsAppId = threadsAppId;
    }

    public String getThreadsAppSecret() {
        return threadsAppSecret;
    }

    public void setThreadsAppSecret(String threadsAppSecret) {
        this.threadsAppSecret = threadsAppSecret;
    }

    public String getThreadsRedirectUri() {
        return threadsRedirectUri;
    }

    public void setThreadsRedirectUri(String threadsRedirectUri) {
        this.threadsRedirectUri = threadsRedirectUri;
    }

    public String getSource() {
        return source;
    }

    public void setSource(String source) {
        this.source = source;
    }
}
