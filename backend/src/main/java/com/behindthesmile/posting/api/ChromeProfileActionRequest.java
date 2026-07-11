package com.behindthesmile.posting.api;

public record ChromeProfileActionRequest(
        String url,
        String referer,
        String videoQuality
) {
}
