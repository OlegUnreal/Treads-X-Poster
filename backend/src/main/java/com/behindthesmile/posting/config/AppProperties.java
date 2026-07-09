package com.behindthesmile.posting.config;

import java.util.List;

public record AppProperties(
        OpenAi openAi,
        Defaults defaults,
        Runtime runtime,
        List<Account> accounts,
        X x,
        Threads threads
) {
    public record OpenAi(String apiKey, String model, String imageModel, int imageFillLimit) {}

    public record Defaults(String language, String topic, int count) {}

    public record Runtime(
            String dataDir,
            String queueFile,
            String draftsFile,
            String xLinksFile,
            String contentPlanFile,
            String activeAccountFile,
            String mediaDir,
            String publicBaseUrl
    ) {}

    public record Account(
            String id,
            String label,
            X x,
            Threads threads
    ) {}

    public record X(
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
            boolean browserHeadless
    ) {}

    public record Threads(
            String accountLabel,
            String accessToken,
            String userId,
            String appId,
            String appSecret,
            String redirectUri
    ) {}
}
