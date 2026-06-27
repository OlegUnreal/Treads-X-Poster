package com.behindthesmile.posting.config;

public record AppProperties(
        OpenAi openAi,
        Defaults defaults,
        X x,
        Threads threads
) {
    public record OpenAi(String apiKey, String model) {}

    public record Defaults(String language, String topic, String tone, int count) {}

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
