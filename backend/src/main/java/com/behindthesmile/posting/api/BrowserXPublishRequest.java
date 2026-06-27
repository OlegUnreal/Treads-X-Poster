package com.behindthesmile.posting.api;

public record BrowserXPublishRequest(
        String queuePostId,
        String text,
        String imageUrl,
        Boolean markPublished
) {
}
